package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TitanRepository {
    private static final String JSON_PATH = "/data/titans.json";
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
     * Resets the catalog (for tests). The JSON file is reloaded on the next
     * access.
     */
    public static void resetCache() {
        synchronized (TitanRepository.class) {
            titansById = null;
        }
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
            String json = JsonSupport.readClasspathResource(TitanRepository.class, JSON_PATH);
            return parseTitansJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load titan catalog from " + JSON_PATH, e);
        }
    }

    private static Map<String, Titan> parseTitansJson(String json) {
        Map<String, Titan> result = new LinkedHashMap<>();

        // Expects: [{ "id": "...", "element": "...", "image": "..." }, ...]
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

        return new Titan(id, element, image != null ? "/images/titans/" + image : null);
    }
}
