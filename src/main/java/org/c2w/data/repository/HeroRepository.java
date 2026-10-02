package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Hero;
import org.c2w.data.model.Role;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Loads/saves the hero catalog from two separate files:
 * <ul>
 *     <li>{@code heroes.json} - the "objective" master data (id/roles/image)
 *     that is identical for every guild and only changes when Hero Wars
 *     itself changes (new heroes, a changed role/avatar). Read-only from
 *     this class's own perspective for now - there is no in-app editor for
 *     it yet (see the planned GitHub-based master-data download workflow).</li>
 *     <li>{@code cowScore.json} - Thorsten's manually curated {@link
 *     org.c2w.data.model.FortMarks} per hero (see that type's Javadoc), edited exclusively via
 *     {@code HeroCoreScoreDialog} and persisted through {@link
 *     #saveCowScores}. The copy the app reads and writes
 *     lives in the workspace folder (see {@link #cowScoreFile()}); the copy
 *     inside the jar only holds the shipped defaults it is created from -
 *     see {@link FortMarkFiles}' class Javadoc.</li>
 * </ul>
 * Keeping these in two files means a future wholesale refresh of {@code
 * heroes.json} (e.g. pulling updated master data from GitHub) can never
 * accidentally clobber the scores in {@code cowScore.json}, and vice versa -
 * {@link #saveCowScores} only ever writes {@code cowScore.json}, never
 * {@code heroes.json}. {@link #findById}/{@link #findAll} merge both files
 * back into the combined {@link Hero} view the rest of the app uses, exactly
 * as before this split.
 */
public class HeroRepository {
    private static final String HEROES_JSON_PATH = "/data/heroes.json";
    private static final String COW_SCORE_JSON_PATH = "/data/cowScore.json";

    /** File name of the workspace copy of the hero CowScores - see {@link #cowScoreFile()}. */
    private static final String COW_SCORE_FILE_NAME = "cowScore.json";

    /** Classpath-relative folder every hero's "image" JSON field is resolved against - see {@link #parseHeroObject}. */
    private static final String IMAGE_PATH_PREFIX = "/images/heroes/";

    private final Path workspaceDir;
    private volatile Map<String, Hero> heroesById;

    /**
     * Loads the catalog right away: the master data from the classpath,
     * the CowScores from the workspace copy in {@code workspaceDir} (created
     * from the shipped defaults if it does not exist yet).
     *
     * @throws RuntimeException if the master data cannot be read
     */
    public HeroRepository(Path workspaceDir) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        this.workspaceDir = workspaceDir;
        this.heroesById = loadHeroes();
    }

    /**
     * Returns the hero with the given id, or Optional.empty() if no hero with
     * this id exists.
     */
    public Optional<Hero> findById(String heroId) {
        return Optional.ofNullable(heroesById.get(heroId));
    }

    /**
     * Returns all known heroes as a list sorted by id.
     */
    public List<Hero> findAll() {
        return heroesById.values().stream()
                .sorted(Comparator.comparing(Hero::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known heroes.
     */
    public int count() {
        return heroesById.size();
    }

    /**
     * Persists every hero's current {@link Hero#fortMarks()} to the
     * workspace copy of {@code cowScore.json} (see {@link #cowScoreFile()};
     * pretty-printed, see {@link JsonSupport#writeJsonFile})
     * and makes {@code catalog} the in-memory catalog for subsequent {@link
     * #findById}/{@link #findAll} calls. Deliberately does NOT touch
     * {@code heroes.json} - see this class's own Javadoc for why the two
     * files are kept separate. Every hero in {@code catalog} gets an entry,
     * see {@link FortMarkFiles#toTree} for the exact format.
     */
    public synchronized void saveCowScores(List<Hero> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        Map<String, FortMarks> cowScoresById = new LinkedHashMap<>();
        for (Hero hero : catalog) {
            cowScoresById.put(hero.id(), hero.fortMarks());
        }
        JsonSupport.writeJsonFile(FortMarkFiles.toTree(cowScoresById), cowScoreFile());

        Map<String, Hero> updated = new LinkedHashMap<>();
        for (Hero hero : catalog) {
            updated.put(hero.id(), hero);
        }
        heroesById = updated;
    }

    /**
     * The shipped default fortification marks of every known hero (from the {@code
     * cowScore.json} inside the jar, {@link FortMarks#NONE} for a hero
     * without an entry there) - what {@code HeroCoreScoreDialog}'s "restore
     * defaults" button resets its values to. Only reads, never writes: the
     * workspace copy is not touched until the dialog is saved.
     */
    public Map<String, FortMarks> loadDefaultCowScores() {
        return FortMarkFiles.loadDefaults(HeroRepository.class, COW_SCORE_JSON_PATH, heroesById.keySet(), true, "hero");
    }

    /**
     * The workspace copy of {@code cowScore.json} - directly in the workspace folder this repository was
     * created for. A workspace change in the settings only takes effect after
     * a restart, when a new repository is created for the new folder.
     */
    public Path cowScoreFile() {
        return workspaceDir.resolve(COW_SCORE_FILE_NAME);
    }

    // --- private ---

    private Map<String, Hero> loadHeroes() {
        try {
            String heroesJson = JsonSupport.readClasspathResource(HeroRepository.class, HEROES_JSON_PATH);
            Map<String, Hero> masterData = parseHeroesJson(heroesJson, Map.of());
            Map<String, FortMarks> defaults = FortMarkFiles.loadDefaults(HeroRepository.class, COW_SCORE_JSON_PATH,
                    masterData.keySet(), true, "hero");
            Map<String, FortMarks> fortMarks = FortMarkFiles.loadWorkspace(cowScoreFile(), defaults, true, "hero");
            Map<String, Hero> result = new LinkedHashMap<>();
            for (Hero hero : masterData.values()) {
                result.put(hero.id(), new Hero(hero.id(), hero.roles(), hero.imagePath(), fortMarks.get(hero.id())));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hero catalog from " + HEROES_JSON_PATH, e);
        }
    }

    private static Map<String, Hero> parseHeroesJson(String json, Map<String, FortMarks> cowScores) {
        Map<String, Hero> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "image": "...", "roles": [...] }, ...] - purely the
        // "objective" master data; fortification marks now live in cowScore.json.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Hero hero = parseHeroObject(element.getAsJsonObject(), cowScores);
            if (hero != null) {
                result.put(hero.id(), hero);
            }
        }

        return result;
    }

    private static Hero parseHeroObject(JsonObject obj, Map<String, FortMarks> cowScores) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String image = JsonSupport.getStringOrNull(obj, "image");
        List<Role> roles = parseRoles(obj);

        if (id == null || id.isBlank() || roles.isEmpty()) {
            Logger.log("heroes.json: skipping invalid hero entry (id=" + id + "): "
                    + (id == null || id.isBlank() ? "missing/blank id" : "no valid role"));
            return null;
        }

        FortMarks fortMarks = cowScores.getOrDefault(id, FortMarks.NONE);
        return new Hero(id, roles, image != null ? IMAGE_PATH_PREFIX + image : null, fortMarks);
    }

    private static List<Role> parseRoles(JsonObject obj) {
        List<Role> roles = new ArrayList<>();
        String heroId = JsonSupport.getStringOrNull(obj, "id");
        for (String roleName : JsonSupport.getStringList(obj, "roles")) {
            try {
                roles.add(Role.valueOf(roleName.trim()));
            } catch (IllegalArgumentException e) {
                Logger.log("heroes.json: hero '" + heroId + "' has unknown role '" + roleName + "', ignoring it");
            }
        }
        return roles;
    }
}
