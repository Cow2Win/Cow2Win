package org.c2w.data.repository;

import org.c2w.data.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GuildRepository {

    private GuildRepository() {
    }

    /**
     * Loads the guild stored at the given path.
     *
     * @throws IOException if the file cannot be read, is not valid JSON, or
     *                      does not describe a valid {@link Guild} (e.g.
     *                      missing id)
     */
    public static Guild load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);

        Object root;
        try {
            root = new JsonParser(json).parseRoot();
        } catch (RuntimeException e) {
            throw new IOException("Malformed guild JSON in " + path + ": " + e.getMessage(), e);
        }
        if (!(root instanceof Map)) {
            throw new IOException("Guild file " + path + " does not contain a JSON object at the top level");
        }

        try {
            return guildFromJson(asMap(root));
        } catch (RuntimeException e) {
            throw new IOException("Could not interpret guild JSON in " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Writes the given guild to the given path as pretty-printed JSON,
     * creating parent directories as needed.
     */
    public static void save(Guild guild, Path path) throws IOException {
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        StringBuilder sb = new StringBuilder();
        writeValue(sb, guildToTree(guild), 0);
        sb.append("\n");

        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---- Guild <-> generic JSON tree (Map/List/String/Number/Boolean/null) ----

    private static Guild guildFromJson(Map<String, Object> obj) {
        String id = getString(obj, "id", "");
        String name = getString(obj, "name", "");
        int season = getInt(obj, "season", 0);
        LocalDate seasonStart = getDate(obj, "seasonStart");

        List<GuildMember> members = new ArrayList<>();
        for (Object memberObj : getList(obj, "members")) {
            members.add(memberFromJson(asMap(memberObj)));
        }

        return new Guild(id, name, members, season, seasonStart);
    }

    private static Object guildToTree(Guild guild) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", guild.id());
        map.put("name", guild.name());
        map.put("season", guild.season());
        map.put("seasonStart", guild.seasonStart() == null ? null : guild.seasonStart().toString());

        List<Object> members = new ArrayList<>();
        for (GuildMember member : guild.members()) {
            members.add(memberToTree(member));
        }
        map.put("members", members);

        return map;
    }

    private static GuildMember memberFromJson(Map<String, Object> obj) {
        String id = getString(obj, "id", "");
        String name = getString(obj, "name", "");

        List<HeroTeam> heroTeams = new ArrayList<>();
        for (Object teamObj : getList(obj, "heroTeams")) {
            heroTeams.add(heroTeamFromJson(asMap(teamObj), id));
        }

        List<TitanTeam> titanTeams = new ArrayList<>();
        for (Object teamObj : getList(obj, "titanTeams")) {
            titanTeams.add(titanTeamFromJson(asMap(teamObj), id));
        }

        return new GuildMember(id, name, heroTeams, titanTeams);
    }

    private static Object memberToTree(GuildMember member) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", member.id());
        map.put("name", member.name());

        List<Object> heroTeams = new ArrayList<>();
        for (HeroTeam team : member.heroTeams()) {
            heroTeams.add(heroTeamToTree(team));
        }
        map.put("heroTeams", heroTeams);

        List<Object> titanTeams = new ArrayList<>();
        for (TitanTeam team : member.titanTeams()) {
            titanTeams.add(titanTeamToTree(team));
        }
        map.put("titanTeams", titanTeams);

        return map;
    }

    private static HeroTeam heroTeamFromJson(Map<String, Object> obj, String memberId) {
        List<Hero> heroes = new ArrayList<>();
        for (Object idObj : getList(obj, "heroIds")) {
            if (idObj == null) {
                continue;
            }
            String heroId = String.valueOf(idObj);
            HeroRepository.findById(heroId).ifPresentOrElse(heroes::add,
                    () -> System.err.println("Unknown hero id in guild file, skipping: " + heroId));
        }

        int totalPower = getInt(obj, "totalPower", 0);
        LocalDate lastModified = getDate(obj, "lastModified");

        return new HeroTeam(memberId, heroes, totalPower, lastModified);
    }

    private static Object heroTeamToTree(HeroTeam team) {
        Map<String, Object> map = new LinkedHashMap<>();

        List<Object> heroIds = new ArrayList<>();
        for (Hero hero : team.heroes()) {
            heroIds.add(hero.id());
        }
        map.put("heroIds", heroIds);

        map.put("totalPower", team.totalPower());
        map.put("lastModified", team.lastModified() == null ? null : team.lastModified().toString());

        return map;
    }

    private static TitanTeam titanTeamFromJson(Map<String, Object> obj, String memberId) {
        List<Titan> titans = new ArrayList<>();
        for (Object idObj : getList(obj, "titanIds")) {
            if (idObj == null) {
                continue;
            }
            String titanId = String.valueOf(idObj);
            TitanRepository.findById(titanId).ifPresentOrElse(titans::add,
                    () -> System.err.println("Unknown titan id in guild file, skipping: " + titanId));
        }

        int totalPower = getInt(obj, "totalPower", 0);
        LocalDate lastModified = getDate(obj, "lastModified");

        return new TitanTeam(memberId, titans, totalPower, lastModified);
    }

    private static Object titanTeamToTree(TitanTeam team) {
        Map<String, Object> map = new LinkedHashMap<>();

        List<Object> titanIds = new ArrayList<>();
        for (Titan titan : team.titans()) {
            titanIds.add(titan.id());
        }
        map.put("titanIds", titanIds);

        map.put("totalPower", team.totalPower());
        map.put("lastModified", team.lastModified() == null ? null : team.lastModified().toString());

        return map;
    }

    // ---- small typed accessors on top of the generic JSON tree ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object, found: " + value);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Expected '" + key + "' to be a JSON array, found: " + value);
        }
        return (List<Object>) value;
    }

    private static String getString(Map<String, Object> obj, String key, String defaultValue) {
        Object value = obj.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String getStringOrNull(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int getInt(Map<String, Object> obj, String key, int defaultValue) {
        Object value = obj.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static LocalDate getDate(Map<String, Object> obj, String key) {
        String value = getStringOrNull(obj, key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date for '" + key + "' in guild file, ignoring: " + value);
            return null;
        }
    }

    // ---- minimal hand-rolled JSON parser (no external dependencies) ----

    private static final class JsonParser {
        private final String input;
        private int pos;

        JsonParser(String input) {
            this.input = input;
            this.pos = 0;
        }

        Object parseRoot() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = input.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' in JSON object at position " + pos);
                }
            }
            return result;
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                Object value = parseValue();
                result.add(value);
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' in JSON array at position " + pos);
                }
            }
            return result;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= input.length()) {
                    throw new IllegalArgumentException("Unterminated JSON string");
                }
                char c = input.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (pos >= input.length()) {
                        throw new IllegalArgumentException("Unterminated JSON escape sequence");
                    }
                    char escaped = input.charAt(pos++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = input.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape sequence \\" + escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Double parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < input.length() && isNumberChar(input.charAt(pos))) {
                pos++;
            }
            String number = input.substring(start, pos);
            if (number.isEmpty() || number.equals("-")) {
                throw new IllegalArgumentException("Invalid JSON number at position " + start);
            }
            return Double.parseDouble(number);
        }

        private boolean isNumberChar(char c) {
            return Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
        }

        private Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid JSON literal at position " + pos);
        }

        private Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid JSON literal at position " + pos);
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            return input.charAt(pos);
        }

        private char next() {
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            return input.charAt(pos++);
        }

        private void expect(char expected) {
            if (pos >= input.length() || input.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }
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
