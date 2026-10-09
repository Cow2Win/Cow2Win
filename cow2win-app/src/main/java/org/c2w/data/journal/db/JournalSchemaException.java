package org.c2w.data.journal.db;

/**
 * The journal database has a schema this Cow2Win version cannot work with:
 * a newer version (written by a newer Cow2Win) or a broken migration. The
 * database is left untouched.
 */
public class JournalSchemaException extends JournalException {

    public JournalSchemaException(String message) {
        super(message);
    }

    public JournalSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
