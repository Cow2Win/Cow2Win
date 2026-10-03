package org.c2w.service;

import org.c2w.data.journal.db.JournalDatabase;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Owns the Weltenschlacht journal database of the OPEN guild: at most one
 * {@link JournalDatabase} at a time, opened lazily when the journal is first
 * needed and closed
 * <ul>
 *   <li>when another guild is opened ({@link AppContext.Listener#guildChanged} with a
 *       different guild folder - in-memory edits of the same guild keep it open),</li>
 *   <li>before a guild folder is deleted ({@link GuildService#deleteGuild}; Windows keeps
 *       open files locked),</li>
 *   <li>at program exit.</li>
 * </ul>
 * One instance per application, reachable via {@link AppContext#journal()}.
 */
public final class JournalService implements AppContext.Listener {

    private final Supplier<Path> currentGuildDir;
    private JournalDatabase database;
    private JournalRepository repository;

    /** A service following the open guild of {@code context} (registers itself as listener). */
    JournalService(AppContext context) {
        this(() -> context.guildFilePath() == null ? null : context.guildFilePath().getParent());
        context.addListener(this);
    }

    /** A service for the guild folder {@code currentGuildDir} supplies (tests). */
    JournalService(Supplier<Path> currentGuildDir) {
        this.currentGuildDir = Objects.requireNonNull(currentGuildDir);
    }

    /**
     * The journal of the open guild, opened on first use.
     *
     * @param createIfMissing create the database if the guild has none yet (first save)
     * @return the repository, or empty if no guild is open, or the guild has no journal
     *         and {@code createIfMissing} is false
     */
    public synchronized Optional<JournalRepository> repository(boolean createIfMissing) throws JournalException {
        Path guildDir = currentGuildDir.get();
        if (guildDir == null) {
            return Optional.empty();
        }
        if (database != null && !samePath(database.guildDir(), guildDir)) {
            closeCurrent();
        }
        if (database == null) {
            Optional<JournalDatabase> opened = JournalDatabase.open(guildDir, createIfMissing);
            if (opened.isEmpty()) {
                return Optional.empty();
            }
            database = opened.get();
            repository = new JournalRepository(database);
        }
        return Optional.of(repository);
    }

    /** True if a journal database is open right now. */
    public synchronized boolean isOpen() {
        return database != null;
    }

    /** The guild folder of the open journal, empty if none is open. */
    public synchronized Optional<Path> openGuildDir() {
        return database == null ? Optional.empty() : Optional.of(database.guildDir());
    }

    /** Closes the open journal, if any. */
    public synchronized void closeCurrent() {
        if (database != null) {
            database.close();
            database = null;
            repository = null;
        }
    }

    /** Closes the open journal if it belongs to {@code guildDir} - call before deleting or renaming that folder. */
    public synchronized void closeIfOpenFor(Path guildDir) {
        if (database != null && samePath(database.guildDir(), guildDir)) {
            closeCurrent();
        }
    }

    /** Another guild was opened: close the journal of the previous one. */
    @Override
    public synchronized void guildChanged() {
        if (database != null && !samePath(database.guildDir(), currentGuildDir.get())) {
            closeCurrent();
        }
    }

    private static boolean samePath(Path a, Path b) {
        return a != null && b != null && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
