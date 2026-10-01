package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The team combo bonus of the hero-team CowScore: +1.25 % once if at least
 * one active {@link TeamCombo} matches, independent of the fortification.
 */
class TeamScoreCalculatorComboTest {

    private static final double EPS = 1e-9;

    private static final Fortification FOUNDRY = new Fortification("foundry", FortificationType.HERO, 5, 0, 0, 0,
            new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification BRIDGE = new Fortification("heros-bridge", FortificationType.HERO, 6, 0, 0, 0,
            null, List.of(), 0);

    private static final TeamCombo SEBASTIAN_NEBULA = combo("sebastian-nebula", null, "sebastian", "nebula");
    private static final TeamCombo KRISTA_LARS = combo("krista-lars", null, "krista", "lars");
    private static final TeamCombo AUGUSTUS_ORION_DORIAN = combo("augustus-orion-dorian", null,
            "augustus", "orion", "dorian");
    private static final TeamCombos COMBOS = new TeamCombos(List.of(SEBASTIAN_NEBULA, KRISTA_LARS, AUGUSTUS_ORION_DORIAN));

    private static TeamCombo combo(String id, LocalDate deactivated, String... heroIds) {
        return new TeamCombo(id, id, List.of(heroIds), ComboSource.C2W, deactivated);
    }

    /** A team of MAGE heroes (no role match at the foundry) without pet/war flag. */
    private static HeroTeam team(int power, WarFlag warFlag, String... heroIds) {
        List<Hero> heroes = java.util.Arrays.stream(heroIds).map(id -> new Hero(id, List.of(Role.MAGE))).toList();
        return new HeroTeam("m1", 0, heroes, null, warFlag, power, null);
    }

    @AfterEach
    void resetRegisteredCombos() {
        TeamScoreCalculator.setHeroCombos(null);
    }

    @Test
    @DisplayName("no combo matches: no combo bonus")
    void noCombo() {
        HeroTeam team = team(1_000_000, null, "sebastian", "krista", "orion");

        assertTrue(TeamScoreCalculator.matchingCombos(team, COMBOS).isEmpty());
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team, FOUNDRY, COMBOS).comboPercent(), EPS);
        assertEquals(10.0, TeamScoreCalculator.scoreFor(team, FOUNDRY, COMBOS).total(), EPS);
    }

    @Test
    @DisplayName("one combo matches: +1.25 %, at every fortification")
    void oneCombo() {
        HeroTeam team = team(1_000_000, null, "sebastian", "nebula", "orion");

        assertEquals(List.of(SEBASTIAN_NEBULA), TeamScoreCalculator.matchingCombos(team, COMBOS));
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team, FOUNDRY, COMBOS).comboPercent(), EPS);
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team, BRIDGE, COMBOS).comboPercent(), EPS);
        assertEquals(10.0 * 1.0125, TeamScoreCalculator.scoreFor(team, FOUNDRY, COMBOS).total(), EPS);
        assertEquals(10.0 * 1.0125, TeamScoreCalculator.scoreFor(team, BRIDGE, COMBOS).total(), EPS);
    }

    @Test
    @DisplayName("two combos match: both are reported, but the bonus counts only once")
    void twoCombosCountOnce() {
        HeroTeam team = team(1_000_000, null, "sebastian", "nebula", "krista", "lars", "orion");

        assertEquals(List.of(SEBASTIAN_NEBULA, KRISTA_LARS), TeamScoreCalculator.matchingCombos(team, COMBOS));
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team, FOUNDRY, COMBOS).comboPercent(), EPS);
        assertEquals(10.0 * 1.0125, TeamScoreCalculator.scoreFor(team, FOUNDRY, COMBOS).total(), EPS);
    }

    @Test
    @DisplayName("a combo only partly in the team does not count")
    void partialCombo() {
        HeroTeam team = team(1_000_000, null, "augustus", "orion", "sebastian");

        assertTrue(TeamScoreCalculator.matchingCombos(team, COMBOS).isEmpty());
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team, FOUNDRY, COMBOS).comboPercent(), EPS);
    }

    @Test
    @DisplayName("a deactivated combo does not count")
    void deactivatedCombo() {
        TeamCombos combos = new TeamCombos(List.of(combo("sebastian-nebula", LocalDate.of(2026, 9, 30),
                "sebastian", "nebula")));
        HeroTeam team = team(1_000_000, null, "sebastian", "nebula");

        assertTrue(TeamScoreCalculator.matchingCombos(team, combos).isEmpty());
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team, FOUNDRY, combos).comboPercent(), EPS);
        assertEquals(1.0, TeamScoreCalculator.sortScore(team(100_000, null, "sebastian", "nebula"), combos), EPS);
    }

    @Test
    @DisplayName("sortScore: power / 100 000 x (1 + 0.6 % if war flag + 1.25 % if combo)")
    void sortScore() {
        assertEquals(1.0, TeamScoreCalculator.sortScore(team(100_000, null, "sebastian"), COMBOS), EPS);
        assertEquals(1.0125, TeamScoreCalculator.sortScore(team(100_000, null, "sebastian", "nebula"), COMBOS), EPS);
        assertEquals(1.0185, TeamScoreCalculator.sortScore(
                team(100_000, new WarFlag("flag-frost"), "sebastian", "nebula"), COMBOS), EPS);
    }

    @Test
    @DisplayName("memberScores holds five bonus components, the combo one last; total = powerTerm + sum")
    void breakdownHasFiveComponents() {
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(
                team(1_000_000, null, "krista", "lars"), FOUNDRY, COMBOS);

        assertEquals(5, breakdown.memberScores().size());
        assertEquals(10.0 * 1.25 / 100, breakdown.memberScores().get(4), EPS);
        assertEquals(breakdown.total(),
                breakdown.powerTerm() + breakdown.memberScores().stream().mapToDouble(Double::doubleValue).sum(), EPS);
    }

    @Test
    @DisplayName("the overloads without combos use the registered ones (none by default)")
    void registeredCombos() {
        HeroTeam team = team(100_000, null, "sebastian", "nebula");
        assertEquals(1.0, team.sortScore(), EPS, "no combos registered");

        TeamScoreCalculator.setHeroCombos(COMBOS);

        assertEquals(List.of(SEBASTIAN_NEBULA), TeamScoreCalculator.matchingCombos(team));
        assertEquals(1.0125, team.sortScore(), EPS);
        assertEquals(1.0125, TeamScoreCalculator.scoreFor(team, BRIDGE).total(), EPS);
    }
}
