package org.c2w.infra;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The only place that knows where the game catalog's files come from - the objective game data
 * (heroes, titans, pets, war flags, fortifications) and their images, as kept in the
 * repository's {@code catalog/} folder and described by its {@link CatalogManifest}.
 *
 * <p>The active catalog is either the one built into the jar ({@code /catalog/} on the
 * classpath, the default) or a folder on disk with the same layout ({@link #useFolder}, for
 * now only used by tests). Paths are always relative to the catalog root and use {@code /},
 * e.g. {@code data/heroes.json} or {@code images/heroes/Adam.png}.
 */
public final class CatalogStore {

    /** The highest {@link CatalogManifest#formatVersion()} this program understands. */
    public static final int SUPPORTED_CATALOG_FORMAT = 1;

    private static final String BUILT_IN_ROOT = "/catalog/";

    /** The folder of the active catalog, null for the built-in one. */
    private static volatile Path folder;

    private CatalogStore() {
    }

    /**
     * The text (UTF-8) of a catalog file, e.g. {@code "data/heroes.json"}.
     *
     * @throws IOException if the active catalog has no such file
     */
    public static String readText(String relativePath) throws IOException {
        checkRelative(relativePath);
        Path activeFolder = folder;
        if (activeFolder != null) {
            Path file = activeFolder.resolve(relativePath);
            if (!Files.isRegularFile(file)) {
                throw new IOException("Catalog file not found: " + file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        try (InputStream in = CatalogStore.class.getResourceAsStream(BUILT_IN_ROOT + relativePath)) {
            if (in == null) {
                throw new IOException("Catalog file not found in the built-in catalog: " + relativePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** URL of a catalog image, e.g. {@code "images/heroes/Adam.png"}; null if the active catalog has none. */
    public static URL imageUrl(String relativePath) {
        checkRelative(relativePath);
        Path activeFolder = folder;
        if (activeFolder == null) {
            return CatalogStore.class.getResource(BUILT_IN_ROOT + relativePath);
        }
        Path file = activeFolder.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return file.toUri().toURL();
        } catch (MalformedURLException e) {
            Logger.logException("Catalog image has no URL: " + file, e);
            return null;
        }
    }

    /**
     * The parsed manifest of the active catalog.
     *
     * @throws IOException              if it cannot be read
     * @throws IllegalArgumentException if it is malformed
     */
    public static CatalogManifest manifest() throws IOException {
        return CatalogManifest.parse(readText(CatalogManifest.FILE_NAME));
    }

    /** Makes the catalog in {@code catalogFolder} (same layout as {@code catalog/}) the active one. */
    public static void useFolder(Path catalogFolder) {
        if (catalogFolder == null) {
            throw new IllegalArgumentException("catalogFolder must not be null");
        }
        folder = catalogFolder.toAbsolutePath().normalize();
    }

    /** Makes the catalog built into the jar the active one again (the default). */
    public static void useBuiltIn() {
        folder = null;
    }

    /** Rejects absolute paths and paths leaving the catalog root. */
    private static void checkRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")
                || relativePath.contains("\\") || relativePath.contains(":")
                || ("/" + relativePath + "/").contains("/../")) {
            throw new IllegalArgumentException("Not a relative catalog path: " + relativePath);
        }
    }
}
