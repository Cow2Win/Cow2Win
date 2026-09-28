package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.CowScoreTier;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reading and writing of a CowScore sidecar file - {@code cowScore.json}
 * for heroes (see {@link HeroRepository}) and {@code titanCowScore.json}
 * for titans (see {@link TitanRepository}). Extracted (2026-09-28) when the
 * titans got the same master-data/score split the heroes already had since
 * 2026-09-14, so both repositories share one implementation of the file
 * format instead of two copies that could drift apart.
 *
 * <p>File format (identical for both files): a JSON array with one entry per
 * entity that has a deliberate, non-default assessment -
 * {@code [{"id": "...", "generalScore": "GREAT", "buffFitScores": {"<fortificationId>": "MODERATE"}}, ...]}.
 * Both score fields are optional, and an entity without any entry falls back
 * to {@link CowScore#DEFAULT}. The file is kept sparse on the way out, see
 * {@link #toTree}.
 */
final class CowScoreFiles {

    private CowScoreFiles() {
        // Utility class, no instantiation
    }

    /**
     * Loads the classpath resource {@code resourcePath} into an id ->
     * {@link CowScore} map. A missing or malformed file is logged and treated
     * as "no entity has an explicit score yet" (empty map) - the sidecar file
     * must never prevent the catalog itself (and therefore the whole app)
     * from loading.
     *
     * @param anchor       class the resource is resolved against
     * @param resourcePath classpath-absolute path, e.g. {@code "/data/cowScore.json"}
     * @param entityLabel  "hero" or "titan" - only used in log messages
     */
    static Map<String, CowScore> load(Class<?> anchor, String resourcePath, String entityLabel) {
        String fileName = fileNameOf(resourcePath);
        try {
            String json = JsonSupport.readClasspathResource(anchor, resourcePath);
            return parse(json, fileName, entityLabel);
        } catch (IOException | RuntimeException e) {
            Logger.log(fileName + ": could not read Cow2Win scores (" + e.getMessage()
                    + "), using the default CowScore for every " + entityLabel);
            return new LinkedHashMap<>();
        }
    }

    /**
     * Parses the content of a CowScore file - see the class Javadoc for the
     * format. Entries without an id are skipped, unknown tier names fall back
     * to the default (both logged). A structurally broken document (not a
     * JSON array) throws, see {@link #load} for how that is handled.
     */
    static Map<String, CowScore> parse(String json, String fileName, String entityLabel) {
        Map<String, CowScore> result = new LinkedHashMap<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            JsonObject obj = element.getAsJsonObject();
            String id = JsonSupport.getStringOrNull(obj, "id");
            if (id == null || id.isBlank()) {
                Logger.log(fileName + ": skipping entry without an id");
                continue;
            }
            CowScoreTier generalScore = parseGeneralScore(obj, id, fileName, entityLabel);
            Map<String, CowScoreTier> buffFitScores = parseBuffFitScores(obj, id, fileName, entityLabel);
            result.put(id, new CowScore(generalScore, buffFitScores));
        }
        return result;
    }

    /**
     * Builds the file content for {@code cowScoresById} (in its iteration
     * order). An entity whose CowScore {@link CowScore#isDefault() is the
     * default} gets no entry at all, a {@code generalScore} of {@link
     * CowScoreTier#GOOD} is left out, and so is every {@code buffFitScores}
     * entry whose tier is {@link CowScoreTier#GOOD} (that is already what a
     * missing entry resolves to for a matching role/element). This is the
     * single place that enforces the "sparse file" rule, so callers can pass
     * in whatever their editor holds without filtering it themselves.
     */
    static JsonArray toTree(Map<String, CowScore> cowScoresById) {
        JsonArray tree = new JsonArray();
        for (var entry : cowScoresById.entrySet()) {
            CowScore cowScore = entry.getValue();
            if (cowScore == null || cowScore.isDefault()) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.getKey());
            if (cowScore.generalScore() != CowScoreTier.GOOD) {
                obj.addProperty("generalScore", cowScore.generalScore().name());
            }
            JsonObject buffFitScores = new JsonObject();
            for (var buffFit : cowScore.buffFitScores().entrySet()) {
                if (buffFit.getValue() != CowScoreTier.GOOD) {
                    buffFitScores.addProperty(buffFit.getKey(), buffFit.getValue().name());
                }
            }
            if (buffFitScores.size() > 0) {
                obj.add("buffFitScores", buffFitScores);
            }
            // Only GOOD overrides and a GOOD generalScore: nothing left worth writing.
            if (obj.size() > 1) {
                tree.add(obj);
            }
        }
        return tree;
    }

    // --- private ---

    /** Optional "generalScore" field - null (i.e. the default) when absent or naming an unknown tier (only the latter is logged). */
    private static CowScoreTier parseGeneralScore(JsonObject obj, String id, String fileName, String entityLabel) {
        String name = JsonSupport.getStringOrNull(obj, "generalScore");
        if (name == null) {
            return null;
        }
        try {
            return CowScoreTier.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            Logger.log(fileName + ": " + entityLabel + " '" + id + "' has unknown generalScore '" + name
                    + "', using the default");
            return null;
        }
    }

    /** Optional "buffFitScores" object (fortification id -> tier name) - an entry with an unknown tier is skipped (logged). */
    private static Map<String, CowScoreTier> parseBuffFitScores(JsonObject obj, String id, String fileName,
                                                                String entityLabel) {
        Map<String, CowScoreTier> result = new LinkedHashMap<>();
        for (var entry : JsonSupport.getStringMap(obj, "buffFitScores").entrySet()) {
            String fortificationId = entry.getKey();
            String tierName = entry.getValue();
            try {
                result.put(fortificationId, CowScoreTier.valueOf(tierName.trim()));
            } catch (IllegalArgumentException e) {
                Logger.log(fileName + ": " + entityLabel + " '" + id + "' has unknown buffFitScores tier '"
                        + tierName + "' for fortification '" + fortificationId + "', ignoring it");
            }
        }
        return result;
    }

    private static String fileNameOf(String resourcePath) {
        return resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
    }
}
