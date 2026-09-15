package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.CowScore;
import org.c2w.data.model.Hero;
import org.c2w.data.model.Role;
import org.c2w.data.model.ScoreTier;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Loads/saves the hero catalog from two separate files (2026-09-14,
 * Trennung von heroes.json und den editierbaren Score-Werten):
 * <ul>
 *     <li>{@code heroes.json} - the "objective" master data (id/roles/image)
 *     that is identical for every guild and only changes when Hero Wars
 *     itself changes (new heroes, a changed role/avatar). Read-only from
 *     this class's own perspective for now - there is no in-app editor for
 *     it yet (see the planned GitHub-based master-data download workflow).</li>
 *     <li>{@code cowScore.json} - Thorsten's manually curated {@link
 *     CowScore} per hero (see that type's Javadoc), edited exclusively via
 *     {@code HeroBuffFitScoresDialog} and persisted through {@link
 *     #saveCowScores}.</li>
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

    /** See {@link JsonSupport#resolveDataFile} for how this resolves in the IDE vs. the packaged app. */
    private static final Path COW_SCORE_FILE_PATH = JsonSupport.resolveDataFile("data", "cowScore.json");

    /** Classpath-relative folder every hero's "image" JSON field is resolved against - see {@link #parseHeroObject}. */
    private static final String IMAGE_PATH_PREFIX = "/images/heroes/";

    private static volatile Map<String, Hero> heroesById;

    /**
     * Returns the hero with the given id, or Optional.empty() if no hero with
     * this id exists.
     */
    public static Optional<Hero> findById(String heroId) {
        ensureLoaded();
        return Optional.ofNullable(heroesById.get(heroId));
    }

    /**
     * Returns all known heroes as a list sorted by id.
     */
    public static List<Hero> findAll() {
        ensureLoaded();
        return heroesById.values().stream()
                .sorted(Comparator.comparing(Hero::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known heroes.
     */
    public static int count() {
        ensureLoaded();
        return heroesById.size();
    }

    /**
     * Resets the catalog (for tests). Both JSON files are reloaded on the
     * next access.
     */
    public static void resetCache() {
        synchronized (HeroRepository.class) {
            heroesById = null;
        }
    }

    /**
     * Persists every hero's current {@link Hero#cowScore()} to {@code
     * cowScore.json} (pretty-printed, see {@link JsonSupport#writeJsonFile})
     * and makes {@code catalog} the in-memory catalog for subsequent {@link
     * #findById}/{@link #findAll} calls - same round-trip convention as
     * {@code FortificationRepository#save}. Deliberately does NOT touch
     * {@code heroes.json} - see this class's own Javadoc for why the two
     * files are kept separate. See {@link #cowScoresToTree} for the one rule
     * this enforces on the way out: a hero whose {@link Hero#cowScore()} is
     * {@link CowScore#isDefault()} gets no entry at all in {@code
     * cowScore.json}, keeping it sparse regardless of what a caller (e.g.
     * {@code HeroBuffFitScoresDialog}) passes in.
     */
    public static synchronized void saveCowScores(List<Hero> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        JsonSupport.writeJsonFile(cowScoresToTree(catalog), COW_SCORE_FILE_PATH);

        Map<String, Hero> updated = new LinkedHashMap<>();
        for (Hero hero : catalog) {
            updated.put(hero.id(), hero);
        }
        heroesById = updated;
    }

    // --- private ---

    private static void ensureLoaded() {
        if (heroesById == null) {
            synchronized (HeroRepository.class) {
                if (heroesById == null) {
                    heroesById = loadHeroes();
                }
            }
        }
    }

    private static Map<String, Hero> loadHeroes() {
        try {
            String heroesJson = JsonSupport.readClasspathResource(HeroRepository.class, HEROES_JSON_PATH);
            Map<String, CowScore> cowScores = loadCowScores();
            return parseHeroesJson(heroesJson, cowScores);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hero catalog from " + HEROES_JSON_PATH, e);
        }
    }

    /**
     * Loads {@code cowScore.json} into an id -> {@link CowScore} map. A
     * missing or malformed file is logged and treated as "no hero has an
     * explicit score yet" (empty map, i.e. every hero falls back to {@link
     * CowScore#DEFAULT}) - same defensive convention as {@code
     * CatalogVersion}: this sidecar file being absent/broken must never
     * prevent {@code heroes.json} (and therefore the whole app) from
     * loading.
     */
    private static Map<String, CowScore> loadCowScores() {
        Map<String, CowScore> result = new LinkedHashMap<>();
        try {
            String json = JsonSupport.readClasspathResource(HeroRepository.class, COW_SCORE_JSON_PATH);
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (var element : array) {
                JsonObject obj = element.getAsJsonObject();
                String id = JsonSupport.getStringOrNull(obj, "id");
                if (id == null || id.isBlank()) {
                    Logger.log("cowScore.json: skipping entry without an id");
                    continue;
                }
                result.put(id, parseCowScore(obj, id));
            }
        } catch (IOException | RuntimeException e) {
            Logger.log("cowScore.json: could not read Cow2Win scores (" + e.getMessage()
                    + "), using the default CowScore for every hero");
        }
        return result;
    }

    private static Map<String, Hero> parseHeroesJson(String json, Map<String, CowScore> cowScores) {
        Map<String, Hero> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "image": "...", "roles": [...] }, ...] - purely the
        // "objective" master data; generalScore/buffFitScores now live in cowScore.json.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Hero hero = parseHeroObject(element.getAsJsonObject(), cowScores);
            if (hero != null) {
                result.put(hero.id(), hero);
            }
        }

        return result;
    }

    private static Hero parseHeroObject(JsonObject obj, Map<String, CowScore> cowScores) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String image = JsonSupport.getStringOrNull(obj, "image");
        List<Role> roles = parseRoles(obj);

        if (id == null || id.isBlank() || roles.isEmpty()) {
            Logger.log("heroes.json: skipping invalid hero entry (id=" + id + "): "
                    + (id == null || id.isBlank() ? "missing/blank id" : "no valid role"));
            return null;
        }

        CowScore cowScore = cowScores.getOrDefault(id, CowScore.DEFAULT);
        return new Hero(id, roles, image != null ? IMAGE_PATH_PREFIX + image : null, cowScore);
    }

    /**
     * Parses one {@code cowScore.json} entry's "generalScore"/"buffFitScores"
     * fields into a {@link CowScore} - see {@link #parseGeneralScore}/{@link
     * #parseBuffFitScores} for the per-field parsing rules (unchanged from
     * before the heroes.json/cowScore.json split, just now producing a
     * {@link CowScore} instead of being stored as two separate {@link Hero}
     * fields).
     */
    private static CowScore parseCowScore(JsonObject obj, String heroId) {
        ScoreTier generalScore = parseGeneralScore(obj, heroId);
        Map<String, ScoreTier> buffFitScores = parseBuffFitScores(obj, heroId);
        return new CowScore(generalScore, buffFitScores);
    }

    /**
     * Parses the optional "generalScore" field (a {@link ScoreTier} name, e.g.
     * "ELEVATED") - absent for most heroes, in which case {@link CowScore}'s
     * own compact constructor falls back to {@link ScoreTier#STANDARD}, so
     * null is returned here both when the field is missing and when it names
     * an unknown tier (logged either way is only the latter, since the former
     * is the expected, sparse-catalog case).
     */
    private static ScoreTier parseGeneralScore(JsonObject obj, String heroId) {
        String name = JsonSupport.getStringOrNull(obj, "generalScore");
        if (name == null) {
            return null;
        }
        try {
            return ScoreTier.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            Logger.log("cowScore.json: hero '" + heroId + "' has unknown generalScore '" + name + "', using the default");
            return null;
        }
    }

    /**
     * Parses the optional "buffFitScores" object (fortification id ->
     * {@link ScoreTier} name, e.g. {"bastion": "ELEVATED"}) - absent/empty
     * for most heroes (sparse, per-fortification overrides only), in which
     * case {@link CowScore#buffFitScore(String, boolean)} falls back to its
     * role-match-based default. An entry with an unknown tier name is
     * skipped (logged) rather than failing the whole hero.
     */
    private static Map<String, ScoreTier> parseBuffFitScores(JsonObject obj, String heroId) {
        Map<String, ScoreTier> result = new LinkedHashMap<>();
        for (var entry : JsonSupport.getStringMap(obj, "buffFitScores").entrySet()) {
            String fortificationId = entry.getKey();
            String tierName = entry.getValue();
            try {
                result.put(fortificationId, ScoreTier.valueOf(tierName.trim()));
            } catch (IllegalArgumentException e) {
                Logger.log("cowScore.json: hero '" + heroId + "' has unknown buffFitScores tier '" + tierName
                        + "' for fortification '" + fortificationId + "', ignoring it");
            }
        }
        return result;
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

    // --- private: writing cowScore.json (see #saveCowScores) ---

    /**
     * Builds the {@code cowScore.json} array: one entry per hero whose
     * {@link Hero#cowScore()} is NOT {@link CowScore#isDefault()} - a hero
     * without any deliberate assessment gets no entry at all (sparser than
     * the old, pre-split {@code heroes.json} writer, which always wrote an
     * "id"/"roles" entry regardless since that file also carried master
     * data).
     */
    private static JsonArray cowScoresToTree(List<Hero> catalog) {
        JsonArray tree = new JsonArray();
        for (Hero hero : catalog) {
            if (!hero.cowScore().isDefault()) {
                tree.add(cowScoreToTree(hero));
            }
        }
        return tree;
    }

    private static JsonObject cowScoreToTree(Hero hero) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", hero.id());
        if (hero.generalScore() != ScoreTier.STANDARD) {
            obj.addProperty("generalScore", hero.generalScore().name());
        }
        JsonObject buffFitScores = buffFitScoresToTree(hero.buffFitScores());
        if (buffFitScores.size() > 0) {
            obj.add("buffFitScores", buffFitScores);
        }
        return obj;
    }

    /**
     * Builds the "buffFitScores" JSON object for one hero, omitting any
     * entry whose tier is {@link ScoreTier#STANDARD} - the same "sparse
     * file, STANDARD is the unwritten default" convention already used for
     * {@link Hero#generalScore()} (see {@link CowScore}'s Javadoc): a
     * missing entry already resolves to STANDARD (when the hero's role
     * matches the fortification's buff) via {@link Hero#buffFitScore}, so
     * persisting it explicitly would only add dead weight to cowScore.json.
     * This is the single place that enforces the rule, so a caller (e.g.
     * {@code HeroBuffFitScoresDialog}) can hand {@link #saveCowScores} a
     * {@link Hero#buffFitScores()} map with STANDARD entries in it (e.g. one
     * left over from before this rule existed) without needing its own
     * filtering - {@link #saveCowScores} always drops them on the way out.
     */
    private static JsonObject buffFitScoresToTree(Map<String, ScoreTier> buffFitScores) {
        JsonObject obj = new JsonObject();
        for (var entry : buffFitScores.entrySet()) {
            if (entry.getValue() != ScoreTier.STANDARD) {
                obj.addProperty(entry.getKey(), entry.getValue().name());
            }
        }
        return obj;
    }
}
