package org.c2w.data.journal.parse;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.LogDirection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BattleLogFileNameTest {

    private static List<BattleLogVocabulary> vocabularies;

    private static final GuildRef OWN = new GuildRef("Deutscher Bund", 133, 193861);

    /** Battle day -> opponent, ranking points and result. */
    private static final Map<String, Object[]> BATTLES = Map.of(
            "14-09-2026", new Object[]{new GuildRef("Elysium", 199, 238926), -42, BattleResult.LOSS},
            "17-09-2026", new Object[]{new GuildRef("RAZEM", 290, 316390), 975, BattleResult.WIN},
            "21-09-2026", new Object[]{new GuildRef("Rakuen", 62, 265237), -286, BattleResult.LOSS},
            "24-09-2026", new Object[]{new GuildRef("Das Schwarze Auge", 79, 114834), 851, BattleResult.WIN},
            "28-09-2026", new Object[]{new GuildRef("Rebel Alliance", 42, 64942), -171, BattleResult.LOSS},
            "01-10-2026", new Object[]{new GuildRef("Союз", 59, 81447), 0, BattleResult.RUNNING});

    @BeforeAll
    static void loadVocabularies() {
        vocabularies = BattleLogVocabulary.loadAll();
    }

    @Test
    void parsesAllSampleFileNames() {
        for (Map.Entry<String, String> folder : BattleLogTestFiles.LANGUAGE_BY_FOLDER.entrySet()) {
            for (String day : BattleLogTestFiles.BATTLE_DAYS) {
                for (LogDirection direction : LogDirection.values()) {
                    Path file = BattleLogTestFiles.file(folder.getKey(), day, direction);
                    BattleLogFileName name = parse(file.getFileName().toString()).orElseThrow(
                            () -> new AssertionError("not recognized: " + file.getFileName()));
                    Object[] battle = BATTLES.get(day);
                    String where = folder.getKey() + " " + day + " " + direction;

                    assertEquals(LocalDate.of(Integer.parseInt(day.substring(6)), Integer.parseInt(day.substring(3, 5)),
                            Integer.parseInt(day.substring(0, 2))), name.date(), where);
                    assertEquals(OWN, name.ownGuild(), where);
                    assertEquals(battle[0], name.opponent(), where);
                    assertEquals(battle[1], name.rankingPoints(), where);
                    assertEquals(battle[2], name.result(), where);
                    assertEquals(direction, name.direction(), where);
                    assertEquals(folder.getValue(), name.language(), where);
                    assertEquals(List.of(), name.problems(), where);
                }
            }
        }
    }

    @Test
    void runningBattleUsesTheDrawWordOfEachLanguage() {
        assertEquals("Unentschieden", resultWord("de"));
        assertEquals("Draw", resultWord("en"));
        assertEquals("Égalité", resultWord("fr"));
    }

    @Test
    void ignoresTheDownloadSuffix() {
        Path file = BattleLogTestFiles.file("de", "14-09-2026", LogDirection.ATTACK);
        assertTrue(file.getFileName().toString().endsWith("Angriffs-Log (1).csv"), "sample file with suffix");

        BattleLogFileName name = parse(file.getFileName().toString()).orElseThrow();

        assertEquals(LogDirection.ATTACK, name.direction());
        assertEquals(-42, name.rankingPoints());
    }

    @Test
    void stripsAPath() {
        BattleLogFileName name = parse("C:\\Downloads\\24-09-2026 Deutscher Bund Server 133 (193861) - "
                + "Das Schwarze Auge Server 79 (114834) Sieg +851 Rangpunkte Verteidigungs-Log.csv").orElseThrow();

        assertEquals(LogDirection.DEFENSE, name.direction());
        assertEquals("Das Schwarze Auge", name.opponent().name());
    }

    @Test
    void renamedFileIsNotRecognized() {
        assertTrue(parse("battle.csv").isEmpty());
        assertTrue(parse("31-02-2026 A Server 1 (2) - B Server 3 (4) Sieg +800 Rangpunkte Angriffs-Log.csv").isEmpty(),
                "invalid date");
        assertTrue(parse(null).isEmpty());
    }

    @Test
    void guildNameWithServerWordAndDashIsAnchoredOnTheId() {
        BattleLogFileName name = parse("01-01-2027 Server - Team Server 7 (11) - Ab - Cd Server 8 (22) "
                + "Defeat -5 ranking pts. Attack Log.csv").orElseThrow();

        assertEquals(new GuildRef("Server - Team", 7, 11), name.ownGuild());
        assertEquals(new GuildRef("Ab - Cd", 8, 22), name.opponent());
    }

    @Test
    void realDrawIsRecognizedByItsRankingPoints() {
        BattleLogFileName name = parse("01-01-2027 A Server 1 (2) - B Server 3 (4) Unentschieden +375 Rangpunkte "
                + "Angriffs-Log.csv").orElseThrow();

        assertEquals(BattleResult.DRAW, name.result());
        assertEquals(List.of(), name.problems());
    }

    @Test
    void reportsAResultWordThatContradictsTheRankingPoints() {
        BattleLogFileName name = parse("01-01-2027 A Server 1 (2) - B Server 3 (4) Sieg -12 Rangpunkte "
                + "Angriffs-Log.csv").orElseThrow();

        assertEquals(BattleResult.LOSS, name.result(), "the ranking points decide");
        assertEquals(1, name.problems().size(), name.problems().toString());
    }

    @Test
    void reportsImpossibleRankingPoints() {
        BattleLogFileName name = parse("01-01-2027 A Server 1 (2) - B Server 3 (4) Sieg +500 Rangpunkte "
                + "Angriffs-Log.csv").orElseThrow();

        assertNull(name.result());
        assertEquals(1, name.problems().size(), name.problems().toString());
    }

    @Test
    void acceptsTheTypographicApostropheInFrenchWords() {
        BattleLogFileName name = parse("01-01-2027 A Serveur 1 (2) - B Serveur 3 (4) Défaite -5 pts de classement "
                + "Journal d’attaque.csv").orElseThrow();

        assertEquals(LogDirection.ATTACK, name.direction());
        assertEquals("francais", name.language());
    }

    private static String resultWord(String folder) {
        Path file = BattleLogTestFiles.file(folder, "01-10-2026", LogDirection.ATTACK);
        return parse(file.getFileName().toString()).orElseThrow().resultWord();
    }

    private static Optional<BattleLogFileName> parse(String fileName) {
        return BattleLogFileName.parse(fileName, vocabularies);
    }
}
