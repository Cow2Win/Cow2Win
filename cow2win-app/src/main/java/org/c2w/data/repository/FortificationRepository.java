package org.c2w.data.repository;

import com.google.gson.*;
import org.c2w.data.model.*;
import org.c2w.eval.AbstractLineupAlgorithm;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only access to the fortification catalog ({@code fortifications.json}
 * inside the jar). All values in it are fixed: the app
 * offers no way to edit or save the catalog, it can only be changed in the
 * source tree and shipped with a new build.
 */
public class FortificationRepository {
    private static final String JSON_PATH = "/data/fortifications.json";

    private static volatile Map<String, Fortification> fortificationsByid;

    /**
     * Returns the fortification with the given id, or Optional.empty() if no
     * fortification with this id exists.
     */
    public static Optional<Fortification> findById(String fortificationId) {
        ensureLoaded();
        return Optional.ofNullable(fortificationsByid.get(fortificationId));
    }

    /**
     * Returns all known fortifications as a list sorted by id.
     */
    public static List<Fortification> findAll() {
        ensureLoaded();
        return fortificationsByid.values().stream()
                .sorted(Comparator.comparing(Fortification::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known fortifications.
     */
    public static int count() {
        ensureLoaded();
        return fortificationsByid.size();
    }

    /**
     * Resets the catalog (for tests). The JSON file is reloaded on the next
     * access.
     */
    public static void resetCache() {
        synchronized (FortificationRepository.class) {
            fortificationsByid = null;
        }
    }

    // --- private: loading ---

    private static void ensureLoaded() {
        if (fortificationsByid == null) {
            synchronized (FortificationRepository.class) {
                if (fortificationsByid == null) {
                    fortificationsByid = loadFortifications();
                }
            }
        }
    }

    private static Map<String, Fortification> loadFortifications() {
        try {
            String json = JsonSupport.readClasspathResource(FortificationRepository.class, JSON_PATH);
            Map<String, Fortification> catalog = parseFortificationsJson(json);
            validateCatalog(catalog);
            return catalog;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fortification catalog from " + JSON_PATH, e);
        }
    }

    /**
     * Sanity-checks the already-parsed catalog and logs (via {@link Logger}) anything that
     * looks like a data-entry mistake in {@code fortifications.json} rather than failing
     * silently - specifically: prerequisites referencing a fortification id that doesn't
     * exist in the catalog, and cycles in the prerequisite graph. Neither of these crashes
     * the app on its own (see {@link AbstractLineupAlgorithm#computeUnlockDepths},
     * which already degrades gracefully for both cases), so this is purely about making a
     * likely typo visible instead of it silently producing a slightly-wrong "optimal" lineup.
     *
     * <p>Package-private (not private) so {@code FortificationRepositoryValidationTest} can
     * call it directly against a synthetic catalog instead of only via {@link #loadFortifications()}.
     */
    static void validateCatalog(Map<String, Fortification> byId) {
        for (Fortification f : byId.values()) {
            for (String prerequisite : f.prerequisites()) {
                if (!byId.containsKey(prerequisite)) {
                    Logger.log("fortifications.json: fortification '" + f.id() + "' has prerequisite '"
                            + prerequisite + "' which does not exist in the catalog");
                }
            }
        }

        Set<String> visited = new HashSet<>();
        for (String id : byId.keySet()) {
            if (!visited.contains(id)) {
                detectCycle(id, byId, visited, new LinkedHashSet<>());
            }
        }
    }

    /**
     * Depth-first search over the prerequisite graph, reporting each distinct cycle exactly
     * once via {@link Logger#log}. {@code path} is the chain of ids currently being expanded
     * (in order); {@code visited} marks ids whose subtree has been fully explored already
     * (from this or an earlier root), so a cycle reachable from several different entry
     * points is only logged the first time it's found, not once per entry point.
     */
    private static void detectCycle(String id, Map<String, Fortification> byId, Set<String> visited, LinkedHashSet<String> path) {
        if (path.contains(id)) {
            List<String> cyclePath = new ArrayList<>();
            boolean insideCycle = false;
            for (String pathId : path) {
                if (pathId.equals(id)) {
                    insideCycle = true;
                }
                if (insideCycle) {
                    cyclePath.add(pathId);
                }
            }
            cyclePath.add(id);
            Logger.log("fortifications.json: cyclic prerequisites detected: " + String.join(" -> ", cyclePath));
            return;
        }
        if (visited.contains(id)) {
            return;
        }

        Fortification f = byId.get(id);
        if (f == null) {
            // Unknown reference - already reported above, nothing further to explore here.
            visited.add(id);
            return;
        }

        path.add(id);
        for (String prerequisite : f.prerequisites()) {
            detectCycle(prerequisite, byId, visited, path);
        }
        path.remove(id);
        visited.add(id);
    }

    /**
     * Package-private (not private) so {@code FortificationRepositoryValidationTest} can feed
     * it synthetic JSON directly instead of only ever exercising it via the bundled
     * {@code fortifications.json} classpath resource.
     */
    static Map<String, Fortification> parseFortificationsJson(String json) {
        Map<String, Fortification> result = new LinkedHashMap<>();

        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Fortification fort = parseFortificationObject(element.getAsJsonObject());
            if (fort != null) {
                result.put(fort.id(), fort);
            }
        }

        return result;
    }

    private static Fortification parseFortificationObject(JsonObject obj) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String typeStr = JsonSupport.getStringOrNull(obj, "type");
        Integer capacity = JsonSupport.getInteger(obj, "capacity");
        Integer captureBonus = JsonSupport.getInteger(obj, "captureBonus");
        Integer row = JsonSupport.getInteger(obj, "row");
        Integer column = JsonSupport.getInteger(obj, "column");
        Integer importance = JsonSupport.getInteger(obj, "strategicImportance");
        List<String> prerequisites = JsonSupport.getStringList(obj, "prerequisites");
        Buff buff = parseBuff(JsonSupport.getObject(obj, "buff"));

        if (id == null || typeStr == null || capacity == null || captureBonus == null ||
            row == null || column == null || importance == null) {
            Logger.log("fortifications.json: skipping invalid fortification entry (id=" + id
                    + "): missing required field(s)");
            return null;
        }

        FortificationType type;
        try {
            type = FortificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            Logger.log("fortifications.json: fortification '" + id + "' has unknown type '" + typeStr + "', skipping it");
            return null;
        }

        return new Fortification(
                id,
                type,
                capacity,
                captureBonus,
                row,
                column,
                buff,
                prerequisites,
                importance
        );
    }

    private static Buff parseBuff(JsonObject buffObj) {
        if (buffObj == null) {
            return null;
        }
        try {
            String kind = JsonSupport.getStringOrNull(buffObj, "kind");
            BuffEffect effect = BuffEffect.valueOf(JsonSupport.getStringOrNull(buffObj, "effect"));
            Double bonusPercent = JsonSupport.getDouble(buffObj, "bonusPercent");

            if ("ROLE".equals(kind)) {
                Role role = Role.valueOf(JsonSupport.getStringOrNull(buffObj, "role"));
                return new RoleBuff(role, effect, bonusPercent);
            }
            if ("ELEMENT".equals(kind)) {
                TitanElement element = TitanElement.valueOf(JsonSupport.getStringOrNull(buffObj, "element"));
                return new ElementBuff(element, effect, bonusPercent);
            }
            return null;
        } catch (RuntimeException e) {
            Logger.log("fortifications.json: could not parse fortification buff, skipping it: " + e.getMessage());
            return null;
        }
    }
}
