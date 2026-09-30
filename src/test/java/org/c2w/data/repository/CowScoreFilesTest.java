package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.CowScoreTier;
import org.c2w.data.model.Titan;
import org.c2w.util.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link CowScoreFiles} - the shared file format of {@code cowScore.json}
 * (heroes) and {@code titanCowScore.json} (titans), extracted 2026-09-28 - the
 * workspace copy with its merge rules (2026-09-29), plus a load check of the
 * real titan catalog.
 */
class CowScoreFilesTest {

    @Test
    @DisplayName("parse: reads generalScore and buffFitScores, skips entries without an id")
    void parseReadsScores() {
        String json = """
                [
                  {"id": "ignis", "generalScore": "GREAT", "buffFitScores": {"bastion-of-fire": "MODERATE"}},
                  {"generalScore": "NEGATIVE"},
                  {"id": "nova"}
                ]
                """;

        Map<String, CowScore> result = CowScoreFiles.parse(json, "titanCowScore.json", "titan");

        assertEquals(2, result.size());
        assertEquals(CowScoreTier.GREAT, result.get("ignis").generalScore());
        assertEquals(CowScoreTier.MODERATE, result.get("ignis").buffFitScores().get("bastion-of-fire"));
        assertTrue(result.get("nova").isDefault());
    }

    @Test
    @DisplayName("parse: unknown tier names fall back to the default instead of failing the entry")
    void parseToleratesUnknownTiers() {
        String json = """
                [{"id": "ignis", "generalScore": "LEGENDARY", "buffFitScores": {"bastion-of-fire": "HUGE", "sun-temple": "AVERAGE"}}]
                """;

        CowScore cowScore = CowScoreFiles.parse(json, "titanCowScore.json", "titan").get("ignis");

        assertEquals(CowScoreTier.GOOD, cowScore.generalScore());
        assertEquals(Map.of("sun-temple", CowScoreTier.AVERAGE), cowScore.buffFitScores());
    }

    @Test
    @DisplayName("toTree: lists every entity with an explicit generalScore, buffFitScores stay sparse")
    void toTreeListsEveryEntity() {
        Map<String, CowScore> scores = new LinkedHashMap<>();
        scores.put("ignis", new CowScore(CowScoreTier.GREAT, Map.of("bastion-of-fire", CowScoreTier.GOOD)));
        scores.put("nova", CowScore.DEFAULT);
        scores.put("hyperion", new CowScore(null, Map.of("sun-temple", CowScoreTier.MODERATE)));

        JsonArray tree = CowScoreFiles.toTree(scores);

        assertEquals(3, tree.size());
        JsonObject ignis = tree.get(0).getAsJsonObject();
        assertEquals("ignis", ignis.get("id").getAsString());
        assertEquals("GREAT", ignis.get("generalScore").getAsString());
        assertFalse(ignis.has("buffFitScores"));
        JsonObject nova = tree.get(1).getAsJsonObject();
        assertEquals("nova", nova.get("id").getAsString());
        assertEquals("GOOD", nova.get("generalScore").getAsString());
        assertFalse(nova.has("buffFitScores"));
        JsonObject hyperion = tree.get(2).getAsJsonObject();
        assertEquals("GOOD", hyperion.get("generalScore").getAsString());
        assertEquals("MODERATE", hyperion.getAsJsonObject("buffFitScores").get("sun-temple").getAsString());
    }

    @Test
    @DisplayName("loadWorkspace: first start creates the workspace file from the defaults, listing every entity")
    void loadWorkspaceCreatesFile(@TempDir Path workspace) throws IOException {
        Path file = workspace.resolve("titanCowScore.json");
        Map<String, CowScore> defaults = new LinkedHashMap<>();
        defaults.put("ignis", new CowScore(CowScoreTier.GREAT, null));
        defaults.put("nova", CowScore.DEFAULT);

        Map<String, CowScore> result = CowScoreFiles.loadWorkspace(file, defaults, "titan");

        assertEquals(defaults, result);
        assertTrue(Files.isRegularFile(file));
        assertEquals(defaults, CowScoreFiles.parse(Files.readString(file), "titanCowScore.json", "titan"));
    }

    @Test
    @DisplayName("loadWorkspace: own values win (even GOOD), missing entities get their default and are added to the file")
    void loadWorkspaceMergesAndCompletes(@TempDir Path workspace) throws IOException {
        Path file = workspace.resolve("titanCowScore.json");
        Files.writeString(file, """
                [
                  {"id": "ignis", "generalScore": "GOOD"},
                  {"id": "removed-titan", "generalScore": "NEGATIVE"}
                ]
                """);
        Map<String, CowScore> defaults = new LinkedHashMap<>();
        defaults.put("ignis", new CowScore(CowScoreTier.GREAT, null));
        defaults.put("nova", new CowScore(CowScoreTier.AVERAGE, null));

        Map<String, CowScore> result = CowScoreFiles.loadWorkspace(file, defaults, "titan");

        assertEquals(CowScoreTier.GOOD, result.get("ignis").generalScore());
        assertEquals(CowScoreTier.AVERAGE, result.get("nova").generalScore());
        assertFalse(result.containsKey("removed-titan"));
        assertEquals(result, CowScoreFiles.parse(Files.readString(file), "titanCowScore.json", "titan"));
    }

    @Test
    @DisplayName("loadWorkspace: a complete workspace file is not rewritten")
    void loadWorkspaceLeavesCompleteFileAlone(@TempDir Path workspace) throws IOException {
        Path file = workspace.resolve("cowScore.json");
        String content = "[{\"id\": \"astaroth\", \"generalScore\": \"NEGATIVE\"}]";
        Files.writeString(file, content);

        Map<String, CowScore> result = CowScoreFiles.loadWorkspace(file, Map.of("astaroth", CowScore.DEFAULT), "hero");

        assertEquals(CowScoreTier.NEGATIVE, result.get("astaroth").generalScore());
        assertEquals(content, Files.readString(file));
    }

    @Test
    @DisplayName("loadWorkspace: a broken workspace file falls back to the defaults and is left untouched")
    void loadWorkspaceKeepsBrokenFile(@TempDir Path workspace) throws IOException {
        Path file = workspace.resolve("cowScore.json");
        Files.writeString(file, "{ not an array");
        Map<String, CowScore> defaults = Map.of("astaroth", new CowScore(CowScoreTier.GREAT, null));

        Map<String, CowScore> result = CowScoreFiles.loadWorkspace(file, defaults, "hero");

        assertEquals(defaults, result);
        assertEquals("{ not an array", Files.readString(file));
    }

    @Test
    @DisplayName("round trip: toTree followed by parse gives back the same scores")
    void roundTrip() {
        Map<String, CowScore> scores = new LinkedHashMap<>();
        scores.put("ignis", new CowScore(CowScoreTier.NEGATIVE, Map.of("bastion-of-fire", CowScoreTier.GREAT)));

        Map<String, CowScore> reparsed = CowScoreFiles.parse(CowScoreFiles.toTree(scores).toString(),
                "titanCowScore.json", "titan");

        assertEquals(scores, reparsed);
    }

    @Test
    @DisplayName("TitanRepository: real catalog loads and seeds a complete titanCowScore.json in the workspace")
    void titanCatalogLoads(@TempDir Path workspace) throws IOException {
        String previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        try {
            TitanRepository.resetCache();

            assertTrue(TitanRepository.count() > 0);
            Titan ignis = TitanRepository.findById("ignis").orElseThrow();
            assertNotNull(ignis.cowScore());
            Path file = workspace.resolve("titanCowScore.json");
            assertEquals(TitanRepository.cowScoreFile(), file);
            assertEquals(TitanRepository.count(),
                    CowScoreFiles.parse(Files.readString(file), "titanCowScore.json", "titan").size());
            assertEquals(TitanRepository.count(), TitanRepository.loadDefaultCowScores().size());
        } finally {
            Config.setWorkspacePath(previousWorkspace);
            TitanRepository.resetCache();
        }
    }

    @Test
    @DisplayName("toTree drops GOOD buffFitScores by default, but keeps them for pets/war flags")
    void toTreeKeepsGoodOverridesOnRequest() {
        Map<String, CowScore> scores = Map.of("albus",
                new CowScore(CowScoreTier.GREAT, Map.of("f1", CowScoreTier.GOOD)));
        assertFalse(CowScoreFiles.toTree(scores).toString().contains("\"f1\""));
        String kept = CowScoreFiles.toTree(scores, true).toString();
        assertTrue(kept.contains("\"f1\":\"GOOD\""), kept);
        assertEquals(CowScoreTier.GOOD,
                CowScoreFiles.parse(kept, "petCowScore.json", "pet").get("albus").buffFitScoreOrGeneral("f1"));
        assertEquals(CowScoreTier.GREAT,
                CowScoreFiles.parse(kept, "petCowScore.json", "pet").get("albus").buffFitScoreOrGeneral("f2"));
    }
}
