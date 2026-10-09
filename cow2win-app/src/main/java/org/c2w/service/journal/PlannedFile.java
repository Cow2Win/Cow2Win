package org.c2w.service.journal;

import org.c2w.data.journal.BattleLogParseResult;
import org.c2w.data.journal.LogDirection;

import java.nio.file.Path;

/**
 * One file of an import as {@code prepare} saw it.
 *
 * @param path      the file
 * @param status    will be imported, skipped, or failed
 * @param error     why it failed or was skipped, {@code null} if {@link Status#READY}
 * @param detail    technical detail of an error (exception message, for the log), may be {@code null}
 * @param parsed    the parse result, {@code null} if the file could not be read
 * @param rawCsv    the original bytes (do not modify), {@code null} if the file could not be read
 * @param battleKey index of the battle in {@link ImportPlan#battles()}, {@code -1} if none
 */
public record PlannedFile(Path path, Status status, Error error, String detail, BattleLogParseResult parsed,
                          byte[] rawCsv, int battleKey) {

    /** What happens to the file. */
    public enum Status {
        READY,
        SKIPPED,
        ERROR
    }

    /** Why a file is not imported. */
    public enum Error {
        /** The file could not be read. */
        UNREADABLE,
        /** Not a Clash of Worlds battle log (no known column header row). */
        NOT_A_BATTLE_LOG,
        /** The file name was not recognized - date, guilds and direction unknown. */
        NO_HEAD_DATA,
        /** Another file of the same battle and direction is in the import and has more entries (append-only). */
        DUPLICATE_DIRECTION
    }

    /** The log direction, {@code null} if the file could not be read or has no head data. */
    public LogDirection direction() {
        return parsed == null ? null : parsed.log().header().direction();
    }

    /** Number of parse problems (0 if unread). */
    public int problemCount() {
        return parsed == null ? 0 : parsed.problems().size();
    }
}
