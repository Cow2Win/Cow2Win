package org.c2w.data.repository;

import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
            String json = loadJsonResource(JSON_PATH);
            return parseTitansJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load titan catalog from " + JSON_PATH, e);
        }
    }

    private static String loadJsonResource(String resourcePath) throws IOException {
        try (var is = TitanRepository.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Titan> parseTitansJson(String json) {
        Map<String, Titan> result = new LinkedHashMap<>();

        // Simple JSON parser (no external dependencies)
        // Expects: [{ "id": "...", "element": "...", "image": "..." }, ...]
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Titans JSON must be an array");
        }

        String content = json.substring(1, json.length() - 1);
        List<String> objects = splitJsonObjects(content);

        for (String obj : objects) {
            Titan titan = parseTitanObject(obj.trim());
            if (titan != null) {
                result.put(titan.id(), titan);
            }
        }

        return result;
    }

    private static List<String> splitJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceCount = 0;
        boolean inString = false;
        boolean escaped = false;

        for (char c : content.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                current.append(c);
                escaped = true;
                continue;
            }

            if (c == '"' && !escaped) {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
            }

            current.append(c);

            if (!inString && braceCount == 0 && current.toString().trim().endsWith("}")) {
                String obj = current.toString().trim();
                // Remove leading comma (separator from the previous object)
                if (obj.startsWith(",")) {
                    obj = obj.substring(1).trim();
                }
                if (!obj.isEmpty() && !obj.equals(",")) {
                    if (obj.endsWith(",")) {
                        obj = obj.substring(0, obj.length() - 1);
                    }
                    objects.add(obj);
                    current = new StringBuilder();
                }
            }
        }

        return objects;
    }

    private static Titan parseTitanObject(String objJson) {
        if (!objJson.startsWith("{") || !objJson.endsWith("}")) {
            return null;
        }

        String id = extractJsonString(objJson, "id");
        String elementStr = extractJsonString(objJson, "element");
        String image = extractJsonString(objJson, "image");

        if (id == null || id.isBlank() || elementStr == null || elementStr.isBlank()) {
            return null;
        }

        TitanElement element;
        try {
            element = TitanElement.valueOf(elementStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new Titan(id, element, image != null ? "/images/titans/" + image : null);
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
