package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Titan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the titan fortification marks in {@code titanCowScore.json} (see
 * {@link TitanRepository}, via {@link FortMarkFiles}): round trip with
 * positive and negative marks, and the migration of the former tier-based
 * format ({@code generalScore}/{@code buffFitScores}).
 */
class TitanRepositoryTest {

    private static final String FILE_NAME = "titanCowScore.json";

    private static List<Path> backups(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith(FILE_NAME + ".legacy-")).toList();
        }
    }

    private static long markCount(TitanRepository repository) {
        return repository.findAll().stream().mapToLong(t -> t.fortMarks().marks().size()).sum();
    }

    @Test
    @DisplayName("a new workspace starts with every titan unmarked (the shipped defaults are empty)")
    void newWorkspaceHasNoMarks(@TempDir Path workspace) throws IOException {
        TitanRepository repository = new TitanRepository(workspace);

        assertTrue(repository.count() > 0);
        assertEquals(0, markCount(repository));
        assertTrue(Files.isRegularFile(workspace.resolve(FILE_NAME)), "the workspace copy is created");
        assertTrue(repository.loadDefaultCowScores().values().stream().allMatch(FortMarks::isEmpty));
    }

    @Test
    @DisplayName("saveCowScores writes positive and negative marks that a fresh load reads back")
    void roundTrip(@TempDir Path workspace) throws IOException {
        TitanRepository repository = new TitanRepository(workspace);
        Titan ignis = repository.findById("ignis").orElseThrow();
        Titan nova = repository.findById("nova").orElseThrow();
        FortMarks ignisMarks = new FortMarks(Map.of("bastion-of-fire", FortMark.POSITIVE, "moon-temple", FortMark.NEGATIVE));
        FortMarks novaMarks = new FortMarks(Map.of("bridge", FortMark.NEGATIVE));

        List<Titan> catalog = repository.findAll().stream()
                .map(t -> t.id().equals(ignis.id()) ? new Titan(t.id(), t.element(), t.imagePath(), ignisMarks)
                        : t.id().equals(nova.id()) ? new Titan(t.id(), t.element(), t.imagePath(), novaMarks)
                        : t)
                .toList();
        repository.saveCowScores(catalog);

        TitanRepository reloaded = new TitanRepository(workspace);
        assertEquals(ignisMarks, reloaded.findById("ignis").orElseThrow().fortMarks());
        assertEquals(novaMarks, reloaded.findById("nova").orElseThrow().fortMarks());
        assertEquals(3, markCount(reloaded));

        JsonArray written = JsonParser.parseString(Files.readString(workspace.resolve(FILE_NAME), StandardCharsets.UTF_8))
                .getAsJsonArray();
        assertEquals(reloaded.count(), written.size(), "every titan gets an entry");
        for (var element : written) {
            JsonObject entry = element.getAsJsonObject();
            assertFalse(entry.has("generalScore") || entry.has("buffFitScores"), "no former-format fields are written");
        }
    }

    @Test
    @DisplayName("a former-format file is backed up and migrated: GREAT -> positive, NEGATIVE -> negative, rest neutral")
    void migratesLegacyFile(@TempDir Path workspace) throws IOException {
        String legacy = """
                [
                  {"id": "ignis", "generalScore": "GREAT",
                   "buffFitScores": {"bastion-of-fire": "GREAT", "moon-temple": "NEGATIVE", "bridge": "MODERATE"}},
                  {"id": "nova", "generalScore": "AVERAGE", "buffFitScores": {"bridge": "GOOD"}},
                  {"id": "vulcan", "generalScore": "GOOD"}
                ]
                """;
        Path file = workspace.resolve(FILE_NAME);
        Files.writeString(file, legacy, StandardCharsets.UTF_8);

        TitanRepository repository = new TitanRepository(workspace);

        assertEquals(Map.of("bastion-of-fire", FortMark.POSITIVE, "moon-temple", FortMark.NEGATIVE),
                repository.findById("ignis").orElseThrow().fortMarks().marks());
        assertTrue(repository.findById("nova").orElseThrow().fortMarks().isEmpty());
        assertTrue(repository.findById("vulcan").orElseThrow().fortMarks().isEmpty());
        assertEquals(2, markCount(repository));

        List<Path> backups = backups(workspace);
        assertEquals(1, backups.size(), "exactly one backup of the former file");
        assertEquals(legacy, Files.readString(backups.get(0), StandardCharsets.UTF_8), "backup holds the former content");
        FortMarkFiles.Parsed rewritten = FortMarkFiles.parse(Files.readString(file, StandardCharsets.UTF_8),
                FILE_NAME, true, "titan");
        assertFalse(rewritten.containsLegacyEntries(), "the workspace file is now in the new format");
        assertEquals(repository.count(), rewritten.marksById().size());
    }

    @Test
    @DisplayName("a former-format file where every titan only has generalScore GOOD migrates to 0 marks")
    void allGeneralScoreGoodMigratesToNoMarks(@TempDir Path workspace) throws IOException {
        List<String> ids = new TitanRepository(workspace).findAll().stream().map(Titan::id).toList();
        Files.delete(workspace.resolve(FILE_NAME));
        StringBuilder legacy = new StringBuilder("[\n");
        for (int i = 0; i < ids.size(); i++) {
            legacy.append("  {\"id\": \"").append(ids.get(i)).append("\", \"generalScore\": \"GOOD\"}")
                    .append(i < ids.size() - 1 ? ",\n" : "\n");
        }
        legacy.append("]\n");
        Files.writeString(workspace.resolve(FILE_NAME), legacy, StandardCharsets.UTF_8);

        TitanRepository repository = new TitanRepository(workspace);

        assertEquals(ids.size(), repository.count());
        assertEquals(0, markCount(repository));
        assertEquals(1, backups(workspace).size());
    }
}
