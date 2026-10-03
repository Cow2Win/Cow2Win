package org.c2w.data.journal.db;

/**
 * The journal database file is in use by another process - typically a second
 * Cow2Win instance with the same guild open. H2 locks the file per process.
 */
public class JournalLockedException extends JournalException {

    public JournalLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
