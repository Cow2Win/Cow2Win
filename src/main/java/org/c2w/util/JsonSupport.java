package org.c2w.util;

import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // --- resolving a writable data file (relative paths only) ---

    /**
     * Resolves the on-disk location of a writable catalog/demo data file - used where a real
     * {@link Path} is needed (via {@link Files}/{@link #writeJsonFile}), not just a classpath
     * resource: {@code HeroRepository}/{@code FortificationRepository}'s {@code save(...)}
     * (the catalog-editing dialogs, e.g. {@code HeroCoreScoreDialog}) and {@code C2WApp}'s
     * first-run demo guild/lineup.
     *
     * <p>Always a path relative to the current working directory, never absolute or tied to
     * one specific layout, so the same code works both:
     * <ul>
     *     <li>run from the IDE/source checkout - working directory is the project root, so
     *         there is no top-level {@code resources/} folder and this falls back to the
     *         source tree's {@code src/main/resources/...}; and</li>
     *     <li>run from the packaged Windows app-image (see {@code pom.xml}'s jpackage
     *         execution) - working directory is the app's own install folder, which has a
     *         plain, on-disk {@code resources/...} folder shipped right there via jpackage's
     *         {@code appContentPaths} (a copy of {@code src/main/resources}), and that
     *         packaged layout is preferred over the source-tree fallback whenever it's
     *         present.</li>
     * </ul>
     *
     * <p>Note this only decides WHERE to read/write the file - it does not make edits saved
     * this way visible to the normal catalog loading in {@link #readClasspathResource}, which
     * always reads the classpath copy embedded in the jar. Callers that already hold the
     * updated data in memory (as both current {@code save(...)} implementations do) are
     * unaffected by that; it would only matter for picking up such an edit after restarting
     * the packaged app without rebuilding it.
     *
     * @param relativeParts path segments under the data folder, e.g. {@code "data",
     *         "heroes.json"} - joined the same way as {@link Paths#get(String, String...)}.
     */
    public static Path resolveDataFile(String... relativeParts) {
        Path packaged = resolveUnder(Paths.get("resources"), relativeParts);
        if (Files.exists(packaged)) {
            return packaged;
        }
        return resolveUnder(Paths.get("src", "main", "resources"), relativeParts);
    }

    private static Path resolveUnder(Path base, String... parts) {
        Path path = base;
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path;
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

    /**
     * Reads a flat object-valued property as a String-to-String map (e.g.
     * {@code "buffFitScores": {"bastion": "ELEVATED", "city-hall": "LOW"}}) -
     * insertion order preserved, missing/null property yields an empty map.
     * Non-string values are read via {@code getAsString()} (fine for the
     * enum-name-valued maps this is currently used for).
     */
    public static Map<String, String> getStringMap(JsonObject obj, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonObject sub = getObject(obj, key);
        if (sub != null) {
            for (var entry : sub.entrySet()) {
                if (!entry.getValue().isJsonNull()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
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
            Logger.log("Invalid date for '" + key + "', ignoring: " + value);
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
