package org.c2w.datatool.data;

import org.c2w.datatool.TestResources;
import org.c2w.infra.CatalogManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loading and writing back without a change must reproduce every file exactly. */
class RoundTripTest {

    @TempDir
    Path temp;

    @Test
    void everyDataFileIsWrittenBackByteForByte() throws IOException {
        Path root = TestResources.copyTo(temp);
        DataSet data = DataSet.load(root);
        for (TableKind kind : TableKind.values()) {
            String file = Files.readString(root.resolve(kind.relativePath()), StandardCharsets.UTF_8);
            // fortifications.json may have CRLF in a working copy (repository: LF) - only that may differ.
            assertEquals(file.replace("\r\n", "\n"), data.render(kind), kind.relativePath().toString());
        }
    }

    @Test
    void theNamesAndTheManifestAreReproduced() throws IOException {
        Path root = TestResources.copyTo(temp);
        String names = Files.readString(root.resolve(DataSet.NAMES), StandardCharsets.UTF_8);
        NamesFile file = new NamesFile(names);
        assertEquals(names, file.text());
        assertFalse(file.isModified());
        String manifest = Files.readString(root.resolve(DataSet.MANIFEST), StandardCharsets.UTF_8);
        assertEquals(manifest, CatalogManifest.parse(manifest).toJson());
    }

    @Test
    void savingWithoutChangesWritesNothing() throws IOException {
        Path root = TestResources.copyTo(temp);
        Map<Path, byte[]> before = snapshot(root);
        DataSet data = DataSet.load(root);
        assertFalse(data.isDirty());

        SaveResult result = data.save(new SaveOptions(Set.of(), LocalDate.of(2030, 1, 1)));

        assertTrue(result.saved(), () -> "errors: " + result.errors());
        assertEquals(java.util.List.of(), result.written());
        Map<Path, byte[]> after = snapshot(root);
        assertEquals(before.keySet(), after.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), path.toString()));
    }

    @Test
    void theShippedDataHasNoErrors() throws IOException {
        DataSet data = DataSet.load(TestResources.copyTo(temp));
        assertEquals(java.util.List.of(), data.validate().stream().filter(Problem::isError).toList());
    }

    static Map<Path, byte[]> snapshot(Path root) throws IOException {
        Map<Path, byte[]> files = new HashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path), Files.readAllBytes(path));
            }
        }
        return files;
    }
}
