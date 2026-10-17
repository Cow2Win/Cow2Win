package org.c2w.datatool.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.c2w.infra.JsonSupport;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The display names of all catalog entries, {@code catalog/names.json}: one object per catalog
 * id (sorted by id), holding the name per language folder name (sorted too) -
 * {@code "adam": {"deutsch": "Adam", "english": "Adam", "francais": "Adam"}}, one line per id.
 * Languages the tool does not know (see {@link Language}) are kept as they are.
 */
public final class NamesFile {

    /** Catalog id to language folder name to name. */
    private final Map<String, Map<String, String>> names = new TreeMap<>();
    private final String fileText;

    public NamesFile(String text) {
        this.fileText = text;
        for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
            Map<String, String> byLanguage = new TreeMap<>();
            for (Map.Entry<String, JsonElement> name : entry.getValue().getAsJsonObject().entrySet()) {
                if (!name.getValue().isJsonNull()) {
                    byLanguage.put(name.getKey(), name.getValue().getAsString());
                }
            }
            names.put(entry.getKey(), byLanguage);
        }
    }

    /** An empty file (no ids). */
    public static NamesFile empty() {
        return new NamesFile("{}");
    }

    /** The name of {@code id} in {@code language}, or null. */
    public String get(String id, Language language) {
        Map<String, String> byLanguage = names.get(id);
        return byLanguage == null ? null : byLanguage.get(language.folder());
    }

    public boolean containsId(String id) {
        return names.containsKey(id);
    }

    public Set<String> ids() {
        return names.keySet();
    }

    /** Sets (or, with a null or blank name, removes) the name of {@code id} in {@code language}. */
    public void set(String id, Language language, String name) {
        if (name == null || name.isBlank()) {
            Map<String, String> byLanguage = names.get(id);
            if (byLanguage != null) {
                byLanguage.remove(language.folder());
            }
        } else {
            names.computeIfAbsent(id, key -> new TreeMap<>()).put(language.folder(), name);
        }
    }

    /** Removes {@code id} with the names of all languages. */
    public void remove(String id) {
        names.remove(id);
    }

    /** True if {@link #text()} differs from the text this file was read from (line endings aside). */
    public boolean isModified() {
        return !text().equals(fileText.replace("\r\n", "\n"));
    }

    /** The file content: one line per id, LF, trailing newline. */
    public String text() {
        if (names.isEmpty()) {
            return "{}\n";
        }
        StringBuilder out = new StringBuilder("{\n");
        boolean firstId = true;
        for (Map.Entry<String, Map<String, String>> entry : names.entrySet()) {
            if (!firstId) {
                out.append(",\n");
            }
            firstId = false;
            out.append("  ").append(JsonSupport.GSON.toJson(entry.getKey())).append(": {");
            boolean firstName = true;
            for (Map.Entry<String, String> name : entry.getValue().entrySet()) {
                if (!firstName) {
                    out.append(", ");
                }
                firstName = false;
                out.append(JsonSupport.GSON.toJson(name.getKey())).append(": ")
                        .append(JsonSupport.GSON.toJson(name.getValue()));
            }
            out.append('}');
        }
        return out.append("\n}\n").toString();
    }
}
