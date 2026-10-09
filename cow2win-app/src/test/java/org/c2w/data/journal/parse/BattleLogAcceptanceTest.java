package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance table of the parser requirement (Weltenschlacht journal, phase 1):
 * per sample file the number of single fights (hero / titan), undefended rows
 * and positions, captured rows, points, units and fights with a defender buff.
 * Identical for DE, EN and FR - except the running battle of 01.10.2026, whose
 * German attack export was taken a little earlier (one fight less).
 */
class BattleLogAcceptanceTest {

    /** Hero/titan counts of a log without units: all single fights have no {@link TeamKind}. */
    private static final int NO_UNITS = -1;

    /**
     * folder, day, direction, fights, hero fights, titan fights (or {@link #NO_UNITS} for both),
     * undefended rows, undefended positions, captured rows, points, units, fights with buff.
     */
    static Stream<Arguments> acceptanceTable() {
        List<Arguments> rows = new ArrayList<>();
        for (String folder : List.of("de", "en", "fr")) {
            rows.add(row(folder, "14-09-2026", LogDirection.ATTACK, 97, 57, 40, 0, 0, 9, 3793, 1181, 42));
            rows.add(row(folder, "14-09-2026", LogDirection.DEFENSE, 82, NO_UNITS, NO_UNITS, 0, 0, 10, 4207, 0, 42));
            rows.add(row(folder, "17-09-2026", LogDirection.ATTACK, 90, 52, 38, 0, 0, 11, 4606, 1104, 44));
            rows.add(row(folder, "17-09-2026", LogDirection.DEFENSE, 86, 51, 35, 0, 0, 6, 2357, 1043, 26));
            rows.add(row(folder, "21-09-2026", LogDirection.ATTACK, 92, 54, 38, 0, 0, 9, 3725, 1129, 38));
            rows.add(row(folder, "21-09-2026", LogDirection.DEFENSE, 118, NO_UNITS, NO_UNITS, 0, 0, 17, 6576, 0, 62));
            rows.add(row(folder, "24-09-2026", LogDirection.ATTACK, 81, 43, 38, 0, 0, 6, 2783, 990, 41));
            rows.add(row(folder, "24-09-2026", LogDirection.DEFENSE, 43, NO_UNITS, NO_UNITS, 0, 0, 3, 1782, 0, 14));
            rows.add(row(folder, "28-09-2026", LogDirection.ATTACK, 74, 41, 33, 0, 0, 6, 2643, 899, 20));
            rows.add(row(folder, "28-09-2026", LogDirection.DEFENSE, 78, NO_UNITS, NO_UNITS, 0, 0, 11, 4346, 0, 43));
            rows.add(row(folder, "01-10-2026", LogDirection.DEFENSE, 61, NO_UNITS, NO_UNITS, 0, 0, 6, 2879, 0, 14));
        }
        rows.add(row("de", "01-10-2026", LogDirection.ATTACK, 71, 40, 31, 9, 15, 12, 4990, 865, 32));
        rows.add(row("en", "01-10-2026", LogDirection.ATTACK, 72, 41, 31, 9, 15, 12, 5025, 877, 33));
        rows.add(row("fr", "01-10-2026", LogDirection.ATTACK, 72, 41, 31, 9, 15, 12, 5025, 877, 33));
        // Earlier German export of the running battle; the table only gives the total number of fights (66).
        rows.add(row("partial", "01-10-2026", LogDirection.ATTACK, 66, 36, 30, 9, 15, 11, 4630, 805, 29));
        rows.add(row("partial", "01-10-2026", LogDirection.DEFENSE, 59, NO_UNITS, NO_UNITS, 0, 0, 6, 2809, 0, 14));
        return rows.stream();
    }

    private static Arguments row(String folder, String day, LogDirection direction, int fights, int heroFights,
                                 int titanFights, int undefendedRows, int undefendedPositions, int captured,
                                 int points, int units, int withBuff) {
        return Arguments.of(folder, day, direction, fights, heroFights, titanFights, undefendedRows,
                undefendedPositions, captured, points, units, withBuff);
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("acceptanceTable")
    void matchesTheAcceptanceTable(String folder, String day, LogDirection direction, int fights, int heroFights,
                                   int titanFights, int undefendedRows, int undefendedPositions, int captured,
                                   int points, int units, int withBuff) {
        BattleLogParseResult result = BattleLogTestFiles.parse(folder, day, direction);
        BattleLog log = result.log();

        assertEquals(List.of(), result.problems());
        assertEquals(direction, log.header().direction());
        assertEquals(fights, log.fights().size(), "single fights");
        if (heroFights == NO_UNITS) {
            assertTrue(log.fights().stream().noneMatch(Fight::hasUnits), "no units expected");
            assertTrue(log.fights().stream().allMatch(f -> f.teamKind() == null), "no team kind without units");
        } else {
            assertEquals(heroFights, count(log, TeamKind.HERO), "hero fights");
            assertEquals(titanFights, count(log, TeamKind.TITAN), "titan fights");
        }
        List<FortEvent> undefended = log.fortEvents().stream()
                .filter(e -> e.kind() == FortEventKind.UNDEFENDED).toList();
        assertEquals(undefendedRows, undefended.size(), "undefended rows");
        assertEquals(undefendedPositions, undefended.stream().mapToInt(FortEvent::freePositions).sum(),
                "undefended positions");
        assertEquals(captured, log.fortEvents().stream().filter(e -> e.kind() == FortEventKind.CAPTURED).count(),
                "captured rows");
        assertEquals(points, log.totalPoints(), "points");
        assertEquals(units, log.fights().stream()
                .mapToInt(f -> f.attacker().units().size() + f.defender().units().size()).sum(), "units");
        assertEquals(withBuff, log.fights().stream().filter(f -> f.defender().buff() != null).count(),
                "fights with buff");
    }

    @Test
    void thereAre38SampleFiles() {
        assertEquals(12, BattleLogTestFiles.files("de").size());
        assertEquals(12, BattleLogTestFiles.files("en").size());
        assertEquals(12, BattleLogTestFiles.files("fr").size());
        assertEquals(2, BattleLogTestFiles.files("partial").size());
    }

    /**
     * No parse problems in any sample file - which also means: every undefended
     * sentence was recognized via the vocabulary (the tolerant fallback always
     * reports a problem) - and every name, buff and color is known.
     */
    @Test
    void everyNameBuffAndColorOfAllSampleFilesIsKnown() {
        for (Path file : BattleLogTestFiles.allFiles()) {
            BattleLogParseResult result = BattleLogTestFiles.parse(file);
            String name = file.getParent().getFileName() + "/" + file.getFileName();
            assertEquals(List.of(), result.problems(), name);
            assertTrue(result.log().header().isComplete(), name);
            for (BattleLogEntry entry : result.log().entries()) {
                assertNotNull(entry.fortificationId(), name + " line " + entry.lineNumber());
                if (entry instanceof Fight fight) {
                    if (fight.defender().buff() != null) {
                        assertNotNull(fight.defender().buff().effect(), name + " line " + fight.lineNumber());
                        assertNotNull(fight.defender().buff().percent(), name + " line " + fight.lineNumber());
                    }
                    for (FightSide side : List.of(fight.attacker(), fight.defender())) {
                        for (FightUnit unit : side.units()) {
                            String where = name + " line " + fight.lineNumber() + " " + unit.name();
                            assertTrue(unit.isResolved(), where);
                            if (unit.kind() == UnitKind.HERO || unit.kind() == UnitKind.PET) {
                                assertNotNull(unit.color(), where);
                            }
                            if (unit.patronage() != null) {
                                assertNotNull(unit.patronage().petId(), where);
                            }
                        }
                    }
                }
            }
        }
    }

    private static long count(BattleLog log, TeamKind kind) {
        return log.fights().stream().filter(f -> f.teamKind() == kind).count();
    }
}
