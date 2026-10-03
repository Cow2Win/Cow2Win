package org.c2w.service;

import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;

import java.util.Optional;

/**
 * Access to the Weltenschlacht journal of the open guild - implemented by
 * {@link JournalService}; tests can supply their own (e.g. one that fails).
 */
@FunctionalInterface
public interface JournalStore {

    /**
     * The journal of the open guild.
     *
     * @param createIfMissing create the database if the guild has none yet
     * @return the repository, or empty if there is no open guild, or no journal
     *         and {@code createIfMissing} is false
     */
    Optional<JournalRepository> repository(boolean createIfMissing) throws JournalException;
}
