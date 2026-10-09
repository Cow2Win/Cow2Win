package org.c2w.data.journal.db;

import java.io.IOException;

/**
 * A Weltenschlacht journal database could not be opened, read or written.
 * Checked (an {@link IOException}, like the other repositories' failures) so
 * the GUI reports it instead of crashing; the message is meant for the user,
 * the cause (usually a {@link java.sql.SQLException}) for the log.
 */
public class JournalException extends IOException {

    public JournalException(String message) {
        super(message);
    }

    public JournalException(String message, Throwable cause) {
        super(message, cause);
    }
}
