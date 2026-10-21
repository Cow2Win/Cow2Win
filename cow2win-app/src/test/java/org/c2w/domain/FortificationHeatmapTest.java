package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.domain.FortificationHeatmap.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationHeatmap}: power per slot against the average of the fortification type. */
class FortificationHeatmapTest {

    private final List<GuildMember> members = new ArrayList<>();
    private final List<Lineup.Entry> entries = new ArrayList<>();

    @Test
    @DisplayName("An empty fortification is EMPTY; empty slots of a partly filled one count as 0")
    void emptyAndPartlyFilled() {
        Fortification a = fort("a", FortificationType.HERO, 4);
        Fortification b = fort("b", FortificationType.HERO, 4);
        hero(a, 1_000_000);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO, a, b);

        assertEquals(250_000, heatmap.rating("a").value(), 0.001);
        assertEquals(2.0, heatmap.rating("a").shareOfReference(), 0.0001, "reference 1 000 000 / 8 slots");
        assertEquals(Level.VERY_HIGH, heatmap.level("a"));
        assertEquals(Level.EMPTY, heatmap.level("b"));
    }

    @Test
    @DisplayName("Limits: above 110 % VERY_HIGH, 100 % to 110 % HIGH, 90 % to below 100 % MEDIUM, below 90 % LOW")
    void limits() {
        // Reference 70 000 / 7 slots = 10 000 per slot.
        Fortification above110 = fort("above110", FortificationType.HERO, 1);
        Fortification at110 = fort("at110", FortificationType.HERO, 1);
        Fortification above100 = fort("above100", FortificationType.HERO, 1);
        Fortification at100 = fort("at100", FortificationType.HERO, 1);
        Fortification below100 = fort("below100", FortificationType.HERO, 1);
        Fortification at90 = fort("at90", FortificationType.HERO, 1);
        Fortification below90 = fort("below90", FortificationType.HERO, 1);
        hero(above110, 11_001);
        hero(at110, 11_000);
        hero(above100, 10_001);
        hero(at100, 10_000);
        hero(below100, 9_999);
        hero(at90, 9_000);
        hero(below90, 8_999);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO,
                above110, at110, above100, at100, below100, at90, below90);

        assertEquals(Level.VERY_HIGH, heatmap.level("above110"), "just above 110 %");
        assertEquals(Level.HIGH, heatmap.level("at110"), "exactly 110 %");
        assertEquals(Level.HIGH, heatmap.level("above100"), "just above 100 %");
        assertEquals(Level.HIGH, heatmap.level("at100"), "exactly 100 %");
        assertEquals(Level.MEDIUM, heatmap.level("below100"), "just below 100 %");
        assertEquals(Level.MEDIUM, heatmap.level("at90"), "exactly 90 %");
        assertEquals(Level.LOW, heatmap.level("below90"), "just below 90 %");
    }

    @Test
    @DisplayName("Shares of the live lineup 'Deutscher Bund' (16.10.2026, heroes) get the expected levels")
    void liveLineupShares() {
        // Same shares as the live lineup, reference 7 000 / 7 slots = 1 000 per slot.
        Fortification heroBridge = fort("hero-bridge", FortificationType.HERO, 1);
        Fortification lighthouse = fort("lighthouse", FortificationType.HERO, 1);
        Fortification bastion = fort("bastion", FortificationType.HERO, 1);
        Fortification academy = fort("academy", FortificationType.HERO, 1);
        Fortification townHall = fort("town-hall", FortificationType.HERO, 1);
        Fortification fortress = fort("fortress", FortificationType.HERO, 1);
        Fortification balance = fort("balance", FortificationType.HERO, 1);
        hero(heroBridge, 1_124);
        hero(lighthouse, 1_067);
        hero(bastion, 1_020);
        hero(academy, 996);
        hero(townHall, 959);
        hero(fortress, 845);
        hero(balance, 989);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO,
                heroBridge, lighthouse, bastion, academy, townHall, fortress, balance);

        assertEquals(1.124, heatmap.rating("hero-bridge").shareOfReference(), 0.0001);
        assertEquals(Level.VERY_HIGH, heatmap.level("hero-bridge"), "112.4 %");
        assertEquals(Level.HIGH, heatmap.level("lighthouse"), "106.7 %");
        assertEquals(Level.HIGH, heatmap.level("bastion"), "102.0 %");
        assertEquals(Level.MEDIUM, heatmap.level("academy"), "99.6 %");
        assertEquals(Level.MEDIUM, heatmap.level("town-hall"), "95.9 %");
        assertEquals(Level.LOW, heatmap.level("fortress"), "84.5 %");
    }

    @Test
    @DisplayName("A partly filled fortification is rated like any other, not EMPTY")
    void partlyFilledNotEmpty() {
        Fortification a = fort("a", FortificationType.HERO, 2);
        Fortification b = fort("b", FortificationType.HERO, 2);
        hero(a, 10_000);
        hero(b, 10_000);
        hero(b, 10_000);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO, a, b);

        assertEquals(Level.LOW, heatmap.level("a"), "5 000 per slot against 7 500");
        assertEquals(Level.VERY_HIGH, heatmap.level("b"), "10 000 per slot against 7 500");
    }

    @Test
    @DisplayName("All fortifications empty: all EMPTY, no division by 0")
    void allEmpty() {
        Fortification a = fort("a", FortificationType.TITAN, 3);
        Fortification b = fort("b", FortificationType.TITAN, 5);

        FortificationHeatmap heatmap = heatmap(FortificationType.TITAN, a, b);

        assertEquals(Level.EMPTY, heatmap.level("a"));
        assertEquals(Level.EMPTY, heatmap.level("b"));
        assertEquals(0, heatmap.rating("a").shareOfReference());
    }

    @Test
    @DisplayName("Only fortifications of the given type count, also for the reference")
    void onlyGivenType() {
        Fortification a = fort("a", FortificationType.HERO, 1);
        Fortification b = fort("b", FortificationType.HERO, 1);
        Fortification t = fort("t", FortificationType.TITAN, 1);
        hero(a, 10_000);
        hero(b, 10_000);
        titan(t, 1_000_000);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO, a, b, t);

        assertEquals(Level.HIGH, heatmap.level("a"), "exactly 100 %");
        assertEquals(Level.HIGH, heatmap.level("b"), "exactly 100 %");
        assertNull(heatmap.rating("t"), "titan fortification not part of the hero heatmap");
    }

    private FortificationHeatmap heatmap(FortificationType type, Fortification... catalog) {
        Guild guild = new Guild("g1", "Guild", members);
        Lineup lineup = new Lineup("g1", "Guild", "", LocalDateTime.of(2026, 1, 1, 0, 0), entries);
        return FortificationHeatmap.of(type, List.of(catalog), lineup, guild);
    }

    private static Fortification fort(String id, FortificationType type, int capacity) {
        return new Fortification(id, type, capacity, 0, 0, 0, null, List.of(), 1);
    }

    private void hero(Fortification fort, int power) {
        String memberId = "m" + members.size();
        members.add(new GuildMember(memberId, memberId, List.of(new HeroTeam(memberId, 0,
                List.of(new Hero("h-" + memberId, List.of(Role.MAGE))), null, null, power, null)), List.of()));
        entries.add(new Lineup.Entry(fort.id(), memberId, Lineup.TeamType.HERO, 0));
    }

    private void titan(Fortification fort, int power) {
        String memberId = "m" + members.size();
        members.add(new GuildMember(memberId, memberId, List.of(),
                List.of(new TitanTeam(memberId, 0, List.of(new Titan("ignis", TitanElement.FIRE)), power, null))));
        entries.add(new Lineup.Entry(fort.id(), memberId, Lineup.TeamType.TITAN, 0));
    }
}
