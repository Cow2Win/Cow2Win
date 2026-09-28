package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.CowScoreTier;
import org.c2w.data.model.Titan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link CowScoreFiles} - the shared file format of {@code cowScore.json}
 * (heroes) and {@code titanCowScore.json} (titans), extracted 2026-09-28 - plus
 * a load check of the real titan catalog after the titans.json/titanCowScore.json
 * split.
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
    @DisplayName("toTree: keeps the file sparse - no default entities, no GOOD values")
    void toTreeIsSparse() {
        Map<String, CowScore> scores = new LinkedHashMap<>();
        scores.put("ignis", new CowScore(CowScoreTier.GREAT, Map.of("bastion-of-fire", CowScoreTier.GOOD)));
        scores.put("nova", CowScore.DEFAULT);
        scores.put("sigurd", new CowScore(CowScoreTier.GOOD, Map.of("gates-of-nature", CowScoreTier.GOOD)));
        scores.put("hyperion", new CowScore(null, Map.of("sun-temple", CowScoreTier.MODERATE)));

        JsonArray tree = CowScoreFiles.toTree(scores);

        assertEquals(2, tree.size());
        JsonObject ignis = tree.get(0).getAsJsonObject();
        assertEquals("ignis", ignis.get("id").getAsString());
        assertEquals("GREAT", ignis.get("generalScore").getAsString());
        assertFalse(ignis.has("buffFitScores"));
        JsonObject hyperion = tree.get(1).getAsJsonObject();
        assertEquals("hyperion", hyperion.get("id").getAsString());
        assertFalse(hyperion.has("generalScore"));
        assertEquals("MODERATE", hyperion.getAsJsonObject("buffFitScores").get("sun-temple").getAsString());
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
    @DisplayName("TitanRepository: real catalog still loads after the titans.json/titanCowScore.json split")
    void titanCatalogLoads() {
        TitanRepository.resetCache();

        assertTrue(TitanRepository.count() > 0);
        Titan ignis = TitanRepository.findById("ignis").orElseThrow();
        assertNotNull(ignis.cowScore());
    }
}
