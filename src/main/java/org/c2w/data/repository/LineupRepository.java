package org.c2w.data.repository;

import org.c2w.data.model.Lineup;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


public class LineupRepository {
    private static final String JSON_DIRECTORY = "/data/lineups/";
    private static volatile Map<String, Lineup> lineupsById;


    public static Lineup load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        Lineup lineup = parseLineupObject(json);
        if (lineup == null) {
            throw new IOException("Could not interpret lineup JSON in " + path);
        }
        return lineup;
    }

    /**
     * Writes the given lineup to the given path as pretty-printed JSON,
     * creating parent directories as needed.
     */
    public static void save(Lineup lineup, Path path) throws IOException {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        StringBuilder sb = new StringBuilder();
        writeValue(sb, lineupToTree(lineup), 0);
        sb.append("\n");

        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Deletes the lineup file at the given path (see
     * {@code org.tdi.cow2.gui.ToolbarPanel#onRemoveLineup}, added
     * 2026-09-03).
     *
     * @throws IOException if the file does not exist or cannot be deleted
     */
    public static void delete(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        Files.delete(path);
    }

    /**
     * Returns the lineup with the given id, or Optional.empty() if no
     * lineup with this id exists.
     */
    public static Optional<Lineup> findById(String lineupId) {
        ensureLoaded();
        return Optional.ofNullable(lineupsById.get(lineupId));
    }

    /**
     * Returns all known lineups as a list sorted by creation time (newest first).
     */
    public static List<Lineup> findAll() {
        ensureLoaded();
        return lineupsById.values().stream()
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known lineups.
     */
    public static int count() {
        ensureLoaded();
        return lineupsById.size();
    }

    /**
     * Creates an empty dummy lineup for initial use when no lineups exist yet.
     * The lineup has no entries (empty team assignments) and serves as a
     * starting point for the application.
     *
     * @return An empty Lineup with default values
     */
    public static Lineup createEmptyLineup() {
        return new Lineup(
                "default",
                "Default Guild",
                "Empty",
                LocalDateTime.now(),
                List.of()
        );
    }

    /**
     * Resets the catalog (for tests). The JSON files are reloaded on the next access.
     */
    public static void resetCache() {
        synchronized (LineupRepository.class) {
            lineupsById = null;
        }
    }

    // --- private ---

    private static void ensureLoaded() {
        if (lineupsById == null) {
            synchronized (LineupRepository.class) {
                if (lineupsById == null) {
                    lineupsById = loadLineups();
                }
            }
        }
    }

    /**
     * Loads all lineups from the JSON directory.
     * If the directory does not exist or is empty, returns an empty map.
     */
    private static Map<String, Lineup> loadLineups() {
        Map<String, Lineup> result = new LinkedHashMap<>();
        
        try {
            // Try to load lineups from resources
            // For now, return empty map as lineups don't exist initially
            // This can be extended to load from actual JSON files later
            return result;
        } catch (Exception e) {
            // If loading fails, return empty map and let the application create empty lineups
            return result;
        }
    }

    /**
     * Parses a single lineup JSON object.
     *
     * @param objJson The JSON string representing a lineup
     * @return The parsed Lineup, or null if parsing failed
     */
    private static Lineup parseLineupObject(String objJson) {
        if (!objJson.startsWith("{") || !objJson.endsWith("}")) {
            return null;
        }

        String guildId = extractJsonString(objJson, "guildId");
        String guildName = extractJsonString(objJson, "guildName");
        String algorithmName = extractJsonString(objJson, "algorithmName");
        String createdAtStr = extractJsonString(objJson, "createdAt");

        if (guildId == null || guildName == null || algorithmName == null || createdAtStr == null) {
            return null;
        }

        LocalDateTime createdAt;
        try {
            createdAt = LocalDateTime.parse(createdAtStr);
        } catch (Exception e) {
            return null;
        }

        List<Lineup.Entry> entries = parseEntries(objJson);

        try {
            return new Lineup(guildId, guildName, algorithmName, createdAt, entries);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses the entries array from a lineup JSON object.
     */
    private static List<Lineup.Entry> parseEntries(String lineupJson) {
        List<Lineup.Entry> entries = new ArrayList<>();
        String pattern = "\"entries\"\\s*:\\s*\\[([^\\]]*)\\]";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(lineupJson);
        
        if (!matcher.find()) {
            return entries;
        }

        String entriesStr = matcher.group(1);
        if (entriesStr == null || entriesStr.isBlank()) {
            return entries;
        }

        List<String> entryObjects = splitJsonObjects(entriesStr);
        for (String entryJson : entryObjects) {
            Lineup.Entry entry = parseEntryObject(entryJson.trim());
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }

    /**
     * Parses a single entry JSON object.
     */
    private static Lineup.Entry parseEntryObject(String objJson) {
        if (!objJson.startsWith("{") || !objJson.endsWith("}")) {
            return null;
        }

        String fortificationId = extractJsonString(objJson, "fortificationId");
        String teamMemberId = extractJsonString(objJson, "teamMemberId");
        String teamTypeStr = extractJsonString(objJson, "teamType");
        Integer teamIndex = extractJsonNumber(objJson, "teamIndex");
        Integer totalPower = extractJsonNumber(objJson, "totalPower");
        Integer buffFitScore = extractJsonNumber(objJson, "buffFitScore");
        Double weightedScore = extractJsonDouble(objJson, "weightedScore");

        if (fortificationId == null || teamMemberId == null || teamTypeStr == null ||
            teamIndex == null || totalPower == null || buffFitScore == null || weightedScore == null) {
            return null;
        }

        Lineup.TeamType teamType;
        try {
            teamType = Lineup.TeamType.valueOf(teamTypeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new Lineup.Entry(
                fortificationId,
                teamMemberId,
                teamType,
                teamIndex,
                totalPower,
                buffFitScore,
                weightedScore
        );
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
        String pattern = "\"" + key + "\"\\s*:\\s*([\\d.]+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
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

    // ---- Lineup <-> generic JSON tree (Map/List/String/Number/Boolean/null) ----

    private static Object lineupToTree(Lineup lineup) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guildId", lineup.guildId());
        map.put("guildName", lineup.guildName());
        map.put("algorithmName", lineup.algorithmName());
        map.put("createdAt", lineup.createdAt().toString());

        List<Object> entries = new ArrayList<>();
        for (Lineup.Entry entry : lineup.entries()) {
            entries.add(entryToTree(entry));
        }
        map.put("entries", entries);

        return map;
    }

    private static Object entryToTree(Lineup.Entry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fortificationId", entry.fortificationId());
        map.put("teamMemberId", entry.teamMemberId());
        map.put("teamType", entry.teamType().name());
        map.put("teamIndex", entry.teamIndex());
        map.put("totalPower", entry.totalPower());
        map.put("buffFitScore", entry.buffFitScore());
        map.put("weightedScore", entry.weightedScore());
        return map;
    }

    // ---- minimal hand-rolled JSON writer (pretty-printed, 2-space indent) ----

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
