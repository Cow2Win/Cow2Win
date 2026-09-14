package org.c2w.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Reads {@code data/catalog-version.json} - a small sidecar file that
 * records which Hero Wars: Dominion Era patch/game state
 * {@code heroes.json}, {@code titans.json} and {@code fortifications.json}
 * were last checked against.
 *
 * <p>Kept as a separate file rather than a "dataVersion" field inside those
 * three catalog files themselves, because all three currently have a JSON
 * <b>array</b> as their root (see {@code HeroRepository}/{@code
 * TitanRepository}/{@code FortificationRepository}'s {@code parse*Json}),
 * so adding a top-level key there would mean restructuring every
 * repository's parse/save round-trip - a real change with a real
 * regression risk. A sidecar file gets the same "state the data at a
 * glance" benefit for zero risk to the existing catalog format.
 *
 * <p>Read once and cached, same convention as the catalog repositories'
 * own {@code ensureLoaded()} pattern. A missing or malformed file never
 * breaks app startup - it just falls back to {@value #UNKNOWN} and logs
 * why (visible in the app's log panel via {@link Logger}).
 */
public final class CatalogVersion {

    private static final String JSON_PATH = "/data/catalog-version.json";
    private static final String UNKNOWN = "unknown";

    private static volatile String dataVersion;
    private static volatile String note;

    private CatalogVersion() {
    }

    /** The catalog data's last-checked-against version/date, e.g. "2026-09-13". */
    public static String dataVersion() {
        ensureLoaded();
        return dataVersion;
    }

    /** A free-text note accompanying the version (what "checked" means here, and how to update it). */
    public static String note() {
        ensureLoaded();
        return note;
    }

    private static void ensureLoaded() {
        if (dataVersion == null) {
            synchronized (CatalogVersion.class) {
                if (dataVersion == null) {
                    load();
                }
            }
        }
    }

    private static void load() {
        try {
            String json = JsonSupport.readClasspathResource(CatalogVersion.class, JSON_PATH);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            dataVersion = JsonSupport.getString(obj, "dataVersion", UNKNOWN);
            note = JsonSupport.getString(obj, "note", "");
        } catch (IOException | RuntimeException e) {
            Logger.log("catalog-version.json: could not read data version (" + e.getMessage() + ")");
            dataVersion = UNKNOWN;
            note = "";
        }
    }
}
