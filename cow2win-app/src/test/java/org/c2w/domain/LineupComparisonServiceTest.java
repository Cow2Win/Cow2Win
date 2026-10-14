package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.LineupComparisonService.FortificationDiff;
import org.c2w.domain.LineupComparisonService.FortificationValues;
import org.c2w.domain.LineupComparisonService.LineupComparison;
import org.c2w.domain.LineupComparisonService.Summary;
import org.c2w.domain.LineupComparisonService.TeamDiff;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    @DisplayName("compareFortifications with the same guild on both sides matches the fortification diffs of compare")
    void compareFortificationsSameGuild() {
        Map<String, FortificationDiff> diffs = LineupComparisonService
                .compare(fixture.before, fixture.after, fixture.guild).fortificationDiffs().stream()
                .collect(Collectors.toMap(FortificationDiff::fortificationId, Function.identity()));

        List<FortificationValues> values = LineupComparisonService.compareFortifications(
                fixture.before, fixture.guild, fixture.after, fixture.guild, FortificationType.HERO);

        for (FortificationValues value : values) {
            FortificationDiff diff = diffs.get(value.fortificationId());
            if (diff == null) {
                assertEquals(0, value.powerLeft() + value.powerRight() + value.filledLeft() + value.filledRight(),
                        value.fortificationId() + " is empty in both lineups");
                continue;
            }
            assertEquals(diff.powerBefore(), value.powerLeft(), value.fortificationId());
            assertEquals(diff.powerAfter(), value.powerRight(), value.fortificationId());
            assertEquals(diff.buffPercentBefore(), value.buffPercentLeft(), value.fortificationId());
            assertEquals(diff.buffPercentAfter(), value.buffPercentRight(), value.fortificationId());
            assertEquals(diff.filledSlotsBefore(), value.filledLeft(), value.fortificationId());
            assertEquals(diff.filledSlotsAfter(), value.filledRight(), value.fortificationId());
        }
    }

    @Test
    @DisplayName("compareFortifications resolves the right lineup with its own guild")
    void compareFortificationsOtherGuild() {
        GuildMember stranger = new GuildMember("x1", "Stranger", List.of(new HeroTeam("x1", 0,
                List.of(new Hero("h-x1", List.of(Role.MAGE))), null, null, 7_000, null)), List.of());
        Guild otherGuild = new Guild("g2", "Other", List.of(stranger));
        Lineup otherLineup = new Lineup("g2", "Other", "", LocalDateTime.of(2026, 1, 1, 0, 0),
                List.of(new Lineup.Entry(fixture.fortA, "x1", Lineup.TeamType.HERO, 0)));

        FortificationValues a = byId(LineupComparisonService.compareFortifications(
                fixture.before, fixture.guild, otherLineup, otherGuild, FortificationType.HERO)).get(fixture.fortA);
        assertEquals(3_000, a.powerLeft());
        assertEquals(7_000, a.powerRight(), "x1 does not exist in the left guild, but in the right one");
        assertEquals(1, a.filledRight());

        FortificationValues withWrongGuild = byId(LineupComparisonService.compareFortifications(
                fixture.before, fixture.guild, otherLineup, fixture.guild, FortificationType.HERO)).get(fixture.fortA);
        assertEquals(0, withWrongGuild.powerRight(), "with the left guild, x1 cannot be resolved");
    }

    @Test
    @DisplayName("compareFortifications lists every fortification of the type in map order; no right side gives 0")
    void compareFortificationsTypeOrderAndMissingRight() {
        List<String> expected = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.TITAN)
                .sorted(Comparator.comparingInt(Fortification::row).thenComparingInt(Fortification::column))
                .map(Fortification::id)
                .toList();

        List<FortificationValues> values = LineupComparisonService.compareFortifications(
                fixture.before, fixture.guild, null, null, FortificationType.TITAN);

        assertEquals(expected, values.stream().map(FortificationValues::fortificationId).toList());
        assertTrue(values.stream().allMatch(v -> v.powerRight() == 0 && v.buffPercentRight() == 0 && v.filledRight() == 0));
        assertEquals(500, byId(values).get(fixture.fortT).powerLeft());
    }

    @Test
    @DisplayName("a fortification without a buff has hasBuff false and a buff of 0")
    void compareFortificationsWithoutBuff() {
        Fortification noBuff = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO && f.buff() == null)
                .findFirst().orElseThrow();
        Lineup lineup = new Lineup("g1", "Guild", "", LocalDateTime.of(2026, 1, 1, 0, 0),
                List.of(new Lineup.Entry(noBuff.id(), "m2", Lineup.TeamType.HERO, 0)));

        FortificationValues value = byId(LineupComparisonService.compareFortifications(
                lineup, fixture.guild, lineup, fixture.guild, FortificationType.HERO)).get(noBuff.id());

        assertFalse(value.hasBuff());
        assertEquals(0, value.buffPercentLeft());
        assertEquals(0, value.buffPercentRight());
        assertEquals(3_000, value.powerLeft());
        assertEquals(noBuff.capacity(), value.capacity());
    }

    private static Map<String, FortificationValues> byId(List<FortificationValues> values) {
        return values.stream().collect(Collectors.toMap(FortificationValues::fortificationId, Function.identity()));
    }

    @Test
    @DisplayName("comparing a lineup with itself finds no change")
    void identicalLineups() {
        LineupComparison comparison = LineupComparisonService.compare(fixture.before, fixture.before, fixture.guild);

        assertEquals(0, comparison.summary().changedCount());
        assertTrue(comparison.fortificationDiffs().stream().allMatch(FortificationDiff::isUnchanged));
    }
}
