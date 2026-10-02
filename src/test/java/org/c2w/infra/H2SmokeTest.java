package org.c2w.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for the H2 dependency (Schlachtplan phase 0.3): the driver is
 * found via {@code META-INF/services/java.sql.Driver}, a file database in a
 * temp directory can be created, Unicode text (the battle logs come in
 * DE/EN/FR/RU) survives a round trip, and the database files can be deleted
 * once the last connection is closed (no lingering Windows file lock).
 */
class H2SmokeTest {

    private static final List<String> NAMES = List.of(
            "Союз Server 59",                   // Cyrillic guild name
            "Défaite -42 pts de classement",    // French accents
            "Draufgänger, Größe, Kaskade",      // German umlauts / sharp s
            "Totem d'esprit des ténèbres",      // apostrophe must survive the SQL layer
            "Heroes' Bridge",              // non-breaking space
            "🐮 Cow2Win");            // emoji outside the BMP

    @TempDir
    Path dir;

    @Test
    void driverIsRegisteredAsService() {
        boolean found = ServiceLoader.load(Driver.class).stream()
                .anyMatch(p -> p.type().getName().equals("org.h2.Driver"));
        assertTrue(found, "org.h2.Driver not registered via META-INF/services");
    }

    @Test
    void fileDatabaseRoundTripsUnicodeAndCanBeDeleted() throws SQLException, IOException {
        Path base = dir.resolve("smoke");
        String url = "jdbc:h2:file:" + base.toAbsolutePath();

        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE entry (id INT PRIMARY KEY, name VARCHAR(200) NOT NULL)");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO entry VALUES (?, ?)")) {
                for (int i = 0; i < NAMES.size(); i++) {
                    ps.setInt(1, i);
                    ps.setString(2, NAMES.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        Path dbFile = dir.resolve("smoke.mv.db");
        assertTrue(Files.exists(dbFile), "database file not created: " + dbFile);

        // Reopen so the values really come from disk, not from a cached session.
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name FROM entry ORDER BY id")) {
            for (String expected : NAMES) {
                assertTrue(rs.next());
                assertEquals(expected, rs.getString("name"));
            }
            assertFalse(rs.next());
        }

        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                Files.delete(f);
            }
        }
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(0, files.count(), "database files left behind");
        }
    }
}
