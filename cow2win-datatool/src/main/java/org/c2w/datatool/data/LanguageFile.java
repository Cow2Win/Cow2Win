package org.c2w.datatool.data;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Line-based editor for one language file. Only the lines of the keys that are changed,
 * added or removed are touched - comments, order, blank lines, all UI texts and their
 * escaping stay byte for byte as they are (which {@link Properties#store} would not keep).
 * Values are read the way the app reads them ({@link Properties} over a reader) and written
 * as UTF-8 text, escaping only what the properties format requires.
 */
public final class LanguageFile {

    /** Position of one key: its first and last physical line (continuation lines included). */
    private record Entry(int firstLine, int lastLine, int valueStart) {
    }

    private final String originalText;
    private final List<String> lines;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    public LanguageFile(String text) {
        this.originalText = text;
        this.lines = new ArrayList<>(List.of(text.split("\n", -1)));
        index();
    }

    public String text() {
        return String.join("\n", lines);
    }

    public boolean isModified() {
        return !text().equals(originalText);
    }

    public boolean containsKey(String key) {
        return entries.containsKey(key);
    }

    public String get(String key) {
        return values.get(key);
    }

    public Set<String> keys() {
        return values.keySet();
    }

    /** Replaces the value of an existing key - only that line changes. */
    public void set(String key, String value) {
        Entry entry = entries.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown key: " + key);
        }
        String first = lines.get(entry.firstLine());
        String lineEnd = lines.get(entry.lastLine()).endsWith("\r") ? "\r" : "";
        String newLine = first.substring(0, entry.valueStart()) + escape(value) + lineEnd;
        replace(entry, List.of(newLine));
    }

    /**
     * Adds a key at the end of its section: right after the last line of any key of {@code
     * sectionKeys} (the ids of the same catalog). If the file has none of them yet, right after
     * the section's header comment (one of {@code headers}, compared without {@code #} and
     * case-insensitively), else at the start ({@code atStart}) or the end of the file.
     */
    public void add(String key, String value, Collection<String> sectionKeys, Collection<String> headers,
                    boolean atStart) {
        if (entries.containsKey(key)) {
            throw new IllegalArgumentException("Key exists already: " + key);
        }
        String newLine = key + "=" + escape(value);
        int insertAt = -1;
        for (String sectionKey : sectionKeys) {
            Entry entry = entries.get(sectionKey);
            if (entry != null) {
                insertAt = Math.max(insertAt, entry.lastLine() + 1);
            }
        }
        if (insertAt < 0) {
            insertAt = headerLine(headers) + 1;
        }
        if (insertAt <= 0) {
            insertAt = atStart ? 0 : endOfContent();
        }
        lines.add(insertAt, newLine);
        index();
    }

    public void remove(String key) {
        Entry entry = entries.get(key);
        if (entry != null) {
            replace(entry, List.of());
        }
    }

    private int headerLine(Collection<String> headers) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.startsWith("#")) {
                String title = line.replaceFirst("^#+", "").strip();
                for (String header : headers) {
                    if (title.equalsIgnoreCase(header)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /** Index after the last non-empty line, so a final line break stays the last thing in the file. */
    private int endOfContent() {
        int end = lines.size();
        while (end > 0 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return end;
    }

    private void replace(Entry entry, List<String> replacement) {
        for (int i = entry.lastLine(); i >= entry.firstLine(); i--) {
            lines.remove(i);
        }
        lines.addAll(entry.firstLine(), replacement);
        index();
    }

    // --- parsing ---

    private void index() {
        entries.clear();
        values.clear();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            int first = i;
            int last = i;
            while (last < lines.size() - 1 && continues(lines.get(last))) {
                last++;
            }
            i = last + 1;
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int keyStart = line.length() - trimmed.length();
            int keyEnd = keyEnd(line, keyStart);
            String key = unescapeKey(line.substring(keyStart, keyEnd));
            entries.put(key, new Entry(first, last, valueStart(line, keyEnd)));
            values.put(key, decode(String.join("\n", lines.subList(first, last + 1)), key));
        }
    }

    private static int keyEnd(String line, int from) {
        int i = from;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                break;
            }
            i++;
        }
        return Math.min(i, line.length());
    }

    private static int valueStart(String line, int keyEnd) {
        int i = keyEnd;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (i < line.length() && (line.charAt(i) == '=' || line.charAt(i) == ':')) {
            i++;
            while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                i++;
            }
        }
        return i;
    }

    /** A line continues on the next one if it ends with an odd number of backslashes. */
    private static boolean continues(String line) {
        String content = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        int backslashes = 0;
        for (int i = content.length() - 1; i >= 0 && content.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static String unescapeKey(String rawKey) {
        return rawKey.indexOf('\\') < 0 ? rawKey : decodeKey(rawKey);
    }

    private static String decodeKey(String rawKey) {
        Properties properties = load(rawKey + "=");
        return properties.stringPropertyNames().iterator().next();
    }

    private static String decode(String logicalLine, String key) {
        return load(logicalLine).getProperty(key, "");
    }

    private static Properties load(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /** Escapes a value the way the files are written today: UTF-8 as is, no {@code \}u escapes. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case ' ' -> out.append(i == 0 ? "\\ " : " ");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
