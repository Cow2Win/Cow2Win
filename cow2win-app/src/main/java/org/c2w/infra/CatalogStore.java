package org.c2w.infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/**
 * The only place that knows where the game catalog's files come from - the objective game data
 * (heroes, titans, pets, war flags, fortifications), their display names and their images, as
 * kept in the repository's {@code catalog/} folder and described by its {@link CatalogManifest}.
 *
 * <p>The active catalog is one {@link CatalogSource}: the one built into the jar (the default) or
 * a downloaded one, chosen once at startup ({@code CatalogSelector}) and never changed while the
 * program runs - every file comes from that one source, never mixed with the other. Tests may
 * switch it with {@link #useFolder}/{@link #useBuiltIn}. Paths are always relative to the catalog
 * root and use {@code /}, e.g. {@code data/heroes.json} or {@code images/heroes/Adam.png}.
 */
public final class CatalogStore {

    /** The highest {@link CatalogManifest#formatVersion()} this program understands. */
    public static final int SUPPORTED_CATALOG_FORMAT = 1;

    private static volatile CatalogSource active = CatalogSource.builtIn();

    private CatalogStore() {
    }

    /** The active catalog. */
    public static CatalogSource active() {
        return active;
    }

    /**
     * The text (UTF-8) of a file of the active catalog, e.g. {@code "data/heroes.json"}.
     *
     * @throws IOException if the active catalog has no such file
     */
    public static String readText(String relativePath) throws IOException {
        return active.readText(relativePath);
    }

    /** URL of an image of the active catalog, e.g. {@code "images/heroes/Adam.png"}; null if it has none. */
    public static URL imageUrl(String relativePath) {
        return active.imageUrl(relativePath);
    }

    /**
     * The parsed manifest of the active catalog.
     *
     * @throws IOException              if it cannot be read
     * @throws IllegalArgumentException if it is malformed
     */
    public static CatalogManifest manifest() throws IOException {
        return active.manifest();
    }

    /** Makes {@code source} the active catalog - at startup, before anything reads the catalog. */
    public static void use(CatalogSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        active = source;
    }

    /** Makes the catalog in {@code catalogFolder} (same layout as {@code catalog/}) the active one. */
    public static void useFolder(Path catalogFolder) {
        use(CatalogSource.folder(catalogFolder));
    }

    /** Makes the catalog built into the jar the active one again (the default). */
    public static void useBuiltIn() {
        use(CatalogSource.builtIn());
    }
}
