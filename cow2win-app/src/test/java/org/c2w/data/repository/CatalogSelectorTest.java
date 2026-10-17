package org.c2w.data.repository;

import org.c2w.infra.CatalogSource;
import org.c2w.infra.CatalogStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which catalog is used at startup - the built-in one unless a valid, newer downloaded one exists. */
class CatalogSelectorTest {

    @TempDir
    Path temp;

    @AfterEach
    void backToTheBuiltInCatalog() {
        CatalogStore.useBuiltIn();
    }

    @Test
    void noFolderMeansTheBuiltInCatalogWithoutAReason() {
        CatalogSelector.Selection selection = CatalogSelector.select(temp.resolve("catalog"));
        assertTrue(selection.source().isBuiltIn());
        assertEquals(0, selection.logLines().size());
    }

    @Test
    void aNewerValidCatalogIsUsed() throws IOException {
        Path folder = TestCatalogs.catalog(temp.resolve("catalog"), "2099-01-01", 1);

        CatalogSelector.Selection selection = CatalogSelector.select(folder);

        assertEquals(folder.toAbsolutePath().normalize(), selection.source().folderPath());
        assertTrue(selection.logLines().stream().noneMatch(line -> line.contains("not used")), selection::toString);
    }

    @Test
    void anOlderOrEquallyOldCatalogIsNotUsed() throws IOException {
        String builtIn = CatalogSource.builtIn().manifest().dataVersion().toString();
        assertBuiltIn(TestCatalogs.catalog(temp.resolve("older"), "2020-01-01", 1), "not newer");
        assertBuiltIn(TestCatalogs.catalog(temp.resolve("same"), builtIn, 1), "not newer");
    }

    @Test
    void aBrokenManifestIsNotUsed() throws IOException {
        Path folder = TestCatalogs.catalog(temp.resolve("catalog"), "2099-01-01", 1);
        Files.writeString(folder.resolve("manifest.json"), "{ broken");
        assertBuiltIn(folder, "manifest.json cannot be read");

        Files.delete(folder.resolve("manifest.json"));
        assertBuiltIn(folder, "manifest.json cannot be read");
    }

    @Test
    void aWrongChecksumIsNotUsed() throws IOException {
        Path folder = TestCatalogs.catalog(temp.resolve("catalog"), "2099-01-01", 1);
        Files.writeString(folder.resolve("data/pets.json"), "[]");
        assertBuiltIn(folder, "Size or SHA-256 does not match the manifest: data/pets.json");
    }

    @Test
    void aMissingFileIsNotUsed() throws IOException {
        Path folder = TestCatalogs.catalog(temp.resolve("catalog"), "2099-01-01", 1);
        Files.delete(folder.resolve("images/heroes/Adam.png"));
        assertBuiltIn(folder, "Missing file: images/heroes/Adam.png");
    }

    @Test
    void aNewerFormatIsNotUsed() throws IOException {
        Path folder = TestCatalogs.catalog(temp.resolve("catalog"), "2099-01-01", 2);
        assertBuiltIn(folder, "formatVersion 2 needs a newer program");
    }

    @Test
    void applyAtStartupMakesTheChoiceTheActiveCatalogAndKeepsARejectedFolder() throws IOException {
        Path folder = TestCatalogs.catalog(temp.resolve("catalog"), "2099-01-01", 1);
        CatalogSelector.applyAtStartup(folder);
        assertEquals(folder.toAbsolutePath().normalize(), CatalogStore.active().folderPath());

        Files.writeString(folder.resolve("data/pets.json"), "[]");
        CatalogSelector.applyAtStartup(folder);
        assertTrue(CatalogStore.active().isBuiltIn());
        assertTrue(Files.isDirectory(folder) && Files.exists(folder.resolve("data/pets.json")), "never deleted");
    }

    private static void assertBuiltIn(Path folder, String reason) {
        CatalogSelector.Selection selection = CatalogSelector.select(folder);
        assertTrue(selection.source().isBuiltIn(), folder + " must not be used");
        assertEquals(1, selection.logLines().size(), selection::toString);
        String line = selection.logLines().get(0);
        assertTrue(line.startsWith("Downloaded catalog in ") && line.contains(" not used: ") && line.contains(reason),
                line);
    }
}
