package org.c2w.data.journal.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JournalDatabaseTest {

    private static final List<String> V1_AND_TEST_V2 = List.of(
            "/journal/schema/V1__initial.sql", "/journal/test-migrations/V2__test_marker.sql");

    @TempDir
    Path guildDir;

    @Test
    void withoutCreateIfMissingNoFileIsCreated() throws Exception {
        assertFalse(JournalDatabase.exists(guildDir));

        assertEquals(Optional.empty(), JournalDatabase.open(guildDir, false));

        assertFalse(JournalDatabase.exists(guildDir));
        try (Stream<Path> files = Files.list(guildDir)) {
            assertEquals(0, files.count(), "nothing at all may be created");
        }
    }

    @Test
    void createsTheFileAndMigratesToVersion1() throws Exception {
        try (JournalDatabase db = JournalDatabase.open(guildDir, true).orElseThrow()) {
            assertTrue(JournalDatabase.exists(guildDir));
            assertTrue(Files.isRegularFile(guildDir.resolve("journal.mv.db")));
            assertEquals(1, db.schemaVersion());
            assertEquals(List.of(1), versions(db));
        }
    }

    @Test
    void reopeningDoesNotMigrateTwice() throws Exception {
        JournalDatabase.open(guildDir, true).orElseThrow().close();

        try (JournalDatabase db = JournalDatabase.open(guildDir, false).orElseThrow()) {
            assertEquals(List.of(1), versions(db));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SEASON'"));
        }
    }

    @Test
    void migratesAV1DatabaseToANewerScriptVersion() throws Exception {
        JournalDatabase.open(guildDir, true).orElseThrow().close();

        try (JournalDatabase db = JournalDatabase.open(guildDir, false, new SchemaMigrator(V1_AND_TEST_V2))
                .orElseThrow()) {
            assertEquals(2, db.schemaVersion());
            assertEquals(List.of(1, 2), versions(db));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM test_marker"));
        }
        // ...and opening it again with the same scripts changes nothing.
        try (JournalDatabase db = JournalDatabase.open(guildDir, false, new SchemaMigrator(V1_AND_TEST_V2))
                .orElseThrow()) {
            assertEquals(List.of(1, 2), versions(db));
        }
    }

    @Test
    void databaseFromANewerCow2WinIsNotTouched() throws Exception {
        try (JournalDatabase db = JournalDatabase.open(guildDir, true).orElseThrow()) {
            db.transaction(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute("INSERT INTO schema_version (version, applied_at) VALUES (99, CURRENT_TIMESTAMP)");
                }
                return null;
            });
        }

        JournalSchemaException e = assertThrows(JournalSchemaException.class,
                () -> JournalDatabase.open(guildDir, false));
        assertTrue(e.getMessage().contains("99"), e.getMessage());

        // Still exactly as before (read with plain JDBC, bypassing the migration): versions 1 and 99.
        String url = "jdbc:h2:file:" + guildDir.toAbsolutePath().resolve(JournalDatabase.BASE_NAME) + ";IFEXISTS=TRUE";
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version ORDER BY version")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertTrue(rs.next());
            assertEquals(99, rs.getInt(1));
            assertFalse(rs.next());
        }
    }

    @Test
    void closeIsIdempotentAndReleasesTheFile() throws Exception {
        JournalDatabase db = JournalDatabase.open(guildDir, true).orElseThrow();
        db.close();
        db.close();

        assertTrue(db.isClosed());
        assertThrows(JournalException.class, () -> db.read(c -> 1));
        try (Stream<Path> files = Files.list(guildDir)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        try (Stream<Path> files = Files.list(guildDir)) {
            assertEquals(0, files.count(), "database files can be deleted after close");
        }
    }

    @Test
    void transactionIsRolledBackOnError() throws Exception {
        try (JournalDatabase db = JournalDatabase.open(guildDir, true).orElseThrow()) {
            assertThrows(JournalException.class, () -> db.transaction(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute("INSERT INTO season (season_number, start_date, end_date) VALUES (1, DATE '2026-01-01', DATE '2026-03-26')");
                    st.execute("INSERT INTO season (season_number, start_date, end_date) VALUES (1, DATE '2026-04-01', DATE '2026-06-24')");
                }
                return null;
            }));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM season"));
        }
    }

    @Test
    void secondProcessGetsALockedException() throws Exception {
        JournalDatabase.open(guildDir, true).orElseThrow().close();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                JournalLockHolder.class.getName(), guildDir.toString())
                .redirectErrorStream(true)
                .start();
        try {
            BufferedReader out = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = out.readLine()) != null && !line.contains("OPEN")) {
                // skip log output of the other JVM
            }
            assertNotNull(line, "the other process did not open the journal");

            JournalLockedException e = assertThrows(JournalLockedException.class,
                    () -> JournalDatabase.open(guildDir, false));
            assertTrue(e.getMessage().contains("another program"), e.getMessage());
        } finally {
            holder.getOutputStream().close();
            if (!holder.waitFor(30, TimeUnit.SECONDS)) {
                holder.destroyForcibly();
            }
        }
        // Released again once the other process is gone.
        JournalDatabase.open(guildDir, false).orElseThrow().close();
    }

    @Test
    void jdbcPathWithSpacesAndUmlauts() throws Exception {
        Path dir = Files.createDirectories(guildDir.resolve("Deutscher Bund ä"));
        try (JournalDatabase db = JournalDatabase.open(dir, true).orElseThrow()) {
            assertEquals(dir, db.guildDir());
        }
        assertTrue(Files.isRegularFile(dir.resolve(JournalDatabase.FILE_NAME)));
    }

    static List<Integer> versions(JournalDatabase db) throws JournalException {
        return db.read(c -> {
            List<Integer> result = new java.util.ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT version FROM schema_version ORDER BY version")) {
                while (rs.next()) {
                    result.add(rs.getInt(1));
                }
            }
            return result;
        });
    }

    static long count(JournalDatabase db, String sql) throws JournalException {
        return db.read(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }
}
