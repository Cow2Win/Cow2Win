package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.ComboSource;
import org.c2w.data.model.TeamCombo;
import org.c2w.data.model.TeamCombos;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Reading, merging and writing of a team combo file - currently {@code
 * heroCombos.json} (see {@link HeroComboRepository}); built so that a later
 * {@code titanCombos.json} only needs a different members field and catalog.
 *
 * <p>Like {@link FortMarkFiles}, each file exists twice: the <b>shipped
 * defaults</b> (a read-only classpath resource, see {@link #loadDefaults})
 * and the <b>workspace copy</b> the app actually reads, which the user
 * maintains by hand for now (see {@link #loadWorkspace}).
 *
 * <p>File format: a JSON array with one object per combo -
 * <pre>
 * [
 *   {
 *     "id": "sebastian-nebula",
 *     "heroIds": ["sebastian", "nebula"],
 *     "source": "C2W",
 *     "deactivated": "2026-09-30"
 *   }
 * ]
 * </pre>
 * {@code id} is the unique key, the optional {@code name} a custom label
 * shown untranslated instead of the members' localized names (see {@code
 * org.c2w.i18n.ComboTexts}), the members field ({@code heroIds}) lists
 * {@value TeamCombo#MIN_MEMBERS} to {@value TeamCombo#MAX_MEMBERS} catalog
 * ids, {@code source} is {@code C2W} (shipped) or {@code USER} (added or
 * adapted by the user, see {@link ComboSource}) and the optional {@code
 * deactivated} date switches a combo off. <b>Whoever edits or deactivates a
 * shipped combo by hand has to set its {@code source} to {@code USER}</b> -
 * otherwise the next start replaces it with the default version again.
 *
 * <p>Invalid combos are logged and ignored, never fatal - see {@link #toCombos}.
 */
final class TeamComboFiles {

    static final String FIELD_ID = "id";
    static final String FIELD_NAME = "name";
    static final String FIELD_SOURCE = "source";
    static final String FIELD_DEACTIVATED = "deactivated";

    private TeamComboFiles() {
        // Utility class, no instantiation
    }

    /** Result of {@link #merge}: the merged file content and whether it differs from the workspace file. */
    record MergeResult(JsonArray entries, boolean changed) {
    }

    /**
     * Reads the shipped default combos (classpath resource {@code
     * resourcePath}) as raw file entries. A missing or malformed resource is
     * logged and treated as "no defaults".
     */
    static JsonArray loadDefaults(Class<?> anchor, String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        try {
            return JsonParser.parseString(JsonSupport.readClasspathResource(anchor, resourcePath)).getAsJsonArray();
        } catch (IOException | RuntimeException e) {
            Logger.log(fileName + " (defaults): could not read combos (" + e.getMessage() + "), using none");
            return new JsonArray();
        }
    }

    /**
     * Merges {@code defaults} into the workspace copy {@code workspaceFile}
     * (see {@link #merge}) and returns the raw entries the app works with.
     * The file is only written if the merge actually changed something: a
     * missing file is created from the defaults; an existing one is first
     * copied to {@code <name>.before-update-<date>.bak} next to it - if that
     * backup fails, the file is left unchanged and the merged entries are
     * only used in memory. A file that cannot be read at all is logged and
     * left untouched; the defaults are used for this session instead.
     */
    static JsonArray loadWorkspace(Path workspaceFile, JsonArray defaults) {
        String fileName = String.valueOf(workspaceFile.getFileName());
        JsonArray stored = null;
        if (Files.isRegularFile(workspaceFile)) {
            try {
                stored = JsonParser.parseString(Files.readString(workspaceFile, StandardCharsets.UTF_8)).getAsJsonArray();
            } catch (IOException | RuntimeException e) {
                Logger.log(fileName + ": could not read " + workspaceFile + " (" + e.getMessage()
                        + "), using the shipped combos for this session - the file is left unchanged");
                return defaults.deepCopy();
            }
        }

        MergeResult merged = merge(defaults, stored);
        if (!merged.changed()) {
            return merged.entries();
        }
        if (stored != null && !backup(workspaceFile)) {
            // Never overwrite the user's file without a backup - work with the merged combos in memory only.
            return merged.entries();
        }
        try {
            JsonSupport.writeJsonFile(merged.entries(), workspaceFile);
            Logger.log(fileName + ": " + (stored == null ? "created" : "updated the shipped combos in") + " "
                    + workspaceFile);
        } catch (IOException e) {
            Logger.logException("Could not write " + workspaceFile, e);
        }
        return merged.entries();
    }

    /**
     * Merges the shipped {@code defaults} into the workspace entries {@code
     * workspace} (null if there is no workspace file yet - then the result
     * is a copy of the defaults):
     * <ul>
     *     <li>a {@code C2W} entry is replaced by the default with the same id;</li>
     *     <li>a {@code C2W} entry whose id is no longer shipped is removed;</li>
     *     <li>{@code USER} entries (and anything that is not a recognisable
     *     {@code C2W} entry) are kept exactly as they are - a {@code USER}
     *     entry also wins over a default with the same id;</li>
     *     <li>defaults not in the workspace yet are appended, in their order.</li>
     * </ul>
     * Workspace entries keep their order. Defaults are always added as {@code C2W}.
     */
    static MergeResult merge(JsonArray defaults, JsonArray workspace) {
        Map<String, JsonObject> defaultsById = new LinkedHashMap<>();
        for (JsonElement element : defaults) {
            String id = idOf(element);
            if (id == null) {
                Logger.log("Shipped combo without an id, ignoring it: " + element);
            } else if (defaultsById.containsKey(id)) {
                Logger.log("Shipped combo id '" + id + "' is listed twice, using the first one");
            } else {
                JsonObject copy = element.getAsJsonObject().deepCopy();
                copy.addProperty(FIELD_SOURCE, ComboSource.C2W.name());
                defaultsById.put(id, copy);
            }
        }
        if (workspace == null) {
            return new MergeResult(toArray(defaultsById.values()), true);
        }

        Set<String> userIds = new HashSet<>();
        for (JsonElement element : workspace) {
            String id = idOf(element);
            if (id != null && !isC2w(element)) {
                userIds.add(id);
            }
        }

        JsonArray result = new JsonArray();
        Set<String> placed = new HashSet<>(userIds);
        for (JsonElement element : workspace) {
            String id = idOf(element);
            if (id == null || !isC2w(element)) {
                result.add(element.deepCopy());
            } else if (defaultsById.containsKey(id) && placed.add(id)) {
                result.add(defaultsById.get(id).deepCopy());
            }
            // otherwise: a C2W combo no longer shipped, overridden by a USER combo or listed twice - dropped
        }
        for (var entry : defaultsById.entrySet()) {
            if (placed.add(entry.getKey())) {
                result.add(entry.getValue().deepCopy());
            }
        }
        return new MergeResult(result, !result.equals(workspace));
    }

    /**
     * Turns raw file entries into validated combos. Every invalid entry is
     * logged and ignored, the rest is still loaded:
     * <ul>
     *     <li>no id, or an id already used by an earlier entry;</li>
     *     <li>fewer than {@value TeamCombo#MIN_MEMBERS} or more than
     *     {@value TeamCombo#MAX_MEMBERS} members, a member listed twice, or
     *     a member id not in {@code knownMemberIds};</li>
     *     <li>a {@code deactivated} value that is not an ISO date.</li>
     * </ul>
     * A missing or unknown {@code source} is read as {@link ComboSource#USER}
     * (which is also how the merge treats it); a missing name means "no custom label".
     *
     * @param membersField   e.g. {@code "heroIds"}
     * @param knownMemberIds the ids of the catalog the members must come from
     * @param fileName       only used in log messages
     */
    static TeamCombos toCombos(JsonArray entries, String membersField, Set<String> knownMemberIds, String fileName) {
        List<TeamCombo> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (JsonElement element : entries) {
            String id = idOf(element);
            if (id == null) {
                Logger.log(fileName + ": skipping combo without an id: " + element);
                continue;
            }
            if (!seenIds.add(id)) {
                Logger.log(fileName + ": combo id '" + id + "' is used more than once, ignoring the later combo");
                continue;
            }
            try {
                TeamCombo combo = toCombo(element.getAsJsonObject(), id, membersField, knownMemberIds, fileName);
                if (combo != null) {
                    result.add(combo);
                }
            } catch (RuntimeException e) {
                Logger.log(fileName + ": combo '" + id + "' is invalid (" + e.getMessage() + "), ignoring it");
            }
        }
        return new TeamCombos(result);
    }

    // --- private ---

    private static TeamCombo toCombo(JsonObject obj, String id, String membersField, Set<String> knownMemberIds,
                                     String fileName) {
        List<String> memberIds = JsonSupport.getStringList(obj, membersField).stream().map(String::trim).toList();
        if (memberIds.size() < TeamCombo.MIN_MEMBERS || memberIds.size() > TeamCombo.MAX_MEMBERS) {
            Logger.log(fileName + ": combo '" + id + "' needs " + TeamCombo.MIN_MEMBERS + " to "
                    + TeamCombo.MAX_MEMBERS + " entries in '" + membersField + "', has " + memberIds.size()
                    + " - ignoring it");
            return null;
        }
        if (new HashSet<>(memberIds).size() != memberIds.size()) {
            Logger.log(fileName + ": combo '" + id + "' lists a member twice " + memberIds + " - ignoring it");
            return null;
        }
        List<String> unknown = memberIds.stream().filter(memberId -> !knownMemberIds.contains(memberId)).toList();
        if (!unknown.isEmpty()) {
            Logger.log(fileName + ": combo '" + id + "' has unknown ids " + unknown + " - ignoring it");
            return null;
        }

        LocalDate deactivated = null;
        String deactivatedValue = JsonSupport.getStringOrNull(obj, FIELD_DEACTIVATED);
        if (deactivatedValue != null && !deactivatedValue.isBlank()) {
            try {
                deactivated = LocalDate.parse(deactivatedValue.trim());
            } catch (DateTimeParseException e) {
                Logger.log(fileName + ": combo '" + id + "' has an invalid '" + FIELD_DEACTIVATED + "' date '"
                        + deactivatedValue + "' (expected e.g. 2026-09-30) - ignoring it");
                return null;
            }
        }

        String sourceValue = JsonSupport.getStringOrNull(obj, FIELD_SOURCE);
        ComboSource source = parseSource(sourceValue);
        if (source == null) {
            Logger.log(fileName + ": combo '" + id + "' has no/unknown source '" + sourceValue + "', treating it as "
                    + ComboSource.USER);
            source = ComboSource.USER;
        }
        return new TeamCombo(id, JsonSupport.getStringOrNull(obj, FIELD_NAME), memberIds, source, deactivated);
    }

    /** The trimmed id of a combo entry, null if it is not an object or has no (non-blank) id. */
    private static String idOf(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        try {
            String id = JsonSupport.getStringOrNull(element.getAsJsonObject(), FIELD_ID);
            return id == null || id.isBlank() ? null : id.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isC2w(JsonElement element) {
        try {
            return parseSource(JsonSupport.getStringOrNull(element.getAsJsonObject(), FIELD_SOURCE)) == ComboSource.C2W;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static ComboSource parseSource(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ComboSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonArray toArray(Collection<JsonObject> entries) {
        JsonArray array = new JsonArray();
        entries.forEach(entry -> array.add(entry.deepCopy()));
        return array;
    }

    /**
     * Copies {@code workspaceFile} to {@code <name>.before-update-<date>.bak}
     * before it is rewritten (an existing backup of the same day is kept -
     * it holds the older state). Returns false if that failed.
     */
    private static boolean backup(Path workspaceFile) {
        Path backup = workspaceFile.resolveSibling(workspaceFile.getFileName() + ".before-update-" + LocalDate.now()
                + ".bak");
        try {
            if (!Files.exists(backup)) {
                Files.copy(workspaceFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Logger.log(workspaceFile.getFileName() + ": saved the previous version as " + backup);
            }
            return true;
        } catch (IOException e) {
            Logger.logException("Could not back up " + workspaceFile + " before updating it - left unchanged", e);
            return false;
        }
    }
}
