package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reading and writing of a fortification-mark file - {@code cowScore.json}
 * for heroes (see {@link HeroRepository}), {@code petCowScore.json} (see
 * {@link PetRepository}) and {@code warFlagCowScore.json} (see {@link
 * WarFlagRepository}). Successor of {@link CowScoreFiles} for these three
 * entity types (CowScore concept of 2026-09-30); titans still use {@link
 * CowScoreFiles}.
 *
 * <p>Like before, each file exists twice: the <b>shipped defaults</b> (a
 * read-only classpath resource, see {@link #loadDefaults}) and the
 * <b>workspace copy</b> the app actually reads and the dialogs save to (see
 * {@link #loadWorkspace}), created from the defaults on the first start.
 *
 * <p>File format: a JSON array with one entry per entity -
 * {@code [{"id": "corvus", "fortMarks": {"foundry": "POSITIVE"}}, {"id": "adam"}, ...]}.
 * An entity without marks is written with its id only. The workspace copy
 * lists every catalog entity.
 *
 * <h2>Migration of the former tier-based format</h2>
 * An entry in the former format ({@code generalScore} / {@code buffFitScores}
 * with {@code CowScoreTier} names, no {@code fortMarks}) is converted on
 * reading, per the user's rule of 2026-09-30:
 * <ul>
 *     <li>{@code buffFitScores} entry {@code GREAT} -&gt; {@link FortMark#POSITIVE};</li>
 *     <li>{@code buffFitScores} entry {@code NEGATIVE} -&gt; {@link FortMark#NEGATIVE}
 *     (heroes only - pets and war flags carry no negative marks, the entry is dropped);</li>
 *     <li>every other tier -&gt; unmarked (neutral);</li>
 *     <li>{@code generalScore} -&gt; dropped.</li>
 * </ul>
 * A workspace copy that still contains such entries is first copied to
 * {@code <name>.legacy-<date>.bak} next to it and then rewritten in the new
 * format (see {@link #loadWorkspace}).
 */
final class FortMarkFiles {

    private FortMarkFiles() {
        // Utility class, no instantiation
    }

    /** Result of {@link #parse}: the marks per id, plus whether any entry was still in the former tier-based format. */
    record Parsed(Map<String, FortMarks> marksById, boolean containsLegacyEntries) {
    }

    /**
     * Loads the shipped default marks (classpath resource {@code
     * resourcePath}) for exactly the given catalog entities, in their
     * iteration order - an entity without an entry gets {@link FortMarks#NONE}.
     * A missing or malformed resource is logged and treated as "no marks at all".
     *
     * @param allowNegative false for pets/war flags - negative marks are dropped (logged)
     * @param entityLabel   e.g. "hero" - only used in log messages
     */
    static Map<String, FortMarks> loadDefaults(Class<?> anchor, String resourcePath, Collection<String> catalogIds,
                                               boolean allowNegative, String entityLabel) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        Map<String, FortMarks> shipped;
        try {
            shipped = parse(JsonSupport.readClasspathResource(anchor, resourcePath), fileName, allowNegative,
                    entityLabel).marksById();
        } catch (IOException | RuntimeException e) {
            Logger.log(fileName + " (defaults): could not read fortification marks (" + e.getMessage()
                    + "), using no marks for every " + entityLabel);
            shipped = Map.of();
        }
        Map<String, FortMarks> result = new LinkedHashMap<>();
        for (String id : catalogIds) {
            result.put(id, shipped.getOrDefault(id, FortMarks.NONE));
        }
        return result;
    }

    /**
     * Loads the marks the app actually works with from the workspace copy
     * {@code workspaceFile}, for exactly the given catalog entities:
     * <ul>
     *     <li>an entity listed in the workspace file gets that entry;</li>
     *     <li>an entity missing from it (e.g. added by an app update) gets its
     *     shipped default from {@code defaults};</li>
     *     <li>an entry for an id no longer in the catalog is logged and dropped.</li>
     * </ul>
     * The file is (re)written with the complete result if it does not exist
     * yet, lacks a catalog entity, or still contains entries in the former
     * tier-based format - in the last case a backup copy is made first (see
     * class Javadoc); if that backup fails, the file is left unchanged and
     * the migrated marks are only used in memory. A file that cannot be read at all is logged and left
     * untouched; the shipped defaults are used for this session instead.
     */
    static Map<String, FortMarks> loadWorkspace(Path workspaceFile, Map<String, FortMarks> defaults,
                                                boolean allowNegative, String entityLabel) {
        String fileName = String.valueOf(workspaceFile.getFileName());
        Parsed stored = null;
        if (Files.isRegularFile(workspaceFile)) {
            try {
                stored = parse(Files.readString(workspaceFile, StandardCharsets.UTF_8), fileName, allowNegative,
                        entityLabel);
            } catch (IOException | RuntimeException e) {
                Logger.log(fileName + ": could not read " + workspaceFile + " (" + e.getMessage()
                        + "), using the shipped defaults for this session - the file is left unchanged");
                return new LinkedHashMap<>(defaults);
            }
        }

        Map<String, FortMarks> result = new LinkedHashMap<>();
        boolean complete = stored != null;
        for (var entry : defaults.entrySet()) {
            FortMarks own = stored == null ? null : stored.marksById().get(entry.getKey());
            if (own == null) {
                complete = false;
                result.put(entry.getKey(), entry.getValue());
            } else {
                result.put(entry.getKey(), own);
            }
        }
        if (stored != null) {
            for (String id : stored.marksById().keySet()) {
                if (!defaults.containsKey(id)) {
                    Logger.log(fileName + ": entry for unknown " + entityLabel + " '" + id + "', ignoring it");
                }
            }
        }

        boolean legacy = stored != null && stored.containsLegacyEntries();
        if (legacy && !backupLegacyFile(workspaceFile)) {
            // Never overwrite the user's former file without a backup - work with the migrated values in memory only.
            return result;
        }
        if (!complete || legacy) {
            try {
                JsonSupport.writeJsonFile(toTree(result), workspaceFile);
                String what = stored == null ? "created" : legacy ? "migrated to fortification marks" : "completed";
                Logger.log(fileName + ": " + what + " " + workspaceFile);
            } catch (IOException e) {
                Logger.logException("Could not write " + workspaceFile, e);
            }
        }
        return result;
    }

    /**
     * Parses the content of a mark file - new format and, for migration, the
     * former tier-based format (see class Javadoc). Entries without an id
     * are skipped, unknown mark names are ignored (both logged). A
     * structurally broken document (not a JSON array) throws.
     */
    static Parsed parse(String json, String fileName, boolean allowNegative, String entityLabel) {
        Map<String, FortMarks> result = new LinkedHashMap<>();
        boolean legacy = false;
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            JsonObject obj = element.getAsJsonObject();
            String id = JsonSupport.getStringOrNull(obj, "id");
            if (id == null || id.isBlank()) {
                Logger.log(fileName + ": skipping entry without an id");
                continue;
            }
            Map<String, FortMark> marks = new LinkedHashMap<>();
            if (obj.has("fortMarks")) {
                for (var mark : JsonSupport.getStringMap(obj, "fortMarks").entrySet()) {
                    FortMark parsed = parseMark(mark.getValue());
                    if (parsed == null) {
                        Logger.log(fileName + ": " + entityLabel + " '" + id + "' has unknown mark '"
                                + mark.getValue() + "' for fortification '" + mark.getKey() + "', ignoring it");
                    } else {
                        putMark(marks, mark.getKey(), parsed, allowNegative, fileName, entityLabel, id);
                    }
                }
            } else if (obj.has("generalScore") || obj.has("buffFitScores")) {
                legacy = true;
                for (var tier : JsonSupport.getStringMap(obj, "buffFitScores").entrySet()) {
                    String tierName = tier.getValue() == null ? "" : tier.getValue().trim();
                    if ("GREAT".equals(tierName)) {
                        marks.put(tier.getKey(), FortMark.POSITIVE);
                    } else if ("NEGATIVE".equals(tierName)) {
                        putMark(marks, tier.getKey(), FortMark.NEGATIVE, allowNegative, fileName, entityLabel, id);
                    }
                }
            }
            result.put(id, new FortMarks(marks));
        }
        return new Parsed(result, legacy);
    }

    /**
     * Builds the file content for {@code marksById} (in its iteration order):
     * one entry per entity, {@code fortMarks} sorted by fortification id and
     * omitted when empty. A null value is written like {@link FortMarks#NONE}.
     */
    static JsonArray toTree(Map<String, FortMarks> marksById) {
        JsonArray tree = new JsonArray();
        for (var entry : marksById.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.getKey());
            FortMarks fortMarks = entry.getValue() == null ? FortMarks.NONE : entry.getValue();
            if (!fortMarks.isEmpty()) {
                JsonObject marks = new JsonObject();
                new TreeMap<>(fortMarks.marks()).forEach((fortificationId, mark) ->
                        marks.addProperty(fortificationId, mark.name()));
                obj.add("fortMarks", marks);
            }
            tree.add(obj);
        }
        return tree;
    }

    // --- private ---

    private static FortMark parseMark(String name) {
        if (name == null) {
            return null;
        }
        try {
            return FortMark.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void putMark(Map<String, FortMark> marks, String fortificationId, FortMark mark,
                                boolean allowNegative, String fileName, String entityLabel, String id) {
        if (mark == FortMark.NEGATIVE && !allowNegative) {
            Logger.log(fileName + ": " + entityLabel + " '" + id + "' cannot carry a NEGATIVE mark (fortification '"
                    + fortificationId + "'), ignoring it");
            return;
        }
        marks.put(fortificationId, mark);
    }

    /**
     * Copies a workspace file in the former format to {@code <name>.legacy-<date>.bak}
     * before it is rewritten. Returns false if that failed - the caller then
     * leaves the file untouched.
     */
    private static boolean backupLegacyFile(Path workspaceFile) {
        Path backup = workspaceFile.resolveSibling(workspaceFile.getFileName() + ".legacy-" + LocalDate.now() + ".bak");
        try {
            if (!Files.exists(backup)) {
                Files.copy(workspaceFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Logger.log(workspaceFile.getFileName() + ": saved the former tier-based version as " + backup);
            }
            return true;
        } catch (IOException e) {
            Logger.logException("Could not back up " + workspaceFile + " before migrating it - left unchanged", e);
            return false;
        }
    }
}
