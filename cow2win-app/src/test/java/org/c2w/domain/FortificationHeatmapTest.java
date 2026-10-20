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
        assertEquals(Level.HIGH, heatmap.level("a"));
        assertEquals(Level.EMPTY, heatmap.level("b"));
    }

    @Test
    @DisplayName("Exactly 110 % and exactly 90 % are MEDIUM")
    void limitsIncluded() {
        Fortification a = fort("a", FortificationType.HERO, 1);
        Fortification b = fort("b", FortificationType.HERO, 1);
        Fortification c = fort("c", FortificationType.HERO, 1);
        hero(a, 11_000);
        hero(b, 9_000);
        hero(c, 10_000);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO, a, b, c);

        assertEquals(Level.MEDIUM, heatmap.level("a"), "110 %");
        assertEquals(Level.MEDIUM, heatmap.level("b"), "90 %");
        assertEquals(Level.MEDIUM, heatmap.level("c"), "100 %");
    }

    @Test
    @DisplayName("Just above 110 % is HIGH, just below 90 % is LOW")
    void beyondLimits() {
        Fortification a = fort("a", FortificationType.HERO, 1);
        Fortification b = fort("b", FortificationType.HERO, 1);
        Fortification c = fort("c", FortificationType.HERO, 1);
        hero(a, 11_001);
        hero(b, 8_999);
        hero(c, 10_000);

        FortificationHeatmap heatmap = heatmap(FortificationType.HERO, a, b, c);

        assertEquals(Level.HIGH, heatmap.level("a"));
        assertEquals(Level.LOW, heatmap.level("b"));
        assertEquals(Level.MEDIUM, heatmap.level("c"));
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

        assertEquals(Level.MEDIUM, heatmap.level("a"));
        assertEquals(Level.MEDIUM, heatmap.level("b"));
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
