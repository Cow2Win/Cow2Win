package org.c2w.infra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStoreTest {

    @TempDir
    Path temp;

    @AfterEach
    void backToTheBuiltInCatalog() {
        CatalogStore.useBuiltIn();
    }

    @Test
    void theBuiltInCatalogIsOnTheClasspath() throws IOException {
        assertTrue(CatalogStore.readText("data/heroes.json").contains("\"id\""));
        assertNotNull(CatalogStore.imageUrl("images/heroes/Adam.png"));
        assertNull(CatalogStore.imageUrl("images/heroes/NoSuchHero.png"));
        CatalogManifest manifest = CatalogStore.manifest();
        assertEquals(CatalogStore.SUPPORTED_CATALOG_FORMAT, manifest.formatVersion());
        assertTrue(manifest.files().stream().anyMatch(file -> file.path().equals("data/heroes.json")));
    }

    @Test
    void aFolderCanBeTheActiveCatalog() throws IOException {
        Files.createDirectories(temp.resolve("data"));
        Files.createDirectories(temp.resolve("images/heroes"));
        Files.writeString(temp.resolve("data/heroes.json"), "[{\"id\": \"zora\"}]", StandardCharsets.UTF_8);
        byte[] png = {1, 2, 3};
        Files.write(temp.resolve("images/heroes/Zora.png"), png);
        CatalogManifest written = new CatalogManifest(1, CatalogDataVersion.parse("2030-01-02.1"), "test",
                CatalogManifest.scan(temp));
        Files.writeString(temp.resolve(CatalogManifest.FILE_NAME), written.toJson(), StandardCharsets.UTF_8);

        CatalogStore.useFolder(temp);

        assertEquals("[{\"id\": \"zora\"}]", CatalogStore.readText("data/heroes.json"));
        URL image = CatalogStore.imageUrl("images/heroes/Zora.png");
        assertNotNull(image);
        try (var in = image.openStream()) {
            assertArrayEquals(png, in.readAllBytes());
        }
        assertEquals(written, CatalogStore.manifest());
        assertEquals(List.of("data/heroes.json", "images/heroes/Zora.png"),
                CatalogStore.manifest().files().stream().map(CatalogManifest.FileEntry::path).toList());
        assertEquals(CatalogManifest.sha256(png), CatalogStore.manifest().files().get(1).sha256());
    }

    @Test
    void aMissingFileIsReported() {
        CatalogStore.useFolder(temp);
        assertThrows(IOException.class, () -> CatalogStore.readText("data/heroes.json"));
        assertNull(CatalogStore.imageUrl("images/heroes/Adam.png"), "not taken from the built-in catalog");
        assertThrows(IOException.class, CatalogStore::manifest);

        CatalogStore.useBuiltIn();
        assertThrows(IOException.class, () -> CatalogStore.readText("data/noSuchFile.json"));
    }

    @Test
    void onlyRelativePathsInsideTheCatalogAreAccepted() {
        CatalogStore.useFolder(temp);
        for (String path : List.of("/data/heroes.json", "../config.properties", "data/../../x", "C:/x",
                "data\\heroes.json")) {
            assertThrows(IllegalArgumentException.class, () -> CatalogStore.readText(path), path);
            assertThrows(IllegalArgumentException.class, () -> CatalogStore.imageUrl(path), path);
        }
    }
}
