package org.c2w.infra;

import java.io.IOException;

/**
 * The {@code dataVersion} and {@code note} of the active game catalog, read from its {@link
 * CatalogManifest} (via {@link CatalogStore}) - which Hero Wars: Dominion Era game state the
 * heroes, titans, pets, war flags and fortifications were last checked against. Logged at
 * startup.
 *
 * <p>Read once and cached, same convention as the catalog repositories'
 * own {@code ensureLoaded()} pattern. A missing or malformed manifest never
 * breaks app startup - it just falls back to {@value #UNKNOWN} and logs
 * why (written to the technical log via {@link Logger}).
 */
public final class CatalogVersion {

    private static final String UNKNOWN = "unknown";

    private static volatile String dataVersion;
    private static volatile String note;

    private CatalogVersion() {
    }

    /** The catalog data's last-checked-against version, e.g. "2026-10-17" or "2026-10-17.2". */
    public static String dataVersion() {
        ensureLoaded();
        return dataVersion;
    }

    /** A free-text note accompanying the version (what changed in this catalog state). */
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
            CatalogManifest manifest = CatalogStore.manifest();
            note = manifest.note();
            dataVersion = manifest.dataVersion().toString();
        } catch (IOException | RuntimeException e) {
            Logger.log(CatalogManifest.FILE_NAME + ": could not read data version (" + e.getMessage() + ")");
            note = "";
            dataVersion = UNKNOWN;
        }
    }
}
