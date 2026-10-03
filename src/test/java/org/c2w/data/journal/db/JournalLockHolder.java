package org.c2w.data.journal.db;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Started as a separate JVM by {@code JournalDatabaseTest}: plays a second
 * Cow2Win instance that has the journal of a guild folder open. Prints
 * {@code OPEN} once the database is open and closes it when stdin ends.
 */
public final class JournalLockHolder {

    private JournalLockHolder() {
    }

    public static void main(String[] args) throws Exception {
        Optional<JournalDatabase> db = JournalDatabase.open(Path.of(args[0]), false);
        if (db.isEmpty()) {
            System.out.println("MISSING");
            return;
        }
        System.out.println("OPEN");
        System.out.flush();
        try (JournalDatabase ignored = db.get()) {
            while (System.in.read() >= 0) {
                // wait until the test closes our stdin
            }
        }
    }
}
