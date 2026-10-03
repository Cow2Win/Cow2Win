package org.c2w.data.journal.db;

import org.c2w.infra.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings a journal database to the current schema version: table
 * {@code schema_version(version, applied_at)} records every applied script,
 * and on opening, every missing script is run in order, each in its own
 * transaction (note: H2 commits DDL implicitly, so a script that fails halfway
 * may leave part of its tables behind - the version row is only written on
 * success, and the error is reported).
 *
 * <p>Scripts are classpath resources named {@code V<n>__<description>.sql},
 * listed in {@link #SCRIPTS} (classpath folders cannot be listed reliably in a
 * jar, so every new script is registered there - a test checks that none is
 * forgotten). Their versions must be 1, 2, 3 ... without gaps. Statements are
 * separated by a {@code ;} at the end of a line; lines starting with
 * {@code --} are comments; no {@code ;} inside string literals.
 *
 * <p>A database with a higher version than the newest script (written by a
 * newer Cow2Win) is not touched: {@link JournalSchemaException}.
 */
public final class SchemaMigrator {

    /** The schema scripts of this Cow2Win version, in version order. */
    public static final List<String> SCRIPTS = List.of(
            "/journal/schema/V1__initial.sql");

    private static final Pattern SCRIPT_NAME = Pattern.compile("(?:^|/)V(\\d+)__[^/]+\\.sql$");

    private final List<String> scripts;

    /** A migrator for {@code scripts} (classpath resources, see class Javadoc) - tests pass their own. */
    public SchemaMigrator(List<String> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            throw new IllegalArgumentException("SchemaMigrator needs at least one script");
        }
        for (int i = 0; i < scripts.size(); i++) {
            int version = versionOf(scripts.get(i));
            if (version != i + 1) {
                throw new IllegalArgumentException("Schema script " + scripts.get(i) + " has version " + version
                        + ", expected " + (i + 1) + " (versions must be 1, 2, 3 ... without gaps)");
            }
        }
        this.scripts = List.copyOf(scripts);
    }

    /** The migrator for the shipped {@link #SCRIPTS}. */
    public static SchemaMigrator standard() {
        return new SchemaMigrator(SCRIPTS);
    }

    /** The version the database has after {@link #migrate}. */
    public int latestVersion() {
        return scripts.size();
    }

    /** The version from a script name {@code V<n>__<description>.sql}. */
    static int versionOf(String script) {
        Matcher m = SCRIPT_NAME.matcher(script);
        if (!m.find()) {
            throw new IllegalArgumentException("Schema script name must be V<n>__<description>.sql: " + script);
        }
        return Integer.parseInt(m.group(1));
    }

    /**
     * Runs every script newer than the database's version. Leaves the connection
     * in auto-commit mode as it was found.
     *
     * @return the database's version afterwards
     * @throws JournalSchemaException if the database is newer than this migrator
     *                                knows, or a script cannot be read or fails
     */
    public int migrate(Connection connection) throws JournalSchemaException, SQLException {
        ensureVersionTable(connection);
        int current = currentVersion(connection);
        if (current > latestVersion()) {
            throw new JournalSchemaException("The journal database has schema version " + current
                    + ", but this Cow2Win version only knows up to " + latestVersion()
                    + " - it was written by a newer Cow2Win. Please update Cow2Win.");
        }
        boolean autoCommit = connection.getAutoCommit();
        try {
            for (int version = current + 1; version <= latestVersion(); version++) {
                apply(connection, version, scripts.get(version - 1));
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return latestVersion();
    }

    /** The highest applied version, {@code 0} for a new database. */
    public static int currentVersion(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INT NOT NULL PRIMARY KEY, applied_at TIMESTAMP NOT NULL)");
        }
    }

    private static void apply(Connection connection, int version, String script)
            throws JournalSchemaException, SQLException {
        List<String> statements = statements(read(script));
        connection.setAutoCommit(false);
        try {
            try (Statement st = connection.createStatement()) {
                for (String sql : statements) {
                    st.execute(sql);
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                ps.setInt(1, version);
                ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            }
            connection.commit();
            Logger.log("Journal database migrated to schema version " + version + " (" + script + ")");
        } catch (SQLException e) {
            connection.rollback();
            throw new JournalSchemaException("Journal schema migration to version " + version + " failed ("
                    + script + "): " + e.getMessage(), e);
        }
    }

    private static String read(String script) throws JournalSchemaException {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream(script)) {
            if (in == null) {
                throw new JournalSchemaException("Journal schema script not found on classpath: " + script);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JournalSchemaException("Could not read journal schema script " + script, e);
        }
    }

    /** Splits a script into statements (see class Javadoc). */
    static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.endsWith(";")) {
                current.append(trimmed, 0, trimmed.length() - 1);
                result.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(trimmed).append('\n');
            }
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString().strip());
        }
        return result;
    }
}
