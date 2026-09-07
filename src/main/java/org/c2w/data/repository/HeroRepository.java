package org.c2w.data.repository;

import org.c2w.data.model.Hero;
import org.c2w.data.model.Role;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


public class HeroRepository {
    private static final String JSON_PATH = "/data/heroes.json";
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
            String json = loadJsonResource(JSON_PATH);
            return parseHeroesJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hero catalog from " + JSON_PATH, e);
        }
    }

    private static String loadJsonResource(String resourcePath) throws IOException {
        try (var is = HeroRepository.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Hero> parseHeroesJson(String json) {
        Map<String, Hero> result = new LinkedHashMap<>();

        // Simple JSON parser (no external dependencies)
        // Expects: [{ "id": "...", "image": "...", "roles": [...] }, ...]
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Heroes JSON must be an array");
        }

        String content = json.substring(1, json.length() - 1);
        List<String> objects = splitJsonObjects(content);

        for (String obj : objects) {
            Hero hero = parseHeroObject(obj.trim());
            if (hero != null) {
                result.put(hero.id(), hero);
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

    private static Hero parseHeroObject(String objJson) {
        if (!objJson.startsWith("{") || !objJson.endsWith("}")) {
            return null;
        }

        String id = extractJsonString(objJson, "id");
        String image = extractJsonString(objJson, "image");
        List<Role> roles = extractJsonRoles(objJson, "roles");

        if (id == null || id.isBlank() || roles == null || roles.isEmpty()) {
            return null;
        }

        return new Hero(id, roles, image != null ? "/images/heroes/" + image : null);
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static List<Role> extractJsonRoles(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) {
            return null;
        }

        String rolesStr = matcher.group(1);
        if (rolesStr == null || rolesStr.isBlank()) {
            return List.of();
        }

        List<Role> roles = new ArrayList<>();
        String[] roleNames = rolesStr.split(",");
        for (String roleName : roleNames) {
            roleName = roleName.trim().replaceAll("\"", "").trim();
            try {
                roles.add(Role.valueOf(roleName));
            } catch (IllegalArgumentException e) {
                // Ignore (or log) unknown role
            }
        }
        return roles;
    }
}
