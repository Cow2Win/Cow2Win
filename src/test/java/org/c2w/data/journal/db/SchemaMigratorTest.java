package org.c2w.data.journal.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMigratorTest {

    private static final Path SCHEMA_DIR = Paths.get("src", "main", "resources", "journal", "schema");

    /** Classpath folders can't be listed in a jar, so every script must be registered in SCRIPTS. */
    @Test
    void everyShippedScriptIsRegistered() throws IOException {
        List<String> onDisk;
        try (Stream<Path> files = Files.list(SCHEMA_DIR)) {
            onDisk = files.map(p -> "/journal/schema/" + p.getFileName()).sorted().toList();
        }

        assertEquals(onDisk, SchemaMigrator.SCRIPTS.stream().sorted().toList());
        assertEquals(SchemaMigrator.SCRIPTS.size(), SchemaMigrator.standard().latestVersion());
    }

    @Test
    void versionsMustStartAt1WithoutGaps() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaMigrator(List.of("/journal/test-migrations/V2__test_marker.sql")));
        assertThrows(IllegalArgumentException.class, () -> new SchemaMigrator(List.of("/x/initial.sql")));
        assertThrows(IllegalArgumentException.class, () -> new SchemaMigrator(List.of()));
        assertEquals(12, SchemaMigrator.versionOf("/journal/schema/V12__more_stuff.sql"));
    }

    @Test
    void splitsStatementsAndSkipsComments() {
        List<String> statements = SchemaMigrator.statements("""
                -- comment;
                CREATE TABLE a (
                    id INT -- not a comment line, kept
                );

                INSERT INTO a VALUES (1);
                CREATE TABLE b (id INT)""");

        assertEquals(3, statements.size(), statements.toString());
        assertTrue(statements.get(0).startsWith("CREATE TABLE a ("));
        assertTrue(statements.get(0).endsWith(")"));
        assertEquals("INSERT INTO a VALUES (1)", statements.get(1));
        assertEquals("CREATE TABLE b (id INT)", statements.get(2));
    }
}
