package org.c2w.data.repository;

import com.google.gson.JsonArray;
import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Hero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link FortMarkFiles} - the fortification-mark file format of
 * {@code cowScore.json}, {@code titanCowScore.json}, {@code petCowScore.json} and {@code
 * warFlagCowScore.json} (CowScore concept of 2026-09-30), including the
 * migration of the former tier-based format.
 */
class FortMarkFilesTest {

    @Test
    @DisplayName("parse: reads fortMarks, skips entries without an id and unknown marks")
    void parseNewFormat() {
        String json = """
                [
                  {"id": "corvus", "fortMarks": {"foundry": "POSITIVE", "city-hall": "NEGATIVE", "bridge": "HUGE"}},
                  {"fortMarks": {"foundry": "POSITIVE"}},
                  {"id": "adam"}
                ]
                """;
        FortMarkFiles.Parsed parsed = FortMarkFiles.parse(json, "cowScore.json", true, "hero");

        assertFalse(parsed.containsLegacyEntries());
        assertEquals(2, parsed.marksById().size());
        FortMarks corvus = parsed.marksById().get("corvus");
        assertEquals(FortMark.POSITIVE, corvus.markFor("foundry"));
        assertEquals(FortMark.NEGATIVE, corvus.markFor("city-hall"));
        assertNull(corvus.markFor("bridge"));
        assertTrue(parsed.marksById().get("adam").isEmpty());
    }

    @Test
    @DisplayName("parse: former tier format - GREAT -> POSITIVE, NEGATIVE -> NEGATIVE, rest neutral, generalScore dropped")
    void parseLegacyFormatHeroes() {
        String json = """
                [
                  {"id": "corvus", "generalScore": "GREAT",
                   "buffFitScores": {"foundry": "GREAT", "city-hall": "NEGATIVE", "bastion": "MODERATE", "alchemy-tower": "AVERAGE"}},
                  {"id": "adam", "generalScore": "MODERATE"}
                ]
                """;
        FortMarkFiles.Parsed parsed = FortMarkFiles.parse(json, "cowScore.json", true, "hero");

        assertTrue(parsed.containsLegacyEntries());
        assertEquals(Map.of("foundry", FortMark.POSITIVE, "city-hall", FortMark.NEGATIVE),
                parsed.marksById().get("corvus").marks());
        assertTrue(parsed.marksById().get("adam").isEmpty());
    }

    @Test
    @DisplayName("parse: pets/war flags cannot carry NEGATIVE marks - new and legacy format")
    void parseWithoutNegative() {
        String json = """
                [
                  {"id": "albus", "fortMarks": {"foundry": "POSITIVE", "bastion": "NEGATIVE"}},
                  {"id": "axel", "generalScore": "GOOD", "buffFitScores": {"foundry": "GREAT", "bastion": "NEGATIVE"}}
                ]
                """;
        Map<String, FortMarks> result = FortMarkFiles.parse(json, "petCowScore.json", false, "pet").marksById();

        assertEquals(Map.of("foundry", FortMark.POSITIVE), result.get("albus").marks());
        assertEquals(Map.of("foundry", FortMark.POSITIVE), result.get("axel").marks());
    }

    @Test
    @DisplayName("toTree/parse round trip; unmarked entities are written with their id only")
    void roundTrip() {
        Map<String, FortMarks> marks = new LinkedHashMap<>();
        marks.put("corvus", new FortMarks(Map.of("foundry", FortMark.POSITIVE, "city-hall", FortMark.NEGATIVE)));
        marks.put("adam", FortMarks.NONE);

        JsonArray tree = FortMarkFiles.toTree(marks);
        assertFalse(tree.get(1).getAsJsonObject().has("fortMarks"));
        assertEquals(marks, FortMarkFiles.parse(tree.toString(), "cowScore.json", true, "hero").marksById());
    }

    @Test
    @DisplayName("loadWorkspace: a former-format file is backed up, then rewritten in the new format")
    void loadWorkspaceMigratesLegacyFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("cowScore.json");
        String legacy = """
                [{"id": "corvus", "generalScore": "GOOD", "buffFitScores": {"foundry": "GREAT"}},
                 {"id": "adam", "generalScore": "GREAT"}]
                """;
        Files.writeString(file, legacy, StandardCharsets.UTF_8);
        Map<String, FortMarks> defaults = new LinkedHashMap<>();
        defaults.put("adam", FortMarks.NONE);
        defaults.put("corvus", FortMarks.NONE);

        Map<String, FortMarks> result = FortMarkFiles.loadWorkspace(file, defaults, true, "hero");

        assertEquals(FortMark.POSITIVE, result.get("corvus").markFor("foundry"));
        assertTrue(result.get("adam").isEmpty());
        List<Path> backups;
        try (var files = Files.list(dir)) {
            backups = files.filter(p -> p.getFileName().toString().startsWith("cowScore.json.legacy-")).toList();
        }
        assertEquals(1, backups.size(), "exactly one backup of the former file");
        assertEquals(legacy, Files.readString(backups.get(0), StandardCharsets.UTF_8), "backup holds the former content");
        FortMarkFiles.Parsed rewritten = FortMarkFiles.parse(Files.readString(file, StandardCharsets.UTF_8),
                "cowScore.json", true, "hero");
        assertFalse(rewritten.containsLegacyEntries(), "the workspace file is now in the new format");
        assertEquals(result, rewritten.marksById());
    }

    @Test
    @DisplayName("loadWorkspace: missing file is created from the defaults; missing entities are completed")
    void loadWorkspaceCreatesAndCompletes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("petCowScore.json");
        Map<String, FortMarks> defaults = Map.of("albus", new FortMarks(Map.of("foundry", FortMark.POSITIVE)));

        assertEquals(defaults, FortMarkFiles.loadWorkspace(file, defaults, false, "pet"));
        assertTrue(Files.isRegularFile(file));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "no backup for a new-format file");
        }
    }

    @Test
    @DisplayName("HeroRepository: saveCowScores writes marks that a fresh load reads back")
    void heroRepositoryRoundTrip(@TempDir Path workspace) throws IOException {
        HeroRepository heroes = new HeroRepository(workspace);
        List<Hero> catalog = heroes.findAll();
        assertFalse(catalog.isEmpty());
        Hero first = catalog.get(0);
        List<Hero> updated = catalog.stream()
                .map(h -> h == first
                        ? new Hero(h.id(), h.roles(), h.imagePath(), new FortMarks(Map.of("foundry", FortMark.NEGATIVE)))
                        : h)
                .toList();
        heroes.saveCowScores(updated);

        HeroRepository reloaded = new HeroRepository(workspace);
        assertEquals(FortMark.NEGATIVE, reloaded.findById(first.id()).orElseThrow().fortMark("foundry"));
    }
}
