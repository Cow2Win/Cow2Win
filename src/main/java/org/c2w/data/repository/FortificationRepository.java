package org.c2w.data.repository;

import org.c2w.data.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class FortificationRepository {
    private static final String JSON_PATH = "/data/fortifications.json";


    private static final Path CATALOG_FILE_PATH = Paths.get("src", "main", "resources", "data", "fortifications.json");

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

    public static synchronized void save(List<Fortification> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        if (CATALOG_FILE_PATH.getParent() != null) {
            Files.createDirectories(CATALOG_FILE_PATH.getParent());
        }

        StringBuilder sb = new StringBuilder();
        writeValue(sb, catalogToTree(catalog), 0);
        sb.append("\n");

        try (OutputStream out = Files.newOutputStream(CATALOG_FILE_PATH)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        Map<String, Fortification> updated = new LinkedHashMap<>();
        for (Fortification f : catalog) {
            updated.put(f.id(), f);
        }
        fortificationsByid = updated;
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
            String json = loadJsonResource(JSON_PATH);
            return parseFortificationsJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fortification catalog from " + JSON_PATH, e);
        }
    }

    private static String loadJsonResource(String resourcePath) throws IOException {
        try (var is = FortificationRepository.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Fortification> parseFortificationsJson(String json) {
        Map<String, Fortification> result = new LinkedHashMap<>();

        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Fortifications JSON must be an array");
        }

        String content = json.substring(1, json.length() - 1);
        List<String> objects = splitJsonObjects(content);

        for (String obj : objects) {
            Fortification fort = parseFortificationObject(obj.trim());
            if (fort != null) {
                result.put(fort.id(), fort);
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

    private static Fortification parseFortificationObject(String objJson) {
        if (!objJson.startsWith("{") || !objJson.endsWith("}")) {
            return null;
        }

        String id = extractJsonString(objJson, "id");
        String typeStr = extractJsonString(objJson, "type");
        Integer capacity = extractJsonNumber(objJson, "capacity");
        Integer captureBonus = extractJsonNumber(objJson, "captureBonus");
        Integer row = extractJsonNumber(objJson, "row");
        Integer column = extractJsonNumber(objJson, "column");
        Integer importance = extractJsonNumber(objJson, "strategicImportance");
        List<String> prerequisites = extractJsonStringArray(objJson, "prerequisites");
        Buff buff = parseBuff(extractJsonObject(objJson, "buff"));

        if (id == null || typeStr == null || capacity == null || captureBonus == null || 
            row == null || column == null || importance == null) {
            return null;
        }

        FortificationType type;
        try {
            type = FortificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
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
                prerequisites != null ? prerequisites : List.of(),
                importance
        );
    }

    private static Buff parseBuff(String buffJson) {
        if (buffJson == null) {
            return null;
        }
        try {
            String kind = extractJsonString(buffJson, "kind");
            String display = extractJsonString(buffJson, "display");
            BuffEffect effect = BuffEffect.valueOf(extractJsonString(buffJson, "effect"));
            Double bonusPercent = extractJsonDouble(buffJson, "bonusPercent");
            List<String> buffProfits = extractJsonStringArray(buffJson, "buffProfits");
            buffProfits = buffProfits == null ? List.of() : buffProfits;
            String resolvedDisplay = display == null ? "" : display;

            if ("ROLE".equals(kind)) {
                Role role = Role.valueOf(extractJsonString(buffJson, "role"));
                return new RoleBuff(role, effect, bonusPercent, buffProfits, resolvedDisplay);
            }
            if ("ELEMENT".equals(kind)) {
                TitanElement element = TitanElement.valueOf(extractJsonString(buffJson, "element"));
                return new ElementBuff(element, effect, bonusPercent, buffProfits, resolvedDisplay);
            }
            return null;
        } catch (RuntimeException e) {
            System.err.println("Could not parse fortification buff, skipping it: " + e.getMessage());
            return null;
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static Integer extractJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private static Double extractJsonDouble(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) {
            return null;
        }

        String arrayStr = matcher.group(1);
        if (arrayStr == null || arrayStr.isBlank()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        String[] values = arrayStr.split(",");
        for (String value : values) {
            value = value.trim().replaceAll("\"", "").trim();
            if (!value.isEmpty()) {
                items.add(value);
            }
        }
        return items;
    }

    private static String extractJsonObject(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex < 0) {
            return null;
        }
        int i = colonIndex + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '{') {
            return null;
        }

        int start = i;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    // --- private: writing (see #save) ---

    private static Object catalogToTree(List<Fortification> catalog) {
        List<Object> tree = new ArrayList<>();
        for (Fortification f : catalog) {
            tree.add(fortificationToTree(f));
        }
        return tree;
    }

    private static Object fortificationToTree(Fortification f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", f.id());
        map.put("type", f.type().name());
        map.put("capacity", f.capacity());
        map.put("captureBonus", f.captureBonus());
        map.put("row", f.row());
        map.put("column", f.column());
        map.put("buff", buffToTree(f.buff()));
        map.put("prerequisites", new ArrayList<Object>(f.prerequisites()));
        map.put("strategicImportance", f.strategicImportance());
        return map;
    }

    private static Object buffToTree(Buff buff) {
        if (buff == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (buff instanceof RoleBuff roleBuff) {
            map.put("kind", "ROLE");
            map.put("display", roleBuff.display());
            map.put("effect", roleBuff.effect().name());
            map.put("bonusPercent", roleBuff.bonusPercent());
            map.put("buffProfits", new ArrayList<Object>(roleBuff.buffProfits()));
            map.put("role", roleBuff.role().name());
        } else if (buff instanceof ElementBuff elementBuff) {
            map.put("kind", "ELEMENT");
            map.put("display", elementBuff.display());
            map.put("effect", elementBuff.effect().name());
            map.put("bonusPercent", elementBuff.bonusPercent());
            map.put("buffProfits", new ArrayList<Object>(elementBuff.buffProfits()));
            map.put("element", elementBuff.element().name());
        }
        return map;
    }

    private static void writeValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b.toString());
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append(n.longValue());
            } else {
                sb.append(n);
            }
        } else if (value instanceof Map<?, ?> map) {
            writeObject(sb, map, indent);
        } else if (value instanceof List<?> list) {
            writeArray(sb, list, indent);
        } else {
            throw new IllegalArgumentException("Cannot serialize value of type " + value.getClass());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        String childIndent = "  ".repeat(indent + 1);
        int i = 0;
        int total = map.size();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append(childIndent);
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(": ");
            writeValue(sb, entry.getValue(), indent + 1);
            i++;
            if (i < total) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ".repeat(indent)).append("}");
    }

    private static void writeArray(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        String childIndent = "  ".repeat(indent + 1);
        for (int i = 0; i < list.size(); i++) {
            sb.append(childIndent);
            writeValue(sb, list.get(i), indent + 1);
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ".repeat(indent)).append("]");
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
