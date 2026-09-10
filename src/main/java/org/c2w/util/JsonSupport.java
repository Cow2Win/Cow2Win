package org.c2w.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared Gson setup, plus small helpers for reading typed values out of a
 * generic {@link JsonObject}/{@link JsonArray} tree and for writing one back
 * out. Centralized here (added 2026-09-10, replacing gson for the
 * hand-rolled, dependency-free JSON parsers/writers every repository used to
 * carry its own copy of) so every repository under
 * {@code org.c2w.data.repository} reads/writes JSON the same way.
 */
public final class JsonSupport {

    /**
     * Pretty-printed (2-space indent), not HTML-escaped - matches the output
     * style of the previous hand-written writers, so existing data files
     * don't churn on the next save beyond the actual content change.
     */
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private JsonSupport() {
    }

    // --- reading a classpath resource ---

    public static String readClasspathResource(Class<?> anchor, String resourcePath) throws IOException {
        try (InputStream is = anchor.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // --- reading typed values out of a JsonObject ---

    public static String getString(JsonObject obj, String key, String defaultValue) {
        JsonElement e = obj.get(key);
        return (e == null || e.isJsonNull()) ? defaultValue : e.getAsString();
    }

    public static String getStringOrNull(JsonObject obj, String key) {
        return getString(obj, key, null);
    }

    public static Integer getInteger(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsInt();
    }

    public static int getInt(JsonObject obj, String key, int defaultValue) {
        Integer value = getInteger(obj, key);
        return value == null ? defaultValue : value;
    }

    public static Double getDouble(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsDouble();
    }

    public static JsonObject getObject(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsJsonObject();
    }

    public static JsonArray getArray(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e == null || e.isJsonNull()) ? new JsonArray() : e.getAsJsonArray();
    }

    public static List<String> getStringList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        for (JsonElement e : getArray(obj, key)) {
            if (!e.isJsonNull()) {
                result.add(e.getAsString());
            }
        }
        return result;
    }

    public static LocalDate getLocalDate(JsonObject obj, String key) {
        String value = getStringOrNull(obj, key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date for '" + key + "', ignoring: " + value);
            return null;
        }
    }

    // --- writing ---

    /** Writes {@code value} as a string property, or an explicit JSON null if {@code value} is null. */
    public static void putNullable(JsonObject obj, String key, String value) {
        if (value == null) {
            obj.add(key, JsonNull.INSTANCE);
        } else {
            obj.addProperty(key, value);
        }
    }

    /**
     * Adds a numeric property, writing it without a decimal point when it is
     * a whole number (e.g. "4" instead of "4.0") - matches the style of the
     * previous hand-written writers, so values like a RoleBuff's
     * bonusPercent don't grow a spurious ".0" the next time the file is
     * saved.
     */
    public static void addNumber(JsonObject obj, String key, double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            obj.addProperty(key, (long) value);
        } else {
            obj.addProperty(key, value);
        }
    }

    public static JsonArray toStringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    /**
     * Writes a JSON tree pretty-printed to the given file (UTF-8, trailing
     * newline), creating parent directories as needed.
     */
    public static void writeJsonFile(JsonElement tree, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(tree, writer);
            writer.write("\n");
        }
    }
}
