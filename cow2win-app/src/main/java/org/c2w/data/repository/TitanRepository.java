package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.data.model.TitanRole;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads/saves the titan catalog from two separate files - the same split
 * {@link HeroRepository} uses:
 * <ul>
 *     <li>{@code titans.json} - the "objective" master data (id/element/roles/superTitan/image)
 *     that is identical for every guild and only changes when Hero Wars
 *     itself changes (new titans, a changed element/role/avatar). Never written
 *     by this class.</li>
 *     <li>{@code titanCowScore.json} - Thorsten's manually curated {@link
 *     FortMarks} per titan (positive and negative marks, see that type's
 *     Javadoc), edited via {@code TitanCowScorePanel} and persisted
 *     through {@link #saveCowScores}. Same file format as the heroes'
 *     {@code cowScore.json}, see {@link FortMarkFiles} (which also migrates
 *     the former tier-based format).</li>
 * </ul>
 * Keeping these in two files means a future wholesale refresh of {@code
 * titans.json} can never accidentally clobber the marks in {@code
 * titanCowScore.json}, and vice versa. {@link #findById}/{@link #findAll}
 * merge both files back into the combined {@link Titan} view the rest of the
 * app uses.
 */
public class TitanRepository {
    private static final String TITANS_JSON_PATH = "/data/titans.json";
    private static final String COW_SCORE_JSON_PATH = "/data/titanCowScore.json";

    /** File name of the workspace copy of the titan fortification marks - see {@link #cowScoreFile()}. */
    private static final String COW_SCORE_FILE_NAME = "titanCowScore.json";

    /** Classpath-relative folder every titan's "image" JSON field is resolved against - see {@link #parseTitanObject}. */
    private static final String IMAGE_PATH_PREFIX = "/images/titans/";

    /** Titans may carry negative marks, like heroes - see {@link FortMarkFiles}. */
    private static final boolean ALLOW_NEGATIVE = true;

    /** Used in {@link FortMarkFiles}' log messages. */
    private static final String ENTITY_LABEL = "titan";

    private final Path workspaceDir;
    private volatile Map<String, Titan> titansById;

    /**
     * Loads the catalog right away: the master data from the classpath,
     * the fortification marks from the workspace copy in {@code
     * workspaceDir} (created from the shipped defaults if it does not exist
     * yet, migrated if it is still in the former tier-based format).
     *
     * @throws RuntimeException if the master data cannot be read
     */
    public TitanRepository(Path workspaceDir) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        this.workspaceDir = workspaceDir;
        this.titansById = loadTitans();
    }

    /**
     * Returns the titan with the given id, or Optional.empty() if no titan
     * with this id exists.
     */
    public Optional<Titan> findById(String titanId) {
        return Optional.ofNullable(titansById.get(titanId));
    }

    /**
     * Returns all known titans as a list sorted by id.
     */
    public List<Titan> findAll() {
        return titansById.values().stream()
                .sorted(Comparator.comparing(Titan::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the titans of a given element (sorted by id).
     */
    public List<Titan> findByElement(TitanElement element) {
        return titansById.values().stream()
                .filter(t -> t.element().equals(element))
                .sorted(Comparator.comparing(Titan::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known titans.
     */
    public int count() {
        return titansById.size();
    }

    /**
     * Persists every titan's current {@link Titan#fortMarks()} to the
     * workspace copy of {@code titanCowScore.json} (see {@link
     * #cowScoreFile()}; pretty-printed, see {@link JsonSupport#writeJsonFile}) and makes {@code catalog} the in-memory
     * catalog for subsequent {@link #findById}/{@link #findAll} calls - the
     * TITAN-side counterpart of {@link HeroRepository#saveCowScores}.
     * Deliberately does NOT touch {@code titans.json}. Every titan in {@code
     * catalog} gets an entry, see {@link FortMarkFiles#toTree}.
     */
    public synchronized void saveCowScores(List<Titan> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        Map<String, FortMarks> fortMarksById = new LinkedHashMap<>();
        for (Titan titan : catalog) {
            fortMarksById.put(titan.id(), titan.fortMarks());
        }
        JsonSupport.writeJsonFile(FortMarkFiles.toTree(fortMarksById), cowScoreFile());

        Map<String, Titan> updated = new LinkedHashMap<>();
        for (Titan titan : catalog) {
            updated.put(titan.id(), titan);
        }
        titansById = updated;
    }

    /**
     * The shipped default fortification marks of every known titan (from the
     * {@code titanCowScore.json} inside the jar, {@link FortMarks#NONE} for a
     * titan without an entry there) - what {@code TitanCowScorePanel}'s
     * "restore defaults" button resets its values to. Only reads, never
     * writes: the workspace copy is not touched until the CowScore dialog is saved.
     */
    public Map<String, FortMarks> loadDefaultCowScores() {
        return FortMarkFiles.loadDefaults(TitanRepository.class, COW_SCORE_JSON_PATH, titansById.keySet(),
                ALLOW_NEGATIVE, ENTITY_LABEL);
    }

    /**
     * The workspace copy of {@code titanCowScore.json} - directly in the workspace folder this repository was
     * created for (see {@link HeroRepository#cowScoreFile()}).
     */
    public Path cowScoreFile() {
        return workspaceDir.resolve(COW_SCORE_FILE_NAME);
    }

    // --- private ---

    private Map<String, Titan> loadTitans() {
        try {
            String titansJson = JsonSupport.readClasspathResource(TitanRepository.class, TITANS_JSON_PATH);
            Map<String, Titan> masterData = parseTitansJson(titansJson);
            Map<String, FortMarks> defaults = FortMarkFiles.loadDefaults(TitanRepository.class, COW_SCORE_JSON_PATH,
                    masterData.keySet(), ALLOW_NEGATIVE, ENTITY_LABEL);
            Map<String, FortMarks> fortMarks = FortMarkFiles.loadWorkspace(cowScoreFile(), defaults, ALLOW_NEGATIVE,
                    ENTITY_LABEL, (titanId, fortificationId) -> buffMatches(masterData.get(titanId), fortificationId));
            Map<String, Titan> result = new LinkedHashMap<>();
            for (Titan titan : masterData.values()) {
                result.put(titan.id(), titan.withFortMarks(fortMarks.get(titan.id())));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load titan catalog from " + TITANS_JSON_PATH, e);
        }
    }

    /** True if {@code titan}'s element matches the buff of the fortification {@code fortificationId} - a mark there is not kept. */
    private static boolean buffMatches(Titan titan, String fortificationId) {
        return titan != null && FortificationRepository.findById(fortificationId)
                .map(fortification -> titan.matchesBuff(fortification.buff())).orElse(false);
    }

    /** The titans of a {@code titans.json} text by id, in file order - package-private for tests. */
    static Map<String, Titan> parseTitansJson(String json) {
        Map<String, Titan> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "element": "...", "roles": [...], "superTitan": true, "image": "..." }, ...]
        // ("superTitan" only if true) - purely the
        // "objective" master data; fortification marks live in titanCowScore.json.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Titan titan = parseTitanObject(element.getAsJsonObject());
            if (titan != null) {
                result.put(titan.id(), titan);
            }
        }

        return result;
    }

    private static Titan parseTitanObject(JsonObject obj) {
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

        // Scores used to live directly in titans.json (as generalScore/buffFitScores) - they are no
        // longer read from there, so point out any leftovers instead of dropping them silently.
        if (obj.has("generalScore") || obj.has("buffFitScores")) {
            Logger.log("titans.json: titan '" + id + "' still carries generalScore/buffFitScores - these are "
                    + "ignored now, maintain the titan's fortification marks (fortMarks) in titanCowScore.json "
                    + "instead (via the titan CowScore dialog)");
        }

        List<TitanRole> roles = parseRoles(obj, id);
        JsonElement superTitan = obj.get("superTitan");
        boolean isSuperTitan = superTitan != null && !superTitan.isJsonNull() && superTitan.getAsBoolean();

        return new Titan(id, element, roles, isSuperTitan, image != null ? IMAGE_PATH_PREFIX + image : null, null);
    }

    /**
     * The titan's "roles" in file order: an unknown role is logged and skipped, missing or
     * empty roles are logged as a warning - the titan is loaded anyway (with no roles).
     */
    private static List<TitanRole> parseRoles(JsonObject obj, String id) {
        List<TitanRole> roles = new ArrayList<>();
        JsonElement rolesElement = obj.get("roles");
        if (rolesElement != null && rolesElement.isJsonArray()) {
            for (JsonElement roleElement : rolesElement.getAsJsonArray()) {
                String roleStr = roleElement.isJsonNull() ? null : roleElement.getAsString();
                try {
                    roles.add(TitanRole.valueOf(roleStr));
                } catch (IllegalArgumentException | NullPointerException e) {
                    Logger.log("titans.json: titan '" + id + "' has unknown role '" + roleStr + "', skipping that role");
                }
            }
        }
        if (roles.isEmpty()) {
            Logger.log("titans.json: WARNING - titan '" + id + "' has no (valid) roles, loading it without roles");
        }
        return roles;
    }
}
