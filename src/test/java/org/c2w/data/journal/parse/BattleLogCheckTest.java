package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.c2w.data.journal.parse.BattleLogCheck.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleLogCheckTest {

    @ParameterizedTest(name = "{0}: {3}/{4} -> {5}")
    @CsvSource({
            "14-09-2026, de, fr, 3793, 4207, -42",
            "17-09-2026, en, de, 4606, 2357, 975",
            "21-09-2026, fr, en, 3725, 6576, -286",
            "24-09-2026, de, de, 2783, 1782, 851",
            "28-09-2026, en, fr, 2643, 4346, -171"
    })
    void rankingPointsOfAllFinishedBattlesMatch(String day, String attackFolder, String defenseFolder,
                                                int own, int opponent, int rankingPoints) {
        BattleLog attack = BattleLogTestFiles.parse(attackFolder, day, LogDirection.ATTACK).log();
        BattleLog defense = BattleLogTestFiles.parse(defenseFolder, day, LogDirection.DEFENSE).log();

        BattleLogCheck.Result result = BattleLogCheck.check(attack, defense);

        assertEquals(own, result.ownPoints());
        assertEquals(opponent, result.opponentPoints());
        assertEquals(rankingPoints, result.rankingPoints());
        assertEquals(rankingPoints, result.expectedRankingPoints());
        assertEquals(Verdict.MATCHES, result.verdict());
        assertEquals(rankingPoints > 0 ? BattleResult.WIN : BattleResult.LOSS, result.status());
        assertEquals(List.of(), result.notes());
    }

    @Test
    void runningBattleIsNotChecked() {
        BattleLogCheck.Result result = BattleLogCheck.check(
                BattleLogTestFiles.parse("de", "01-10-2026", LogDirection.ATTACK).log(),
                BattleLogTestFiles.parse("de", "01-10-2026", LogDirection.DEFENSE).log());

        assertEquals(Verdict.RUNNING, result.verdict());
        assertEquals(BattleResult.RUNNING, result.status());
        assertEquals(4990, result.ownPoints());
        assertEquals(2879, result.opponentPoints());
        assertNull(result.expectedRankingPoints());
    }

    @Test
    void partialExportOfTheRunningBattleHasTheKnownSums() {
        BattleLogCheck.Result result = BattleLogCheck.check(
                BattleLogTestFiles.parse("partial", "01-10-2026", LogDirection.ATTACK).log(),
                BattleLogTestFiles.parse("partial", "01-10-2026", LogDirection.DEFENSE).log());

        assertEquals(4630, result.ownPoints());
        assertEquals(2809, result.opponentPoints());
        assertEquals(Verdict.RUNNING, result.verdict());
    }

    @Test
    void oneLogAloneIsNotCheckable() {
        BattleLogCheck.Result attackOnly = BattleLogCheck.check(
                BattleLogTestFiles.parse("de", "24-09-2026", LogDirection.ATTACK).log(), null);
        BattleLogCheck.Result defenseOnly = BattleLogCheck.check(
                null, BattleLogTestFiles.parse("en", "24-09-2026", LogDirection.DEFENSE).log());

        assertEquals(Verdict.NOT_CHECKABLE, attackOnly.verdict());
        assertEquals(2783, attackOnly.ownPoints());
        assertNull(attackOnly.opponentPoints());
        assertEquals(Verdict.NOT_CHECKABLE, defenseOnly.verdict());
        assertEquals(1782, defenseOnly.opponentPoints());
        assertEquals(BattleResult.WIN, defenseOnly.status());
    }

    @Test
    void logsOfTwoDifferentBattlesAreRejected() {
        BattleLog attack = BattleLogTestFiles.parse("de", "24-09-2026", LogDirection.ATTACK).log();
        BattleLog defense = BattleLogTestFiles.parse("de", "21-09-2026", LogDirection.DEFENSE).log();

        assertThrows(IllegalArgumentException.class, () -> BattleLogCheck.check(attack, defense));
    }

    @Test
    void logsInTheWrongDirectionAreRejected() {
        BattleLog defense = BattleLogTestFiles.parse("de", "24-09-2026", LogDirection.DEFENSE).log();

        assertThrows(IllegalArgumentException.class, () -> BattleLogCheck.check(defense, null));
        assertThrows(IllegalArgumentException.class, () -> BattleLogCheck.check(null, null));
    }

    @Test
    void mismatchWhenRowsAreMissing() {
        BattleLog attack = BattleLogTestFiles.parse("de", "24-09-2026", LogDirection.ATTACK).log();
        BattleLog defense = BattleLogTestFiles.parse("de", "24-09-2026", LogDirection.DEFENSE).log();
        BattleLog shortened = new BattleLog(attack.header(), attack.entries().subList(1, attack.entries().size()));

        BattleLogCheck.Result result = BattleLogCheck.check(shortened, defense);

        assertEquals(Verdict.MISMATCH, result.verdict());
        assertEquals(851, result.rankingPoints());
        assertNotEquals(851, result.expectedRankingPoints());
    }

    @Test
    void reportsUndefendedPointsThatDoNotAddUp() {
        BattleLogHeader header = new BattleLogHeader(LocalDate.of(2027, 1, 1), new GuildRef("A", 1, 2),
                new GuildRef("B", 3, 4), 0, BattleResult.RUNNING, LogDirection.ATTACK, "deutsch", "x.csv");
        BattleLog log = new BattleLog(header, List.of(
                new FortEvent("barracks", "Kaserne", FortEventKind.UNDEFENDED, 2, 3, "", 35, 2)));

        BattleLogCheck.Result result = BattleLogCheck.check(log, null);

        assertEquals(1, result.notes().size(), result.notes().toString());
    }

    @Test
    void formula() {
        assertEquals(851, BattleLogCheck.expectedRankingPoints(2783, 1782));
        assertEquals(751, BattleLogCheck.expectedRankingPoints(101, 100));
        assertEquals(750 + 1, BattleLogCheck.expectedRankingPoints(110, 100));
        assertEquals(-42, BattleLogCheck.expectedRankingPoints(3793, 4207));
        assertEquals(-1, BattleLogCheck.expectedRankingPoints(100, 101));
        assertEquals(-1, BattleLogCheck.expectedRankingPoints(100, 110));
        assertEquals(375, BattleLogCheck.expectedRankingPoints(100, 100));
    }
}
