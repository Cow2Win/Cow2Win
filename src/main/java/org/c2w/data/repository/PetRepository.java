package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Pet;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

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
 *     <li>{@code petCowScore.json} - the manually curated {@link org.c2w.data.model.FortMarks}
 *     per pet, persisted through {@link #saveCowScores}. Same file format
 *     and the same shipped-defaults/workspace-copy handling as the heroes'
 *     {@code cowScore.json}, see {@link FortMarkFiles}.</li>
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

    private final Path workspaceDir;
    private volatile Map<String, Pet> petsById;

    /**
     * Loads the catalog right away: the master data from the classpath,
     * the CowScores from the workspace copy in {@code workspaceDir} (created
     * from the shipped defaults if it does not exist yet).
     *
     * @throws RuntimeException if the master data cannot be read
     */
    public PetRepository(Path workspaceDir) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        this.workspaceDir = workspaceDir;
        this.petsById = loadPets();
    }

    /**
     * Returns the pet with the given id, or Optional.empty() if no pet with
     * this id exists.
     */
    public Optional<Pet> findById(String petId) {
        return Optional.ofNullable(petsById.get(petId));
    }

    /**
     * Returns all known pets as a list sorted by id.
     */
    public List<Pet> findAll() {
        return petsById.values().stream()
                .sorted(Comparator.comparing(Pet::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known pets.
     */
    public int count() {
        return petsById.size();
    }

    /**
     * Persists every pet's current {@link Pet#fortMarks()} to the workspace
     * copy of {@code petCowScore.json} (see {@link #cowScoreFile()};
     * pretty-printed, see {@link JsonSupport#writeJsonFile}) and makes {@code
     * catalog} the in-memory catalog for subsequent {@link #findById}/{@link
     * #findAll} calls - the PET-side counterpart of {@link
     * HeroRepository#saveCowScores}. Deliberately does NOT touch {@code
     * pets.json}. Every pet in {@code catalog} gets an entry, see {@link
     * FortMarkFiles#toTree}.
     */
    public synchronized void saveCowScores(List<Pet> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        Map<String, FortMarks> cowScoresById = new LinkedHashMap<>();
        for (Pet pet : catalog) {
            cowScoresById.put(pet.id(), pet.fortMarks());
        }
        JsonSupport.writeJsonFile(FortMarkFiles.toTree(cowScoresById), cowScoreFile());

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
    public Map<String, FortMarks> loadDefaultCowScores() {
        return FortMarkFiles.loadDefaults(PetRepository.class, COW_SCORE_JSON_PATH, petsById.keySet(), false, "pet");
    }

    /**
     * The workspace copy of {@code petCowScore.json} - directly in the workspace folder this repository was
     * created for (see {@link HeroRepository#cowScoreFile()}).
     */
    public Path cowScoreFile() {
        return workspaceDir.resolve(COW_SCORE_FILE_NAME);
    }

    // --- private ---

    private Map<String, Pet> loadPets() {
        try {
            String petsJson = JsonSupport.readClasspathResource(PetRepository.class, PETS_JSON_PATH);
            Map<String, Pet> masterData = parsePetsJson(petsJson);
            Map<String, FortMarks> defaults = FortMarkFiles.loadDefaults(PetRepository.class, COW_SCORE_JSON_PATH,
                    masterData.keySet(), false, "pet");
            Map<String, FortMarks> fortMarks = FortMarkFiles.loadWorkspace(cowScoreFile(), defaults, false, "pet");
            Map<String, Pet> result = new LinkedHashMap<>();
            for (Pet pet : masterData.values()) {
                result.put(pet.id(), new Pet(pet.id(), pet.imagePath(), fortMarks.get(pet.id())));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load pet catalog from " + PETS_JSON_PATH, e);
        }
    }

    private static Map<String, Pet> parsePetsJson(String json) {
        Map<String, Pet> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "image": "..." }, ...] - purely the "objective"
        // master data; fortification marks live in petCowScore.json.
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

        return new Pet(id, image != null ? IMAGE_PATH_PREFIX + image : null, FortMarks.NONE);
    }
}
