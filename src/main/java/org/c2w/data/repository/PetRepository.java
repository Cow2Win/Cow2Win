package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.Pet;
import org.c2w.util.Config;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads/saves the pet catalog from two separate files - the same split
 * {@link HeroRepository} and {@link TitanRepository} use:
 * <ul>
 *     <li>{@code pets.json} - the "objective" master data (id/image) that is
 *     identical for every guild and only changes when Hero Wars itself
 *     changes (new pets, a changed avatar). Never written by this class.
 *     Pets have no roles and no element.</li>
 *     <li>{@code petCowScore.json} - the manually curated {@link CowScore}
 *     per pet, persisted through {@link #saveCowScores}. Same file format
 *     and the same shipped-defaults/workspace-copy handling as the heroes'
 *     {@code cowScore.json}, see {@link CowScoreFiles}.</li>
 * </ul>
 * {@link #findById}/{@link #findAll} merge both files back into the combined
 * {@link Pet} view the rest of the app uses.
 */
public class PetRepository {
    private static final String PETS_JSON_PATH = "/data/pets.json";
    private static final String COW_SCORE_JSON_PATH = "/data/petCowScore.json";

    /** File name of the workspace copy of the pet CowScores - see {@link #cowScoreFile()}. */
    private static final String COW_SCORE_FILE_NAME = "petCowScore.json";

    /** Classpath-relative folder every pet's "image" JSON field is resolved against - see {@link #parsePetObject}. */
    private static final String IMAGE_PATH_PREFIX = "/images/pets/";

    private static volatile Map<String, Pet> petsById;

    /**
     * Returns the pet with the given id, or Optional.empty() if no pet with
     * this id exists.
     */
    public static Optional<Pet> findById(String petId) {
        ensureLoaded();
        return Optional.ofNullable(petsById.get(petId));
    }

    /**
     * Returns all known pets as a list sorted by id.
     */
    public static List<Pet> findAll() {
        ensureLoaded();
        return petsById.values().stream()
                .sorted(Comparator.comparing(Pet::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known pets.
     */
    public static int count() {
        ensureLoaded();
        return petsById.size();
    }

    /**
     * Resets the catalog (for tests). Both JSON files are reloaded on the
     * next access.
     */
    public static void resetCache() {
        synchronized (PetRepository.class) {
            petsById = null;
        }
    }

    /**
     * Persists every pet's current {@link Pet#cowScore()} to the workspace
     * copy of {@code petCowScore.json} (see {@link #cowScoreFile()};
     * pretty-printed, see {@link JsonSupport#writeJsonFile}) and makes {@code
     * catalog} the in-memory catalog for subsequent {@link #findById}/{@link
     * #findAll} calls - the PET-side counterpart of {@link
     * HeroRepository#saveCowScores}. Deliberately does NOT touch {@code
     * pets.json}. Every pet in {@code catalog} gets an entry, see {@link
     * CowScoreFiles#toTree}.
     */
    public static synchronized void saveCowScores(List<Pet> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        Map<String, CowScore> cowScoresById = new LinkedHashMap<>();
        for (Pet pet : catalog) {
            cowScoresById.put(pet.id(), pet.cowScore());
        }
        JsonSupport.writeJsonFile(CowScoreFiles.toTree(cowScoresById), cowScoreFile());

        Map<String, Pet> updated = new LinkedHashMap<>();
        for (Pet pet : catalog) {
            updated.put(pet.id(), pet);
        }
        petsById = updated;
    }

    /**
     * The shipped default CowScore of every known pet (from the {@code
     * petCowScore.json} inside the jar, {@link CowScore#DEFAULT} for a pet
     * without an entry there). Only reads, never writes.
     */
    public static Map<String, CowScore> loadDefaultCowScores() {
        ensureLoaded();
        return CowScoreFiles.loadDefaults(PetRepository.class, COW_SCORE_JSON_PATH, petsById.keySet(), "pet");
    }

    /**
     * The workspace copy of {@code petCowScore.json} - directly in {@link
     * Config#getWorkspaceDir()}, resolved fresh on every call (see {@link
     * HeroRepository#cowScoreFile()}).
     */
    public static Path cowScoreFile() {
        return Config.getWorkspaceDir().resolve(COW_SCORE_FILE_NAME);
    }

    // --- private ---

    private static void ensureLoaded() {
        if (petsById == null) {
            synchronized (PetRepository.class) {
                if (petsById == null) {
                    petsById = loadPets();
                }
            }
        }
    }

    private static Map<String, Pet> loadPets() {
        try {
            String petsJson = JsonSupport.readClasspathResource(PetRepository.class, PETS_JSON_PATH);
            Map<String, Pet> masterData = parsePetsJson(petsJson);
            Map<String, CowScore> defaults = CowScoreFiles.loadDefaults(PetRepository.class, COW_SCORE_JSON_PATH,
                    masterData.keySet(), "pet");
            Map<String, CowScore> cowScores = CowScoreFiles.loadWorkspace(cowScoreFile(), defaults, "pet");
            Map<String, Pet> result = new LinkedHashMap<>();
            for (Pet pet : masterData.values()) {
                result.put(pet.id(), new Pet(pet.id(), pet.imagePath(), cowScores.get(pet.id())));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load pet catalog from " + PETS_JSON_PATH, e);
        }
    }

    private static Map<String, Pet> parsePetsJson(String json) {
        Map<String, Pet> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "image": "..." }, ...] - purely the "objective"
        // master data; generalScore/buffFitScores live in petCowScore.json.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Pet pet = parsePetObject(element.getAsJsonObject());
            if (pet != null) {
                if (result.containsKey(pet.id())) {
                    Logger.log("pets.json: duplicate pet id '" + pet.id() + "', the later entry wins");
                }
                result.put(pet.id(), pet);
            }
        }

        return result;
    }

    private static Pet parsePetObject(JsonObject obj) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String image = JsonSupport.getStringOrNull(obj, "image");

        if (id == null || id.isBlank()) {
            Logger.log("pets.json: skipping invalid pet entry: missing/blank id");
            return null;
        }

        return new Pet(id, image != null ? IMAGE_PATH_PREFIX + image : null, CowScore.DEFAULT);
    }
}
