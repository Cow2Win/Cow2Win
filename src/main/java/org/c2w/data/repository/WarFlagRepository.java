package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.WarFlag;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads/saves the war flag catalog from two separate files - the same split
 * {@link HeroRepository}, {@link TitanRepository} and {@link PetRepository}
 * use:
 * <ul>
 *     <li>{@code warFlags.json} - the "objective" master data (id/image)
 *     that is identical for every guild and only changes when Hero Wars
 *     itself changes (new war flags, a changed icon). Never written by this
 *     class. War flags have no roles and no element.</li>
 *     <li>{@code warFlagCowScore.json} - the manually curated {@link
 *     org.c2w.data.model.FortMarks} per war flag, persisted through {@link #saveCowScores}. Same
 *     file format and the same shipped-defaults/workspace-copy handling as
 *     the heroes' {@code cowScore.json}, see {@link FortMarkFiles}.</li>
 * </ul>
 * {@link #findById}/{@link #findAll} merge both files back into the combined
 * {@link WarFlag} view the rest of the app uses.
 */
public class WarFlagRepository {
    private static final String WAR_FLAGS_JSON_PATH = "/data/warFlags.json";
    private static final String COW_SCORE_JSON_PATH = "/data/warFlagCowScore.json";

    /** File name of the workspace copy of the war flag CowScores - see {@link #cowScoreFile()}. */
    private static final String COW_SCORE_FILE_NAME = "warFlagCowScore.json";

    /** Classpath-relative folder every war flag's "image" JSON field is resolved against - see {@link #parseWarFlagObject}. */
    private static final String IMAGE_PATH_PREFIX = "/images/flags/";

    /** Entity label used in {@link FortMarkFiles}' log messages. */
    private static final String ENTITY_LABEL = "war flag";

    private final Path workspaceDir;
    private volatile Map<String, WarFlag> warFlagsById;

    /**
     * Loads the catalog right away: the master data from the classpath,
     * the CowScores from the workspace copy in {@code workspaceDir} (created
     * from the shipped defaults if it does not exist yet).
     *
     * @throws RuntimeException if the master data cannot be read
     */
    public WarFlagRepository(Path workspaceDir) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        this.workspaceDir = workspaceDir;
        this.warFlagsById = loadWarFlags();
    }

    /**
     * Returns the war flag with the given id, or Optional.empty() if no war
     * flag with this id exists.
     */
    public Optional<WarFlag> findById(String warFlagId) {
        return Optional.ofNullable(warFlagsById.get(warFlagId));
    }

    /**
     * Returns all known war flags as a list sorted by id.
     */
    public List<WarFlag> findAll() {
        return warFlagsById.values().stream()
                .sorted(Comparator.comparing(WarFlag::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known war flags.
     */
    public int count() {
        return warFlagsById.size();
    }

    /**
     * Persists every war flag's current {@link WarFlag#fortMarks()} to the
     * workspace copy of {@code warFlagCowScore.json} (see {@link
     * #cowScoreFile()}; pretty-printed, see {@link JsonSupport#writeJsonFile})
     * and makes {@code catalog} the in-memory catalog for subsequent {@link
     * #findById}/{@link #findAll} calls - the WAR FLAG counterpart of {@link
     * PetRepository#saveCowScores}. Deliberately does NOT touch {@code
     * warFlags.json}. Every war flag in {@code catalog} gets an entry, see
     * {@link FortMarkFiles#toTree}.
     */
    public synchronized void saveCowScores(List<WarFlag> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        Map<String, FortMarks> cowScoresById = new LinkedHashMap<>();
        for (WarFlag warFlag : catalog) {
            cowScoresById.put(warFlag.id(), warFlag.fortMarks());
        }
        JsonSupport.writeJsonFile(FortMarkFiles.toTree(cowScoresById), cowScoreFile());

        Map<String, WarFlag> updated = new LinkedHashMap<>();
        for (WarFlag warFlag : catalog) {
            updated.put(warFlag.id(), warFlag);
        }
        warFlagsById = updated;
    }

    /**
     * The shipped default CowScore of every known war flag (from the {@code
     * warFlagCowScore.json} inside the jar, {@link CowScore#DEFAULT} for a
     * war flag without an entry there). Only reads, never writes.
     */
    public Map<String, FortMarks> loadDefaultCowScores() {
        return FortMarkFiles.loadDefaults(WarFlagRepository.class, COW_SCORE_JSON_PATH, warFlagsById.keySet(), false, ENTITY_LABEL);
    }

    /**
     * The workspace copy of {@code warFlagCowScore.json} - directly in the workspace folder this repository was
     * created for (see {@link HeroRepository#cowScoreFile()}).
     */
    public Path cowScoreFile() {
        return workspaceDir.resolve(COW_SCORE_FILE_NAME);
    }

    // --- private ---

    private Map<String, WarFlag> loadWarFlags() {
        try {
            String warFlagsJson = JsonSupport.readClasspathResource(WarFlagRepository.class, WAR_FLAGS_JSON_PATH);
            Map<String, WarFlag> masterData = parseWarFlagsJson(warFlagsJson);
            Map<String, FortMarks> defaults = FortMarkFiles.loadDefaults(WarFlagRepository.class, COW_SCORE_JSON_PATH,
                    masterData.keySet(), false, ENTITY_LABEL);
            Map<String, FortMarks> fortMarks = FortMarkFiles.loadWorkspace(cowScoreFile(), defaults, false, ENTITY_LABEL);
            Map<String, WarFlag> result = new LinkedHashMap<>();
            for (WarFlag warFlag : masterData.values()) {
                result.put(warFlag.id(), new WarFlag(warFlag.id(), warFlag.imagePath(), fortMarks.get(warFlag.id())));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load war flag catalog from " + WAR_FLAGS_JSON_PATH, e);
        }
    }

    private static Map<String, WarFlag> parseWarFlagsJson(String json) {
        Map<String, WarFlag> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "image": "..." }, ...] - purely the "objective"
        // master data; fortification marks live in warFlagCowScore.json.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            WarFlag warFlag = parseWarFlagObject(element.getAsJsonObject());
            if (warFlag != null) {
                if (result.containsKey(warFlag.id())) {
                    Logger.log("warFlags.json: duplicate war flag id '" + warFlag.id() + "', the later entry wins");
                }
                result.put(warFlag.id(), warFlag);
            }
        }

        return result;
    }

    private static WarFlag parseWarFlagObject(JsonObject obj) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String image = JsonSupport.getStringOrNull(obj, "image");

        if (id == null || id.isBlank()) {
            Logger.log("warFlags.json: skipping invalid war flag entry: missing/blank id");
            return null;
        }

        return new WarFlag(id, image != null ? IMAGE_PATH_PREFIX + image : null, FortMarks.NONE);
    }
}
