package org.c2w.service.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.parse.BattleLogCheck;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One battle of an import (day + opponent) as {@code prepare} saw it.
 *
 * @param date             battle day
 * @param opponent         opponent guild
 * @param existingBattleId the battle already in the journal, {@code null} if new
 * @param result           result from the imported file(s)
 * @param status           running or finished, after this import
 * @param rankingPoints    ranking points from the imported file(s)
 * @param check            control calculation with the imported files plus the other direction from the journal
 * @param actions          per imported direction: new, replaces the stored log, or unchanged
 * @param seasonId         the season the battle will belong to without asking, {@code null} if asked or none
 * @param seasonQuestionId the season question deciding it, {@code null} if not asked
 * @param warnings         things to point out
 */
public record PlannedBattle(
        LocalDate date,
        GuildRef opponent,
        Integer existingBattleId,
        BattleResult result,
        BattleStatus status,
        Integer rankingPoints,
        BattleLogCheck.Result check,
        Map<LogDirection, LogAction> actions,
        Integer seasonId,
        String seasonQuestionId,
        List<Warning> warnings
) {
    public PlannedBattle {
        actions = actions == null ? Map.of() : Map.copyOf(actions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** What happens to the log of one direction. */
    public enum LogAction {
        /** No log of this direction stored yet. */
        NEW,
        /** Replaces the stored log of this direction (later export). */
        REPLACE,
        /** Identical to the stored log (same SHA-256) - nothing written. */
        UNCHANGED
    }

    /** Things the user should know about a battle. */
    public enum Warning {
        /** The battle is already finished in the journal, but the file shows a running state. */
        FINISHED_BATTLE_RUNNING_EXPORT,
        /** The ranking points do not follow from the points (incomplete export or missed rows). */
        RANKING_POINTS_MISMATCH,
        /** The imported file(s) have parse problems. */
        PARSE_PROBLEMS
    }
}
