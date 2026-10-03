package org.c2w.data.journal.db;

import org.c2w.infra.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * The Weltenschlacht journal database of ONE Cow2Win guild: an embedded H2
 * file {@code <guildDir>/journal.mv.db} (no server mode, no auto-server).
 * Created only when the first battle log is saved - a guild without a journal
 * has no database file at all.
 *
 * <p>Holds exactly one connection; every access goes through {@link #read} or
 * {@link #transaction}, which are serialized on this object (the app is Swing
 * plus background {@code SwingWorker}s). No connection pool.
 *
 * <p>H2 locks the file per process: a second Cow2Win instance with the same
 * guild open gets a {@link JournalLockedException}. Close the database before
 * the guild folder is deleted or renamed (Windows keeps open files locked).
 */
public final class JournalDatabase implements AutoCloseable {

    /** Base name of the database inside the guild folder (H2 appends {@code .mv.db}). */
    public static final String BASE_NAME = "journal";

    /** File name of the database inside the guild folder. */
    public static final String FILE_NAME = BASE_NAME + ".mv.db";

    /** H2: "Database may be already in use" - the file is locked by another process. */
    private static final int H2_DATABASE_ALREADY_OPEN = 90020;
    /** H2: database not found and IFEXISTS=TRUE. */
    private static final int H2_DATABASE_NOT_FOUND_WITH_IF_EXISTS = 90146;

    /** Work done with the single connection; may throw SQL or journal errors. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException, JournalException;
    }

    private final Path guildDir;
    private final Connection connection;
    private final int schemaVersion;
    private boolean closed;

    private JournalDatabase(Path guildDir, Connection connection, int schemaVersion) {
        this.guildDir = guildDir;
        this.connection = connection;
        this.schemaVersion = schemaVersion;
    }

    /** True if {@code guildDir} contains a journal database file. */
    public static boolean exists(Path guildDir) {
        return Files.isRegularFile(guildDir.resolve(FILE_NAME));
    }

    /**
     * Opens the journal of {@code guildDir} and migrates it to the current schema.
     *
     * @param createIfMissing create the database file if there is none
     * @return the database, or empty if there is none and {@code createIfMissing} is false
     *         (no file is created then)
     * @throws JournalLockedException if another process has the database open
     * @throws JournalSchemaException if the database is newer than this Cow2Win or a migration fails
     * @throws JournalException       on any other database error
     */
    public static Optional<JournalDatabase> open(Path guildDir, boolean createIfMissing) throws JournalException {
        return open(guildDir, createIfMissing, SchemaMigrator.standard());
    }

    /** Like {@link #open(Path, boolean)} with an explicit migrator (tests with their own scripts). */
    public static Optional<JournalDatabase> open(Path guildDir, boolean createIfMissing, SchemaMigrator migrator)
            throws JournalException {
        if (!createIfMissing && !exists(guildDir)) {
            return Optional.empty();
        }
        String url = jdbcUrl(guildDir, createIfMissing);
        Connection connection;
        try {
            connection = DriverManager.getConnection(url, "sa", "");
        } catch (SQLException e) {
            if (e.getErrorCode() == H2_DATABASE_NOT_FOUND_WITH_IF_EXISTS) {
                return Optional.empty();
            }
            throw translate("open", guildDir, e);
        }
        try {
            int version = migrator.migrate(connection);
            Logger.log("Journal database opened: " + guildDir.resolve(FILE_NAME) + " (schema version " + version + ")");
            return Optional.of(new JournalDatabase(guildDir, connection, version));
        } catch (JournalException e) {
            closeQuietly(connection);
            throw e;
        } catch (SQLException e) {
            closeQuietly(connection);
            throw translate("migrate", guildDir, e);
        }
    }

    /** The guild folder this journal belongs to. */
    public Path guildDir() {
        return guildDir;
    }

    /** Schema version after opening. */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** Runs read-only {@code work} (auto-commit). */
    public synchronized <T> T read(Work<T> work) throws JournalException {
        ensureOpen();
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw translate("read", guildDir, e);
        }
    }

    /** Runs {@code work} in one transaction: committed on success, rolled back on any exception. */
    public synchronized <T> T transaction(Work<T> work) throws JournalException {
        ensureOpen();
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | JournalException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw translate("write", guildDir, e);
        }
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Shuts the database down with {@code SHUTDOWN COMPACT} - H2 rewrites the
     * file without the space freed by replaced or deleted logs, which would
     * otherwise stay in the file - and closes the connection; H2 then releases
     * the file. If compacting fails, the connection is closed anyway (the data
     * is safe, only the file stays bigger). Idempotent.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (Statement st = connection.createStatement()) {
            st.execute("SHUTDOWN COMPACT");
        } catch (SQLException e) {
            Logger.logException("Could not compact the journal of " + guildDir + " on close", e);
        }
        closeQuietly(connection);
        Logger.log("Journal database closed: " + guildDir.resolve(FILE_NAME));
    }

    // --- private ---

    private void ensureOpen() throws JournalException {
        if (closed) {
            throw new JournalException("The journal of " + guildDir + " is closed");
        }
    }

    private static String jdbcUrl(Path guildDir, boolean createIfMissing) {
        String path = guildDir.toAbsolutePath().resolve(BASE_NAME).toString().replace('\\', '/');
        return "jdbc:h2:file:" + path + (createIfMissing ? "" : ";IFEXISTS=TRUE");
    }

    private static JournalException translate(String action, Path guildDir, SQLException e) {
        if (e.getErrorCode() == H2_DATABASE_ALREADY_OPEN) {
            return new JournalLockedException("The journal of " + guildDir
                    + " is in use by another program - is Cow2Win already running with this guild?", e);
        }
        return new JournalException("Could not " + action + " the journal of " + guildDir + ": " + e.getMessage(), e);
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            Logger.logException("Could not close journal database connection", e);
        }
    }
}
