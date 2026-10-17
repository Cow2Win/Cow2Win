package org.c2w.infra;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the files of one game catalog come from: the catalog built into the jar ({@code /catalog/}
 * on the classpath) or a folder on disk with the same layout (e.g. a downloaded catalog in {@code
 * ~/.cow2Win/catalog/}). Always the whole catalog from one place - see {@link CatalogStore} for
 * the active one. Paths are relative to the catalog root and use {@code /}, e.g.
 * {@code data/heroes.json} or {@code images/heroes/Adam.png}.
 */
public final class CatalogSource {

    private static final String BUILT_IN_ROOT = "/catalog/";
    private static final CatalogSource BUILT_IN = new CatalogSource(null);

    /** The folder of this catalog, null for the built-in one. */
    private final Path folder;

    private CatalogSource(Path folder) {
        this.folder = folder;
    }

    /** The catalog built into the jar. */
    public static CatalogSource builtIn() {
        return BUILT_IN;
    }

    /** The catalog in {@code catalogFolder} (same layout as {@code catalog/} in the repository). */
    public static CatalogSource folder(Path catalogFolder) {
        if (catalogFolder == null) {
            throw new IllegalArgumentException("catalogFolder must not be null");
        }
        return new CatalogSource(catalogFolder.toAbsolutePath().normalize());
    }

    public boolean isBuiltIn() {
        return folder == null;
    }

    /** The folder of this catalog, or null for the built-in one. */
    public Path folderPath() {
        return folder;
    }

    /** For the log: "built-in" or "downloaded (&lt;folder&gt;)". */
    public String describe() {
        return isBuiltIn() ? "built-in" : "downloaded (" + folder + ")";
    }

    /**
     * The content of a catalog file.
     *
     * @throws IOException if this catalog has no such file
     */
    public byte[] readBytes(String relativePath) throws IOException {
        checkRelative(relativePath);
        if (folder != null) {
            Path file = folder.resolve(relativePath);
            if (!Files.isRegularFile(file)) {
                throw new IOException("Catalog file not found: " + file);
            }
            return Files.readAllBytes(file);
        }
        try (InputStream in = CatalogSource.class.getResourceAsStream(BUILT_IN_ROOT + relativePath)) {
            if (in == null) {
                throw new IOException("Catalog file not found in the built-in catalog: " + relativePath);
            }
            return in.readAllBytes();
        }
    }

    /**
     * The text (UTF-8) of a catalog file, e.g. {@code "data/heroes.json"}.
     *
     * @throws IOException if this catalog has no such file
     */
    public String readText(String relativePath) throws IOException {
        return new String(readBytes(relativePath), StandardCharsets.UTF_8);
    }

    /** URL of a catalog image, e.g. {@code "images/heroes/Adam.png"}; null if this catalog has none. */
    public URL imageUrl(String relativePath) {
        checkRelative(relativePath);
        if (folder == null) {
            return CatalogSource.class.getResource(BUILT_IN_ROOT + relativePath);
        }
        Path file = folder.resolve(relativePath);
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
     * The parsed manifest of this catalog.
     *
     * @throws IOException              if it cannot be read
     * @throws IllegalArgumentException if it is malformed
     */
    public CatalogManifest manifest() throws IOException {
        return CatalogManifest.parse(readText(CatalogManifest.FILE_NAME));
    }

    /** Rejects absolute paths and paths leaving the catalog root. */
    private static void checkRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")
                || relativePath.contains("\\") || relativePath.contains(":")
                || ("/" + relativePath + "/").contains("/../")) {
            throw new IllegalArgumentException("Not a relative catalog path: " + relativePath);
        }
    }

    @Override
    public String toString() {
        return describe();
    }
}
