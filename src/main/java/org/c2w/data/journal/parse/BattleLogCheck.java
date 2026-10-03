package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Control calculation for one battle: own points A = sum of the points column
 * of the attack log, opponent points B = the same sum of the defense log; the
 * ranking points in the file name follow from them (confirmed on all finished
 * sample battles):
 * <ul>
 *   <li>win: {@code 750 + ceil((A - B) / 10)}</li>
 *   <li>loss: {@code floor((A - B) / 10)}</li>
 *   <li>draw: {@code A = B} and 375</li>
 *   <li>running (0 ranking points): partial state, nothing to check</li>
 * </ul>
 * With only one of the two logs nothing can be calculated. Additionally checks
 * (report only) that every undefended row has 35 points per position.
 */
public final class BattleLogCheck {

    /** Outcome of the ranking points check. */
    public enum Verdict {
        /** The ranking points in the file name match the calculation. */
        MATCHES,
        /** They don't - an incomplete export or rows the parser missed. */
        MISMATCH,
        /** Running battle (0 ranking points) - partial state, not checked. */
        RUNNING,
        /** Only one log, or a file name that was not recognized. */
        NOT_CHECKABLE
    }

    /**
     * @param ownPoints             A, {@code null} without an attack log
     * @param opponentPoints        B, {@code null} without a defense log
     * @param status                result from the file name(s), {@code null} if unknown
     * @param rankingPoints         ranking points from the file name(s), {@code null} if unknown
     * @param expectedRankingPoints ranking points calculated from A and B, {@code null} if not calculable
     * @param verdict               outcome of the comparison
     * @param notes                 further findings (in English), e.g. undefended points that don't add up
     */
    public record Result(Integer ownPoints, Integer opponentPoints, BattleResult status, Integer rankingPoints,
                         Integer expectedRankingPoints, Verdict verdict, List<String> notes) {
        public Result {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    private BattleLogCheck() {
        // Utility class, no instantiation
    }

    /**
     * Checks the attack and/or defense log of the same battle (either may be
     * {@code null}, not both; the languages may differ).
     *
     * @throws IllegalArgumentException if both are {@code null}, a log is in the wrong
     *                                  direction, or the two logs belong to different battles
     *                                  (date or game guild ids differ)
     */
    public static Result check(BattleLog attackLog, BattleLog defenseLog) {
        if (attackLog == null && defenseLog == null) {
            throw new IllegalArgumentException("BattleLogCheck needs at least one log");
        }
        requireDirection(attackLog, LogDirection.ATTACK);
        requireDirection(defenseLog, LogDirection.DEFENSE);
        if (attackLog != null && defenseLog != null) {
            requireSameBattle(attackLog.header(), defenseLog.header());
        }

        List<String> notes = new ArrayList<>();
        Integer own = attackLog == null ? null : attackLog.totalPoints();
        Integer opponent = defenseLog == null ? null : defenseLog.totalPoints();
        checkUndefendedPoints(attackLog, notes);
        checkUndefendedPoints(defenseLog, notes);

        BattleLogHeader header = completeHeader(attackLog, defenseLog);
        if (header == null) {
            notes.add("file name not recognized - ranking points unknown");
            return new Result(own, opponent, null, null, null, Verdict.NOT_CHECKABLE, notes);
        }
        if (attackLog != null && defenseLog != null && attackLog.header().isComplete()
                && defenseLog.header().isComplete()
                && !Objects.equals(attackLog.header().rankingPoints(), defenseLog.header().rankingPoints())) {
            notes.add("ranking points differ between the file names: attack " + attackLog.header().rankingPoints()
                    + ", defense " + defenseLog.header().rankingPoints() + " - exported at different times?");
        }
        BattleResult status = header.result();
        Integer rankingPoints = header.rankingPoints();
        if (status == BattleResult.RUNNING) {
            return new Result(own, opponent, status, rankingPoints, null, Verdict.RUNNING, notes);
        }
        if (own == null || opponent == null) {
            return new Result(own, opponent, status, rankingPoints, null, Verdict.NOT_CHECKABLE, notes);
        }
        int expected = expectedRankingPoints(own, opponent);
        Verdict verdict = rankingPoints != null && expected == rankingPoints ? Verdict.MATCHES : Verdict.MISMATCH;
        return new Result(own, opponent, status, rankingPoints, expected, verdict, notes);
    }

    /** Ranking points of the own guild for own points A and opponent points B (see class Javadoc). */
    public static int expectedRankingPoints(int ownPoints, int opponentPoints) {
        int difference = ownPoints - opponentPoints;
        if (difference > 0) {
            return BattleResult.WIN_BASE_RANKING_POINTS + Math.ceilDiv(difference, 10);
        }
        if (difference < 0) {
            return Math.floorDiv(difference, 10);
        }
        return BattleResult.DRAW_RANKING_POINTS;
    }

    // --- private ---

    private static void requireDirection(BattleLog log, LogDirection expected) {
        if (log != null && log.header().direction() != null && log.header().direction() != expected) {
            throw new IllegalArgumentException("Expected a " + expected + " log: " + log.header().fileName());
        }
    }

    private static void requireSameBattle(BattleLogHeader attack, BattleLogHeader defense) {
        if (!attack.isComplete() || !defense.isComplete()) {
            return;
        }
        if (!attack.date().equals(defense.date())
                || attack.ownGuild().gameGuildId() != defense.ownGuild().gameGuildId()
                || attack.opponent().gameGuildId() != defense.opponent().gameGuildId()) {
            throw new IllegalArgumentException("The logs belong to different battles: "
                    + attack.fileName() + " / " + defense.fileName());
        }
    }

    /** The header to take ranking points and result from: the attack log's if complete, else the defense log's. */
    private static BattleLogHeader completeHeader(BattleLog attackLog, BattleLog defenseLog) {
        if (attackLog != null && attackLog.header().isComplete()) {
            return attackLog.header();
        }
        if (defenseLog != null && defenseLog.header().isComplete()) {
            return defenseLog.header();
        }
        return null;
    }

    private static void checkUndefendedPoints(BattleLog log, List<String> notes) {
        if (log == null) {
            return;
        }
        for (FortEvent event : log.fortEvents()) {
            if (event.kind() == FortEventKind.UNDEFENDED && event.freePositions() != null
                    && event.points() != event.freePositions() * BattleLogParser.UNDEFENDED_POINTS_PER_POSITION) {
                notes.add("line " + event.lineNumber() + " (" + event.fortificationName() + "): "
                        + event.freePositions() + " undefended positions but " + event.points() + " points (expected "
                        + event.freePositions() * BattleLogParser.UNDEFENDED_POINTS_PER_POSITION + ")");
            }
        }
    }
}
