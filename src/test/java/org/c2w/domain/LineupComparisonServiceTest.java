package org.c2w.domain;

import org.c2w.data.model.Lineup;
import org.c2w.domain.LineupComparisonService.FortificationDiff;
import org.c2w.domain.LineupComparisonService.LineupComparison;
import org.c2w.domain.LineupComparisonService.Summary;
import org.c2w.domain.LineupComparisonService.TeamDiff;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LineupComparisonService}: per-team status, per-fortification diff and summary. */
class LineupComparisonServiceTest {

    private final LineupFixture fixture = new LineupFixture();

    private static TeamKey hero(String memberId, int index) {
        return new TeamKey(memberId, Lineup.TeamType.HERO, index);
    }

    @Test
    @DisplayName("every team gets the right status and power, unchanged teams are listed last")
    void teamDiffs() {
        LineupComparison comparison = LineupComparisonService.compare(fixture.before, fixture.after, fixture.guild);
        Map<TeamKey, TeamDiff> byTeam = comparison.teamDiffs().stream()
                .collect(Collectors.toMap(TeamDiff::teamKey, Function.identity()));

        assertEquals(4, byTeam.size());
        assertEquals(TeamDiff.Status.UNCHANGED, byTeam.get(hero("m1", 0)).status());
        assertEquals(TeamDiff.Status.MOVED, byTeam.get(hero("m1", 1)).status());
        assertEquals(fixture.fortA, byTeam.get(hero("m1", 1)).fortificationIdBefore());
        assertEquals(fixture.fortB, byTeam.get(hero("m1", 1)).fortificationIdAfter());
        assertEquals(TeamDiff.Status.ADDED, byTeam.get(hero("m2", 0)).status());
        assertEquals(3_000, byTeam.get(hero("m2", 0)).powerDiff());
        TeamDiff titan = byTeam.get(new TeamKey("m1", Lineup.TeamType.TITAN, 0));
        assertEquals(TeamDiff.Status.REMOVED, titan.status());
        assertEquals(-500, titan.powerDiff());

        List<TeamDiff> diffs = comparison.teamDiffs();
        assertEquals(TeamDiff.Status.UNCHANGED, diffs.get(diffs.size() - 1).status());
    }

    @Test
    @DisplayName("fortification diffs sum power and slots per fortification")
    void fortificationDiffs() {
        Map<String, FortificationDiff> byFort = LineupComparisonService
                .compare(fixture.before, fixture.after, fixture.guild).fortificationDiffs().stream()
                .collect(Collectors.toMap(FortificationDiff::fortificationId, Function.identity()));

        FortificationDiff a = byFort.get(fixture.fortA);
        assertEquals(3_000, a.powerBefore());
        assertEquals(1_000, a.powerAfter());
        assertEquals(2, a.filledSlotsBefore());
        assertEquals(1, a.filledSlotsAfter());
        assertEquals(2_000, byFort.get(fixture.fortB).powerDiff());
        assertEquals(3_000, byFort.get(fixture.fortC).powerDiff());
        assertEquals(-500, byFort.get(fixture.fortT).powerDiff());
    }

    @Test
    @DisplayName("the summary counts changes and splits power by side")
    void summary() {
        Summary summary = LineupComparisonService.compare(fixture.before, fixture.after, fixture.guild).summary();

        assertEquals(1, summary.addedCount());
        assertEquals(1, summary.removedCount());
        assertEquals(1, summary.movedCount());
        assertEquals(1, summary.unchangedCount());
        assertEquals(3, summary.changedCount());
        assertEquals(3_500, summary.totalPowerBefore());
        assertEquals(6_000, summary.totalPowerAfter());
        assertEquals(3_000, summary.heroPowerBefore());
        assertEquals(6_000, summary.heroPowerAfter());
        assertEquals(500, summary.titanPowerBefore());
        assertEquals(0, summary.titanPowerAfter());
    }

    @Test
    @DisplayName("comparing a lineup with itself finds no change")
    void identicalLineups() {
        LineupComparison comparison = LineupComparisonService.compare(fixture.before, fixture.before, fixture.guild);

        assertEquals(0, comparison.summary().changedCount());
        assertTrue(comparison.fortificationDiffs().stream().allMatch(FortificationDiff::isUnchanged));
    }
}
