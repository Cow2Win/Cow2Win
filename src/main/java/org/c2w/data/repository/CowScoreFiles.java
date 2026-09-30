package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.CowScoreTier;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>Since 2026-09-30 only used for {@code titanCowScore.json}</b> - heroes,
 * pets and war flags moved to {@link FortMarkFiles}.
 *
 * <p>Reading and writing of a CowScore file - {@code cowScore.json} for heroes
 * (see {@link HeroRepository}) and {@code titanCowScore.json} for titans
 * (see {@link TitanRepository}). Extracted (2026-09-28) when the titans got
 * the same master-data/score split the heroes already had since 2026-09-14,
 * so both repositories share one implementation of the file format instead
 * of two copies that could drift apart.
 *
 * <p>Each file exists twice (since 2026-09-29):
 * <ul>
 *     <li>the <b>shipped defaults</b> - a classpath resource inside the jar
 *     ({@code /data/cowScore.json}, {@code /data/titanCowScore.json}),
 *     read-only at runtime and only ever used as the starting point, see
 *     {@link #loadDefaults}; and</li>
 *     <li>the <b>workspace copy</b> - a plain file directly in the configured
 *     workspace folder ({@link org.c2w.infra.Config#getWorkspaceDir()}), the
 *     one the app actually reads and the CowScore dialogs save to, see
 *     {@link #loadWorkspace}. Created from the shipped defaults on the first
 *     start, so edits survive a restart and an app update (the jar copy used
 *     to be the only one ever read, which made saved edits disappear after a
 *     restart of the packaged app).</li>
 * </ul>
 *
 * <p>File format (identical for both files and both copies): a JSON array
 * with one entry per entity -
 * {@code [{"id": "...", "generalScore": "GREAT", "buffFitScores": {"<fortificationId>": "MODERATE"}}, ...]}.
 * The workspace copy lists <em>every</em> catalog entity (see {@link #toTree});
 * the shipped defaults may still be sparse, an entity without an entry there
 * falls back to {@link CowScore#DEFAULT}.
 */
final class CowScoreFiles {

    private CowScoreFiles() {
        // Utility class, no instantiation
    }

    /**
     * Loads the shipped default CowScores (classpath resource {@code
     * resourcePath}) for exactly the given catalog entities, in their
     * iteration order - an entity without an entry in that file gets {@link
     * CowScore#DEFAULT}. A missing or malformed resource is logged and treated
     * as "every entity has the default CowScore" - the score file must never
     * prevent the catalog itself (and therefore the whole app) from loading.
     *
     * @param anchor       class the resource is resolved against
     * @param resourcePath classpath-absolute path, e.g. {@code "/data/cowScore.json"}
     * @param catalogIds   ids of every entity in the master-data catalog
     * @param entityLabel  "hero" or "titan" - only used in log messages
     */
    static Map<String, CowScore> loadDefaults(Class<?> anchor, String resourcePath, Collection<String> catalogIds,
                                              String entityLabel) {
        String fileName = fileNameOf(resourcePath);
        Map<String, CowScore> shipped;
        try {
            shipped = parse(JsonSupport.readClasspathResource(anchor, resourcePath), fileName, entityLabel);
        } catch (IOException | RuntimeException e) {
            Logger.log(fileName + " (defaults): could not read Cow2Win scores (" + e.getMessage()
                    + "), using the default CowScore for every " + entityLabel);
            shipped = Map.of();
        }
        Map<String, CowScore> result = new LinkedHashMap<>();
        for (String id : catalogIds) {
            result.put(id, shipped.getOrDefault(id, CowScore.DEFAULT));
        }
        return result;
    }

    /**
     * Loads the CowScores the app actually works with from the workspace
     * copy {@code workspaceFile}, for exactly the given catalog entities:
     * <ul>
     *     <li>an entity listed in the workspace file gets that entry (even if
     *     it equals the default - that is a deliberate choice, see {@link
     *     #toTree});</li>
     *     <li>an entity missing from it (typically one added to the catalog
     *     by an app update) gets its shipped default from {@code defaults}
     *     (see {@link #loadDefaults});</li>
     *     <li>an entry for an id no longer in the catalog is logged and dropped.</li>
     * </ul>
     * If the workspace file does not exist yet (first start) or lacks at
     * least one catalog entity, it is (re)written with the complete result,
     * so it always lists every entity afterwards. A workspace file that
     * exists but cannot be read is logged and left untouched (never
     * overwritten, so a hand-editing mistake does not wipe the user's
     * scores) - the shipped defaults are used for this session instead. A
     * failure to write is logged as well; the returned scores are valid
     * either way.
     *
     * @param defaults    the shipped defaults for every catalog entity (see
     *                    {@link #loadDefaults}) - its key set is the catalog
     * @param entityLabel "hero" or "titan" - only used in log messages
     */
    static Map<String, CowScore> loadWorkspace(Path workspaceFile, Map<String, CowScore> defaults,
                                               String entityLabel) {
        return loadWorkspace(workspaceFile, defaults, entityLabel, false);
    }

    /**
     * Same as {@link #loadWorkspace(Path, Map, String)}; {@code
     * keepGoodBuffFitScores} is passed on to {@link #toTree(Map, boolean)}
     * when the file is (re)written - true for pets and war flags.
     */
    static Map<String, CowScore> loadWorkspace(Path workspaceFile, Map<String, CowScore> defaults,
                                               String entityLabel, boolean keepGoodBuffFitScores) {
        String fileName = String.valueOf(workspaceFile.getFileName());
        Map<String, CowScore> stored = null;
        if (Files.isRegularFile(workspaceFile)) {
            try {
                stored = parse(Files.readString(workspaceFile, StandardCharsets.UTF_8), fileName, entityLabel);
            } catch (IOException | RuntimeException e) {
                Logger.log(fileName + ": could not read " + workspaceFile + " (" + e.getMessage()
                        + "), using the shipped default CowScores for this session - the file is left unchanged");
                return new LinkedHashMap<>(defaults);
            }
        }

        Map<String, CowScore> result = new LinkedHashMap<>();
        boolean complete = stored != null;
        for (var entry : defaults.entrySet()) {
            CowScore own = stored == null ? null : stored.get(entry.getKey());
            if (own == null) {
                complete = false;
                result.put(entry.getKey(), entry.getValue());
            } else {
                result.put(entry.getKey(), own);
            }
        }
        if (stored != null) {
            for (String id : stored.keySet()) {
                if (!defaults.containsKey(id)) {
                    Logger.log(fileName + ": entry for unknown " + entityLabel + " '" + id + "', ignoring it");
                }
            }
        }

        if (!complete) {
            try {
                JsonSupport.writeJsonFile(toTree(result, keepGoodBuffFitScores), workspaceFile);
                Logger.log(fileName + ": " + (stored == null ? "created" : "completed") + " " + workspaceFile);
            } catch (IOException e) {
                Logger.logException("Could not write " + workspaceFile, e);
            }
        }
        return result;
    }

    /**
     * Parses the content of a CowScore file - see the class Javadoc for the
     * format. Entries without an id are skipped, unknown tier names fall back
     * to the default (both logged). A structurally broken document (not a
     * JSON array) throws, see {@link #loadDefaults}/{@link #loadWorkspace}
     * for how that is handled.
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
     * order). Since 2026-09-29 every entity gets an entry with an explicit
     * {@code generalScore} - including {@link CowScoreTier#GOOD} - so the
     * workspace file is a complete, self-explanatory list and a deliberately
     * chosen default is not mistaken for "not assessed yet" (which would pull
     * in the shipped default again, see {@link #loadWorkspace}). {@code
     * buffFitScores} stays sparse: an entry whose tier is {@link
     * CowScoreTier#GOOD} is left out (the CowScore dialogs treat GOOD as "no
     * override" as well), and the property is omitted when nothing is left.
     * A null CowScore is written as {@link CowScore#DEFAULT}.
     */
    static JsonArray toTree(Map<String, CowScore> cowScoresById) {
        return toTree(cowScoresById, false);
    }

    /**
     * Same as {@link #toTree(Map)}, except that with {@code
     * keepGoodBuffFitScores} a {@link CowScoreTier#GOOD} buffFitScores entry
     * is written like any other tier - needed for pets and war flags, where a
     * missing entry means "use the generalScore" (see {@link
     * CowScore#buffFitScoreOrGeneral(String)}), so an explicit GOOD is a real
     * override whenever the generalScore is anything else.
     */
    static JsonArray toTree(Map<String, CowScore> cowScoresById, boolean keepGoodBuffFitScores) {
        JsonArray tree = new JsonArray();
        for (var entry : cowScoresById.entrySet()) {
            CowScore cowScore = entry.getValue() == null ? CowScore.DEFAULT : entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.getKey());
            obj.addProperty("generalScore", cowScore.generalScore().name());
            JsonObject buffFitScores = new JsonObject();
            for (var buffFit : cowScore.buffFitScores().entrySet()) {
                if (keepGoodBuffFitScores || buffFit.getValue() != CowScoreTier.GOOD) {
                    buffFitScores.addProperty(buffFit.getKey(), buffFit.getValue().name());
                }
            }
            if (buffFitScores.size() > 0) {
                obj.add("buffFitScores", buffFitScores);
            }
            tree.add(obj);
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
