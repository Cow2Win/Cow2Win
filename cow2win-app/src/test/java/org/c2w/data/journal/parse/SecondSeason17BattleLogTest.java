package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The second battle of season 17 (15.10.2026, Deutscher Bund - YAMATO, defeat -120):
 * head data and number of fights of both logs in DE, EN, FR and ES (numbers taken from
 * the exports).
 */
class SecondSeason17BattleLogTest {

    private static final String DAY = "15-10-2026";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"de", "en", "es", "fr"})
    void headDataAndFightCounts(String folder) {
        for (LogDirection direction : LogDirection.values()) {
            BattleLogParseResult result = BattleLogTestFiles.parse(folder, DAY, direction);
            BattleLogHeader header = result.log().header();
            String where = folder + " " + direction;

            assertEquals(List.of(), result.problems(), where);
            assertEquals(LocalDate.of(2026, 10, 15), header.date(), where);
            assertEquals(new GuildRef("Deutscher Bund", 133, 193861), header.ownGuild(), where);
            assertEquals(new GuildRef("YAMATO", 40, 172935), header.opponent(), where);
            assertEquals(BattleResult.LOSS, header.result(), where);
            assertEquals(Integer.valueOf(-120), header.rankingPoints(), where);
            assertEquals(direction, header.direction(), where);
        }

        BattleLog attack = BattleLogTestFiles.parse(folder, DAY, LogDirection.ATTACK).log();
        assertEquals(82, attack.fights().size());
        assertEquals(46, attack.fights().stream().filter(f -> f.teamKind() == TeamKind.HERO).count());
        assertEquals(36, attack.fights().stream().filter(f -> f.teamKind() == TeamKind.TITAN).count());
        assertEquals(3182, attack.totalPoints());

        BattleLog defense = BattleLogTestFiles.parse(folder, DAY, LogDirection.DEFENSE).log();
        assertEquals(92, defense.fights().size());
        assertTrue(defense.fights().stream().noneMatch(Fight::hasUnits), "defense log without units");
        assertEquals(4377, defense.totalPoints());
    }

    @Test
    void pointsOfBothLogsGiveTheRankingPointsOfTheFileName() {
        int attack = BattleLogTestFiles.parse("de", DAY, LogDirection.ATTACK).log().totalPoints();
        int defense = BattleLogTestFiles.parse("de", DAY, LogDirection.DEFENSE).log().totalPoints();

        assertEquals(-120, BattleLogCheck.expectedRankingPoints(attack, defense));
    }
}
