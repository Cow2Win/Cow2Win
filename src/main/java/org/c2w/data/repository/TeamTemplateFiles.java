package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.TeamTemplate;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

/**
 * Reading, validating and writing of a team template file - {@code
 * heroTemplates.json} or {@code titanTemplates.json} (see {@link
 * TeamTemplateRepository}).
 *
 * <p>Unlike the combo files (see {@link TeamComboFiles}) there is no merge:
 * shipped defaults (titans only) are copied into the workspace exactly once,
 * when the workspace file does not exist yet (see {@link #createFromDefaultsIfMissing});
 * from then on the file belongs to the user.
 *
 * <p>File format: a JSON array with one object per occupied template slot -
 * <pre>
 * [
 *   { "slot": 1, "titanIds": ["angus", "eden", "hyperion", "araji", "iyari"] },
 *   { "slot": 3, "titanIds": ["sigurd", "nova", "mairi"] }
 * ]
 * </pre>
 * ({@code heroIds} for hero templates; an empty slot is simply missing).
 * Invalid entries are logged and ignored, never fatal - see {@link #toTemplates}.
 */
final class TeamTemplateFiles {

    static final String FIELD_SLOT = "slot";

    private TeamTemplateFiles() {
        // Utility class, no instantiation
    }

    /** Result of {@link #loadWorkspace}: the valid templates, and whether the file had anything that was ignored. */
    record LoadResult(SortedMap<Integer, TeamTemplate> templates, boolean hadProblems) {
    }

    /**
     * Copies the shipped defaults (classpath resource {@code resourcePath})
     * to {@code workspaceFile} - but only if that file does not exist yet.
     * An existing file is never touched, whatever it contains (even an empty
     * array). A missing or malformed resource is logged and no file is
     * created; a failure to write is logged as well.
     */
    static void createFromDefaultsIfMissing(Path workspaceFile, Class<?> anchor, String resourcePath) {
        if (Files.exists(workspaceFile)) {
            return;
        }
        String fileName = String.valueOf(workspaceFile.getFileName());
        JsonArray defaults;
        try {
            defaults = JsonParser.parseString(JsonSupport.readClasspathResource(anchor, resourcePath)).getAsJsonArray();
        } catch (IOException | RuntimeException e) {
            Logger.log(fileName + " (defaults): could not read the shipped templates (" + e.getMessage()
                    + "), not creating " + workspaceFile);
            return;
        }
        try {
            writeAtomically(defaults, workspaceFile);
            Logger.log(fileName + ": created " + workspaceFile + " from the shipped templates");
        } catch (IOException e) {
            Logger.logException("Could not write " + workspaceFile, e);
        }
    }

    /**
     * Loads the templates from {@code workspaceFile}. A missing file means
     * "no templates" (not an error); a file that cannot be read or parsed is
     * logged and treated as "no templates" as well, see {@link LoadResult#hadProblems()}.
     *
     * @param membersField e.g. {@code "titanIds"}
     */
    static LoadResult loadWorkspace(Path workspaceFile, String membersField) {
        if (!Files.isRegularFile(workspaceFile)) {
            return new LoadResult(new TreeMap<>(), false);
        }
        String fileName = String.valueOf(workspaceFile.getFileName());
        JsonArray entries;
        try {
            entries = JsonParser.parseString(Files.readString(workspaceFile, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (IOException | RuntimeException e) {
            Logger.log(fileName + ": could not read " + workspaceFile + " (" + e.getMessage()
                    + "), no templates available");
            return new LoadResult(new TreeMap<>(), true);
        }
        SortedMap<Integer, TeamTemplate> templates = toTemplates(entries, membersField, fileName);
        return new LoadResult(templates, templates.size() != entries.size());
    }

    /**
     * Turns raw file entries into validated templates, keyed by slot. Every
     * invalid entry is logged and ignored, the rest is still loaded:
     * <ul>
     *     <li>not an object, no {@code slot}, or a slot outside
     *     {@value TeamTemplate#MIN_SLOT}..{@value TeamTemplate#MAX_SLOT};</li>
     *     <li>a slot already used by an earlier entry;</li>
     *     <li>fewer than {@value TeamTemplate#MIN_MEMBERS} or more than
     *     {@value TeamTemplate#MAX_MEMBERS} ids, a blank id or an id listed twice.</li>
     * </ul>
     * Ids are NOT checked against the catalog (see {@link TeamTemplate}).
     *
     * @param fileName only used in log messages
     */
    static SortedMap<Integer, TeamTemplate> toTemplates(JsonArray entries, String membersField, String fileName) {
        SortedMap<Integer, TeamTemplate> result = new TreeMap<>();
        for (JsonElement element : entries) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Integer slot = JsonSupport.getInteger(obj, FIELD_SLOT);
                if (slot == null) {
                    Logger.log(fileName + ": skipping template without a '" + FIELD_SLOT + "': " + element);
                    continue;
                }
                if (result.containsKey(slot)) {
                    Logger.log(fileName + ": template slot " + slot + " is used more than once, ignoring the later one");
                    continue;
                }
                List<String> memberIds = JsonSupport.getStringList(obj, membersField).stream().map(String::trim).toList();
                result.put(slot, new TeamTemplate(slot, memberIds));
            } catch (RuntimeException e) {
                Logger.log(fileName + ": invalid template (" + e.getMessage() + "), ignoring it: " + element);
            }
        }
        return result;
    }

    /** The file content for {@code templates}, ordered by slot. */
    static JsonArray toJson(Collection<TeamTemplate> templates, String membersField) {
        JsonArray array = new JsonArray();
        templates.stream()
                .sorted(Comparator.comparingInt(TeamTemplate::slot))
                .forEach(template -> {
                    JsonObject obj = new JsonObject();
                    obj.addProperty(FIELD_SLOT, template.slot());
                    obj.add(membersField, JsonSupport.toStringArray(template.memberIds()));
                    array.add(obj);
                });
        return array;
    }

    /**
     * Writes {@code tree} pretty-printed to {@code target} via a temporary
     * file in the same folder that is then moved over the target, so an
     * aborted write never leaves a half-written file behind.
     */
    static void writeAtomically(JsonElement tree, Path target) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName() + ".", ".tmp");
        try {
            JsonSupport.writeJsonFile(tree, temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Copies {@code file} to {@code <name>.invalid-<date>.bak} next to it
     * (an existing backup of the same day is kept). Used before the first
     * save over a file that had invalid content, so a hand-editing mistake
     * is not lost silently. Returns false if that failed.
     */
    static boolean backupInvalid(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".invalid-" + LocalDate.now() + ".bak");
        try {
            if (Files.exists(file) && !Files.exists(backup)) {
                Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Logger.log(file.getFileName() + ": saved the version with invalid entries as " + backup);
            }
            return true;
        } catch (IOException e) {
            Logger.logException("Could not back up " + file, e);
            return false;
        }
    }
}
