package org.c2w.datatool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryFolderTest {

    @TempDir
    Path temp;

    @Test
    void acceptsARepositoryWithCatalogAndAppResources() throws IOException {
        Path repository = TestResources.copyTo(temp);
        assertNull(RepositoryFolder.check(repository));
    }

    @Test
    void rejectsARepositoryWithoutCatalogHeroes() throws IOException {
        Files.createDirectories(temp.resolve(RepositoryFolder.APP_RESOURCES).resolve("data"));
        Files.writeString(temp.resolve(RepositoryFolder.APP_RESOURCES).resolve("data/cowScore.json"), "{}");
        String problem = RepositoryFolder.check(temp);
        assertNotNull(problem);
        assertTrue(problem.contains("catalog/data/heroes.json"), problem);
    }

    @Test
    void rejectsAFolderWithoutTheAppResources() throws IOException {
        // Looks like catalog data (e.g. a workspace or a downloaded catalog), but is not the repository.
        Path catalogData = Files.createDirectories(temp.resolve("workspace/catalog/data"));
        Files.writeString(catalogData.resolve("heroes.json"), "[]");
        Path workspace = temp.resolve("workspace");
        String problem = RepositoryFolder.check(workspace);
        assertNotNull(problem);
        assertTrue(problem.contains("cow2win-app/src/main/resources/data/cowScore.json"), problem);
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryFolder.find(new String[]{workspace.toString()}, temp));
    }

    @Test
    void findsTheRepositoryFromTheArgumentOrUpwardsFromTheWorkingDirectory() throws IOException {
        Path repository = TestResources.copyTo(temp).toAbsolutePath().normalize();
        assertEquals(Optional.of(repository), RepositoryFolder.find(new String[]{temp.toString()}, Path.of("")));

        Path toolModule = Files.createDirectories(temp.resolve("cow2win-datatool/src"));
        assertEquals(Optional.of(repository), RepositoryFolder.find(new String[0], toolModule));
    }
}
