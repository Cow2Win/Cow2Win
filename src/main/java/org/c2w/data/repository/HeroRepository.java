package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.Hero;
import org.c2w.data.model.Role;
import org.c2w.data.model.ScoreTier;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


public class HeroRepository {
    private static final String JSON_PATH = "/data/heroes.json";

    private static final Path CATALOG_FILE_PATH = Paths.get("src", "main", "resources", "data", "heroes.json");

    /** Classpath-relative folder every hero's "image" JSON field is resolved against - see {@link #parseHeroObject}/{@link #stripImagePrefix}. */
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
     * Resets the catalog (for tests). The JSON file is reloaded on the next
     * access.
     */
    public static void resetCache() {
        synchronized (HeroRepository.class) {
            heroesById = null;
        }
    }

    /**
     * Persists the given catalog to {@code heroes.json} (pretty-printed, see
     * {@link JsonSupport#writeJsonFile}) and makes it the in-memory catalog
     * for subsequent {@link #findById}/{@link #findAll} calls - same
     * round-trip convention as {@code FortificationRepository#save}. See
     * {@link #buffFitScoresToTree} for the one rule this enforces on the way
     * out: a {@link ScoreTier#STANDARD} entry in
     * {@link Hero#buffFitScores()} (or {@link Hero#generalScore()} itself)
     * is never written, keeping the catalog sparse regardless of what a
     * caller (e.g. {@code HeroBuffFitScoresDialog}) passes in.
     */
    public static synchronized void save(List<Hero> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        JsonSupport.writeJsonFile(catalogToTree(catalog), CATALOG_FILE_PATH);

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
            String json = JsonSupport.readClasspathResource(HeroRepository.class, JSON_PATH);
            return parseHeroesJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hero catalog from " + JSON_PATH, e);
        }
    }

    private static Map<String, Hero> parseHeroesJson(String json) {
        Map<String, Hero> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "image": "...", "roles": [...], "generalScore": "..." (optional),
        //             "buffFitScores": {"fortificationId": "TIER", ...} (optional) }, ...]
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Hero hero = parseHeroObject(element.getAsJsonObject());
            if (hero != null) {
                result.put(hero.id(), hero);
            }
        }

        return result;
    }

    private static Hero parseHeroObject(JsonObject obj) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String image = JsonSupport.getStringOrNull(obj, "image");
        List<Role> roles = parseRoles(obj);
        ScoreTier generalScore = parseGeneralScore(obj, id);
        Map<String, ScoreTier> buffFitScores = parseBuffFitScores(obj, id);

        if (id == null || id.isBlank() || roles.isEmpty()) {
            Logger.log("heroes.json: skipping invalid hero entry (id=" + id + "): "
                    + (id == null || id.isBlank() ? "missing/blank id" : "no valid role"));
            return null;
        }

        return new Hero(id, roles, image != null ? IMAGE_PATH_PREFIX + image : null, generalScore, buffFitScores);
    }

    /**
     * Parses the optional "generalScore" field (a {@link ScoreTier} name, e.g.
     * "ELEVATED") - absent for most heroes, in which case {@link Hero}'s own
     * compact constructor falls back to {@link ScoreTier#STANDARD}, so null is
     * returned here both when the field is missing and when it names an
     * unknown tier (logged either way is only the latter, since the former is
     * the expected, sparse-catalog case).
     */
    private static ScoreTier parseGeneralScore(JsonObject obj, String heroId) {
        String name = JsonSupport.getStringOrNull(obj, "generalScore");
        if (name == null) {
            return null;
        }
        try {
            return ScoreTier.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            Logger.log("heroes.json: hero '" + heroId + "' has unknown generalScore '" + name + "', using the default");
            return null;
        }
    }

    /**
     * Parses the optional "buffFitScores" object (fortification id ->
     * {@link ScoreTier} name, e.g. {"bastion": "ELEVATED"}) - absent/empty
     * for most heroes (sparse, per-fortification overrides only), in which
     * case {@link Hero#buffFitScore(String, boolean)} falls back to its
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
                Logger.log("heroes.json: hero '" + heroId + "' has unknown buffFitScores tier '" + tierName
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

    // --- private: writing (see #save) ---

    private static JsonArray catalogToTree(List<Hero> catalog) {
        JsonArray tree = new JsonArray();
        for (Hero hero : catalog) {
            tree.add(heroToTree(hero));
        }
        return tree;
    }

    private static JsonObject heroToTree(Hero hero) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", hero.id());
        // Omitted (rather than written as "placeholder.png") when the hero has no
        // avatar of its own - mirrors parseHeroObject's own null/prefix handling,
        // so a hero without an "image" field keeps falling back to the
        // placeholder on the next load instead of pinning it explicitly.
        if (!hero.imagePath().equals(Hero.PLACEHOLDER_IMAGE_PATH)) {
            obj.addProperty("image", stripImagePrefix(hero.imagePath()));
        }
        JsonArray roles = new JsonArray();
        for (Role role : hero.roles()) {
            roles.add(role.name());
        }
        obj.add("roles", roles);
        if (hero.generalScore() != ScoreTier.STANDARD) {
            obj.addProperty("generalScore", hero.generalScore().name());
        }
        JsonObject buffFitScores = buffFitScoresToTree(hero.buffFitScores());
        if (buffFitScores.size() > 0) {
            obj.add("buffFitScores", buffFitScores);
        }
        return obj;
    }

    private static String stripImagePrefix(String imagePath) {
        return imagePath.startsWith(IMAGE_PATH_PREFIX) ? imagePath.substring(IMAGE_PATH_PREFIX.length()) : imagePath;
    }

    /**
     * Builds the "buffFitScores" JSON object for one hero, omitting any
     * entry whose tier is {@link ScoreTier#STANDARD} - the same "sparse
     * catalog, STANDARD is the unwritten default" convention already used
     * for {@link Hero#generalScore()} (see its Javadoc): a missing entry
     * already resolves to STANDARD (when the hero's role matches the
     * fortification's buff) via {@link Hero#buffFitScore}, so persisting it
     * explicitly would only add dead weight to heroes.json. This is the
     * single place that enforces the rule, so a caller (e.g.
     * {@code HeroBuffFitScoresDialog}) can hand {@link #save} a
     * {@link Hero#buffFitScores()} map with STANDARD entries in it (e.g. one
     * left over from before this rule existed) without needing its own
     * filtering - {@link #save} always drops them on the way out.
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
