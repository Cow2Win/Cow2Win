package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The most important parser test: the language versions of an export are
 * line-identical (same rows, same numbers, only the texts translated), so DE,
 * EN and FR must yield the same language-neutral result - same head data
 * (except language and file name), same entries in the same order, same
 * fortification and unit ids, unit kinds, colors, buffs, numbers and results.
 * Exception: the German attack export of the running battle of 01.10.2026 was
 * taken a little earlier and is a prefix of EN/FR.
 *
 * <p>Also covers append-only: the earlier export under {@code partial/} is a
 * prefix of the later German export of the same battle.
 */
class BattleLogLanguageComparisonTest {

    static Stream<Arguments> battlesAndDirections() {
        List<Arguments> result = new ArrayList<>();
        for (String day : BattleLogTestFiles.BATTLE_DAYS) {
            for (LogDirection direction : LogDirection.values()) {
                result.add(Arguments.of(day, direction));
            }
        }
        return result.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("battlesAndDirections")
    void germanEnglishAndFrenchYieldTheSameResult(String day, LogDirection direction) {
        BattleLog de = BattleLogTestFiles.parse("de", day, direction).log();
        BattleLog en = BattleLogTestFiles.parse("en", day, direction).log();
        BattleLog fr = BattleLogTestFiles.parse("fr", day, direction).log();

        assertEquals("deutsch", de.header().language());
        assertEquals("english", en.header().language());
        assertEquals("francais", fr.header().language());
        assertEquals(neutral(de.header()), neutral(en.header()));
        assertEquals(neutral(de.header()), neutral(fr.header()));
        assertEquals(neutral(en), neutral(fr), "EN and FR are always exported at the same time");

        boolean germanIsEarlier = day.equals("01-10-2026") && direction == LogDirection.ATTACK;
        if (germanIsEarlier) {
            assertPrefix(neutral(de), neutral(en));
            assertEquals(71, de.fights().size());
            assertEquals(72, en.fights().size());
            assertEquals(en.entries().size(), de.entries().size() + 1, "EN/FR have exactly one fight more");
        } else {
            assertEquals(neutral(de), neutral(en));
        }
    }

    @Test
    void partialExportIsAPrefixOfTheLaterGermanExport() throws IOException {
        for (LogDirection direction : LogDirection.values()) {
            var partialFile = BattleLogTestFiles.file("partial", "01-10-2026", direction);
            var laterFile = BattleLogTestFiles.file("de", "01-10-2026", direction);

            List<String> partialLines = Files.readAllLines(partialFile, StandardCharsets.UTF_8);
            List<String> laterLines = Files.readAllLines(laterFile, StandardCharsets.UTF_8);
            assertTrue(partialLines.size() < laterLines.size(), direction.toString());
            assertEquals(partialLines, laterLines.subList(0, partialLines.size()), direction + ": raw lines");

            BattleLog partial = BattleLogTestFiles.parse(partialFile).log();
            BattleLog later = BattleLogTestFiles.parse(laterFile).log();
            assertEquals(neutral(partial.header()), neutral(later.header()));
            // Both partial files end with a complete entry, so the parsed entries are a prefix as well.
            assertPrefix(neutral(partial), neutral(later));
            assertTrue(partial.entries().size() < later.entries().size(), direction.toString());
        }
    }

    // --- language-neutral view (raw texts and language left out) ---

    private static String neutral(BattleLogHeader h) {
        return String.join("|", String.valueOf(h.date()), String.valueOf(h.ownGuild()), String.valueOf(h.opponent()),
                String.valueOf(h.rankingPoints()), String.valueOf(h.result()), String.valueOf(h.direction()));
    }

    static List<String> neutral(BattleLog log) {
        return log.entries().stream().map(BattleLogLanguageComparisonTest::neutral).toList();
    }

    private static String neutral(BattleLogEntry entry) {
        return switch (entry) {
            case FortEvent e -> "FORT line=" + e.lineNumber() + " " + e.fortificationId() + " " + e.kind()
                    + " " + e.freePositions() + "/" + e.totalPositions() + " points=" + e.points();
            case Fight f -> "FIGHT line=" + f.lineNumber() + " " + f.fortificationId() + "#" + f.position()
                    + " " + f.teamKind() + " attackerWins=" + f.attackerWins() + " points=" + f.points()
                    + " attacker=" + neutral(f.attacker()) + " defender=" + neutral(f.defender());
        };
    }

    /** Player names are not translated, so they are part of the neutral view. */
    private static String neutral(FightSide side) {
        StringBuilder sb = new StringBuilder("[" + side.playerName() + " " + side.level() + " " + side.teamPower());
        if (side.buff() != null) {
            sb.append(" buff=").append(side.buff().effect()).append(" ").append(side.buff().percent());
        }
        for (FightUnit u : side.units()) {
            sb.append(" {").append(u.kind()).append(" ").append(u.catalogId()).append(" ").append(u.totemElement())
                    .append(" ").append(u.color()).append("+").append(u.colorLevel())
                    .append(" ").append(u.stars()).append("* L").append(u.level()).append(" P").append(u.power())
                    .append(" ").append(u.damageDealt()).append("/").append(u.damageTaken()).append("/").append(u.healing());
            if (u.patronage() != null) {
                sb.append(" patronage=").append(u.patronage().petId()).append(" ").append(u.patronage().power());
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }

    private static void assertPrefix(List<String> prefix, List<String> full) {
        assertTrue(prefix.size() <= full.size(), "prefix is longer than the full list");
        assertEquals(prefix, full.subList(0, prefix.size()));
    }
}
