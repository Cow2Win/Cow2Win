package org.c2w.data.journal.db;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.LogDirection;

import java.time.LocalDate;
import java.util.Set;

/**
 * One row of the battle list.
 *
 * @param battleId       database id
 * @param date           battle day
 * @param opponent       opponent guild (name and server as last seen)
 * @param result         result from the head data, {@code null} if unknown
 * @param status         running or finished
 * @param rankingPoints  ranking points from the head data, {@code null} if unknown
 * @param ownPoints      sum of the attack log, {@code null} without one
 * @param opponentPoints sum of the defense log, {@code null} without one
 * @param directions     the stored log directions
 * @param seasonId       season id, {@code null} if not assigned
 * @param seasonNumber   season number, {@code null} if not assigned
 */
public record BattleSummary(
        int battleId,
        LocalDate date,
        GuildRef opponent,
        BattleResult result,
        BattleStatus status,
        Integer rankingPoints,
        Integer ownPoints,
        Integer opponentPoints,
        Set<LogDirection> directions,
        Integer seasonId,
        Integer seasonNumber
) {
    public BattleSummary {
        directions = directions == null ? Set.of() : Set.copyOf(directions);
    }
}
