package org.c2w.data.journal.db;

import java.util.List;

/**
 * Result of {@link JournalRepository#saveLog}.
 *
 * @param battleId      id of the battle the log belongs to
 * @param battleCreated true if the battle was new
 * @param outcome       whether the log was new, replaced an earlier export or was identical
 * @param warnings      things the caller should tell the user (in English), e.g. a running
 *                      export saved for an already finished battle
 */
public record SaveResult(int battleId, boolean battleCreated, Outcome outcome, List<String> warnings) {

    /** What happened to the log of this direction. */
    public enum Outcome {
        /** There was no log of this direction yet. */
        CREATED,
        /** An earlier export of this direction was replaced completely. */
        REPLACED,
        /** The very same file (same SHA-256) was already stored - nothing written. */
        UNCHANGED
    }

    public SaveResult {
        if (outcome == null) {
            throw new IllegalArgumentException("SaveResult needs an outcome");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
