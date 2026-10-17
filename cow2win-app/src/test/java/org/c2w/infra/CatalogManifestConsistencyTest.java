package org.c2w.infra;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The repository's {@code catalog/manifest.json} matches the files in {@code catalog/}: every
 * file is listed and every listed file exists, with the right size and SHA-256. The manifest
 * is written by the data tool on save - a file changed or added by hand breaks this test.
 */
class CatalogManifestConsistencyTest {

    /** The repository's catalog folder - Surefire runs in the app module's folder. */
    private static final Path CATALOG = Path.of("..", "catalog");

    @Test
    void theManifestListsExactlyTheFilesOfTheCatalog() throws IOException {
        CatalogManifest manifest = CatalogManifest.parse(
                Files.readString(CATALOG.resolve(CatalogManifest.FILE_NAME), StandardCharsets.UTF_8));
        Map<String, CatalogManifest.FileEntry> listed = byPath(manifest.files());
        Map<String, CatalogManifest.FileEntry> actual = byPath(CatalogManifest.scan(CATALOG));

        List<String> problems = new ArrayList<>();
        for (String path : actual.keySet()) {
            if (!listed.containsKey(path)) {
                problems.add("not in the manifest: " + path);
            } else if (!Objects.equals(listed.get(path), actual.get(path))) {
                problems.add("size/SHA-256 differ: " + path);
            }
        }
        for (String path : listed.keySet()) {
            if (!actual.containsKey(path)) {
                problems.add("listed, but missing: " + path);
            }
        }
        if (!problems.isEmpty()) {
            fail("catalog/manifest.json does not match catalog/ - save in the data tool to rewrite it:\n  "
                    + String.join("\n  ", problems));
        }
        assertEquals(manifest.files().stream().map(CatalogManifest.FileEntry::path).sorted().toList(),
                manifest.files().stream().map(CatalogManifest.FileEntry::path).toList(), "files sorted by path");
    }

    @Test
    void theManifestHasASupportedFormat() throws IOException {
        CatalogManifest manifest = CatalogManifest.parse(
                Files.readString(CATALOG.resolve(CatalogManifest.FILE_NAME), StandardCharsets.UTF_8));
        assertTrue(manifest.formatVersion() >= 1 && manifest.formatVersion() <= CatalogStore.SUPPORTED_CATALOG_FORMAT,
                "formatVersion " + manifest.formatVersion());
    }

    private static Map<String, CatalogManifest.FileEntry> byPath(List<CatalogManifest.FileEntry> files) {
        Map<String, CatalogManifest.FileEntry> result = new LinkedHashMap<>();
        files.forEach(file -> result.put(file.path(), file));
        return result;
    }
}
