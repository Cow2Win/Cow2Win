package org.c2w.data.journal.db;

import org.c2w.data.journal.LogDirection;

import java.time.LocalDateTime;

/**
 * Bookkeeping data of one stored log - what the battle detail shows next to
 * the parsed content.
 *
 * @param battleId      battle the log belongs to
 * @param direction     attack or defense log
 * @param language      game language of the export ({@code deutsch}, {@code english}, {@code francais})
 * @param fileName      original file name
 * @param importedAt    when the log was saved (or last parsed again)
 * @param parserVersion parser version that produced the stored rows
 * @param problemCount  number of parse problems
 * @param fightCount    number of single fights
 * @param pointsTotal   sum of the points of all rows
 */
public record LogInfo(
        int battleId,
        LogDirection direction,
        String language,
        String fileName,
        LocalDateTime importedAt,
        int parserVersion,
        int problemCount,
        int fightCount,
        int pointsTotal
) {
}
