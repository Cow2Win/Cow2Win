package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.util.Config;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads/saves the titan catalog from two separate files (2026-09-28, the
 * same split {@link HeroRepository} got on 2026-09-14):
 * <ul>
 *     <li>{@code titans.json} - the "objective" master data (id/element/image)
 *     that is identical for every guild and only changes when Hero Wars
 *     itself changes (new titans, a changed element/avatar). Never written
 *     by this class.</li>
 *     <li>{@code titanCowScore.json} - Thorsten's manually curated {@link
 *     CowScore} per titan (see that type's Javadoc), persisted through
 *     {@link #saveCowScores}. Same file format as the heroes'
 *     {@code cowScore.json}, see {@link CowScoreFiles}.</li>
 * </ul>
 * Keeping these in two files means a future wholesale refresh of {@code
 * titans.json} can never accidentally clobber the scores in {@code
 * titanCowScore.json}, and vice versa. {@link #findById}/{@link #findAll}
 * merge both files back into the combined {@link Titan} view the rest of the
 * app uses.
 */
public class TitanRepository {
    private static final String TITANS_JSON_PATH = "/data/titans.json";
    private static final String COW_SCORE_JSON_PATH = "/data/titanCowScore.json";

    /** File name of the workspace copy of the titan CowScores - see {@link #cowScoreFile()}. */
    private static final String COW_SCORE_FILE_NAME = "titanCowScore.json";

    /** Classpath-relative folder every titan's "image" JSON field is resolved against - see {@link #parseTitanObject}. */
    private static final String IMAGE_PATH_PREFIX = "/images/titans/";

    private static volatile Map<String, Titan> titansById;

    /**
     * Returns the titan with the given id, or Optional.empty() if no titan
     * with this id exists.
     */
    public static Optional<Titan> findById(String titanId) {
        ensureLoaded();
        return Optional.ofNullable(titansById.get(titanId));
    }

    /**
     * Returns all known titans as a list sorted by id.
     */
    public static List<Titan> findAll() {
        ensureLoaded();
        return titansById.values().stream()
                .sorted(Comparator.comparing(Titan::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the titans of a given element (sorted by id).
     */
    public static List<Titan> findByElement(TitanElement element) {
        ensureLoaded();
        return titansById.values().stream()
                .filter(t -> t.element().equals(element))
                .sorted(Comparator.comparing(Titan::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known titans.
     */
    public static int count() {
        ensureLoaded();
        return titansById.size();
    }

    /**
     * Resets the catalog (for tests). Both JSON files are reloaded on the
     * next access.
     */
    public static void resetCache() {
        synchronized (TitanRepository.class) {
            titansById = null;
        }
    }

    /**
     * Persists every titan's current {@link Titan#cowScore()} to the
     * workspace copy of {@code titanCowScore.json} (see {@link
     * #cowScoreFile()}; pretty-printed, see {@link JsonSupport#writeJsonFile}) and makes {@code catalog} the in-memory
     * catalog for subsequent {@link #findById}/{@link #findAll} calls - the
     * TITAN-side counterpart of {@link HeroRepository#saveCowScores}.
     * Deliberately does NOT touch {@code titans.json}. Every titan in {@code
     * catalog} gets an entry, see {@link CowScoreFiles#toTree}.
     */
    public static synchronized void saveCowScores(List<Titan> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        Map<String, CowScore> cowScoresById = new LinkedHashMap<>();
        for (Titan titan : catalog) {
            cowScoresById.put(titan.id(), titan.cowScore());
        }
        JsonSupport.writeJsonFile(CowScoreFiles.toTree(cowScoresById), cowScoreFile());

        Map<String, Titan> updated = new LinkedHashMap<>();
        for (Titan titan : catalog) {
            updated.put(titan.id(), titan);
        }
        titansById = updated;
    }

    /**
     * The shipped default CowScore of every known titan (from the {@code
     * titanCowScore.json} inside the jar, {@link CowScore#DEFAULT} for a
     * titan without an entry there) - what {@code TitanCoreScoreDialog}'s
     * "restore defaults" button resets its values to. Only reads, never
     * writes: the workspace copy is not touched until the dialog is saved.
     */
    public static Map<String, CowScore> loadDefaultCowScores() {
        ensureLoaded();
        return CowScoreFiles.loadDefaults(TitanRepository.class, COW_SCORE_JSON_PATH, titansById.keySet(), "titan");
    }

    /**
     * The workspace copy of {@code titanCowScore.json} - directly in {@link
     * Config#getWorkspaceDir()}, resolved fresh on every call (see {@link
     * HeroRepository#cowScoreFile()}).
     */
    public static Path cowScoreFile() {
        return Config.getWorkspaceDir().resolve(COW_SCORE_FILE_NAME);
    }

    // --- private ---

    private static void ensureLoaded() {
        if (titansById == null) {
            synchronized (TitanRepository.class) {
                if (titansById == null) {
                    titansById = loadTitans();
                }
            }
        }
    }

    private static Map<String, Titan> loadTitans() {
        try {
            String titansJson = JsonSupport.readClasspathResource(TitanRepository.class, TITANS_JSON_PATH);
            Map<String, Titan> masterData = parseTitansJson(titansJson, Map.of());
            Map<String, CowScore> defaults = CowScoreFiles.loadDefaults(TitanRepository.class, COW_SCORE_JSON_PATH,
                    masterData.keySet(), "titan");
            Map<String, CowScore> cowScores = CowScoreFiles.loadWorkspace(cowScoreFile(), defaults, "titan");
            Map<String, Titan> result = new LinkedHashMap<>();
            for (Titan titan : masterData.values()) {
                result.put(titan.id(), new Titan(titan.id(), titan.element(), titan.imagePath(), cowScores.get(titan.id())));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load titan catalog from " + TITANS_JSON_PATH, e);
        }
    }

    private static Map<String, Titan> parseTitansJson(String json, Map<String, CowScore> cowScores) {
        Map<String, Titan> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "element": "...", "image": "..." }, ...] - purely the
        // "objective" master data; generalScore/buffFitScores live in titanCowScore.json.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Titan titan = parseTitanObject(element.getAsJsonObject(), cowScores);
            if (titan != null) {
                result.put(titan.id(), titan);
            }
        }

        return result;
    }

    private static Titan parseTitanObject(JsonObject obj, Map<String, CowScore> cowScores) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String elementStr = JsonSupport.getStringOrNull(obj, "element");
        String image = JsonSupport.getStringOrNull(obj, "image");

        if (id == null || id.isBlank() || elementStr == null || elementStr.isBlank()) {
            Logger.log("titans.json: skipping invalid titan entry (id=" + id + "): missing/blank id or element");
            return null;
        }

        TitanElement element;
        try {
            element = TitanElement.valueOf(elementStr);
        } catch (IllegalArgumentException e) {
            Logger.log("titans.json: titan '" + id + "' has unknown element '" + elementStr + "', skipping it");
            return null;
        }

        // Scores used to live directly in titans.json (before 2026-09-28) - they are no longer
        // read from there, so point out any leftovers instead of dropping them silently.
        if (obj.has("generalScore") || obj.has("buffFitScores")) {
            Logger.log("titans.json: titan '" + id + "' still carries generalScore/buffFitScores - these are "
                    + "ignored now, move them to titanCowScore.json");
        }

        CowScore cowScore = cowScores.getOrDefault(id, CowScore.DEFAULT);
        return new Titan(id, element, image != null ? IMAGE_PATH_PREFIX + image : null, cowScore);
    }
}
