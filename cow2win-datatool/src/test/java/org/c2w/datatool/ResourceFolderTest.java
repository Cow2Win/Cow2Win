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

class ResourceFolderTest {

    @TempDir
    Path temp;

    @Test
    void acceptsTheAppResourcesFolder() throws IOException {
        Path resources = TestResources.copyTo(temp);
        assertNull(ResourceFolder.check(resources));
    }

    @Test
    void rejectsAFolderWithoutHeroes() throws IOException {
        Path resources = Files.createDirectories(temp.resolve("x/src/main/resources/data"));
        String problem = ResourceFolder.check(resources.getParent());
        assertNotNull(problem);
        assertTrue(problem.contains("heroes.json"), problem);
    }

    @Test
    void rejectsAFolderOutsideSrcMainResources() throws IOException {
        // Looks like data (e.g. a workspace or a copy of the files), but is not the source tree.
        Path workspace = Files.createDirectories(temp.resolve("workspace/data"));
        Files.writeString(workspace.resolve("heroes.json"), "[]");
        String problem = ResourceFolder.check(workspace.getParent());
        assertNotNull(problem);
        assertTrue(problem.contains("src/main/resources"), problem);
        assertThrows(IllegalArgumentException.class,
                () -> ResourceFolder.find(new String[]{workspace.getParent().toString()}, temp));
    }

    @Test
    void findsTheFolderFromTheArgumentOrUpwardsFromTheWorkingDirectory() throws IOException {
        Path resources = TestResources.copyTo(temp).toAbsolutePath().normalize();
        assertEquals(Optional.of(resources), ResourceFolder.find(new String[]{temp.toString()}, Path.of("")));
        assertEquals(Optional.of(resources), ResourceFolder.find(new String[]{resources.toString()}, Path.of("")));

        Path toolModule = Files.createDirectories(temp.resolve("cow2win-datatool/src"));
        assertEquals(Optional.of(resources), ResourceFolder.find(new String[0], toolModule));
    }
}
