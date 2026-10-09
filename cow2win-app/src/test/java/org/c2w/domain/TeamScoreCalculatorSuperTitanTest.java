package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The super titan bonus of the titan-team CowScore: a fixed
 * {@link TeamScoreCalculator#SUPER_TITAN_PERCENT} (0.6 %) per super titan in the team,
 * independent of the fortification and of marks.
 */
class TeamScoreCalculatorSuperTitanTest {

    private static final double EPS = 1e-9;

    private static final Fortification FIRE_FORT = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
            new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification PLAIN_FORT = new Fortification("plain-fort", FortificationType.TITAN, 5, 0, 0, 0,
            null, List.of(), 0);

    /** The five super titans of the catalog - all WATER here, so none matches the fire fort's buff. */
    private static final List<Titan> SUPER_TITANS = List.of(superTitan("solaris", TitanElement.WATER),
            superTitan("araji", TitanElement.WATER), superTitan("eden", TitanElement.WATER),
            superTitan("hyperion", TitanElement.WATER), superTitan("tenebris", TitanElement.WATER));

    @BeforeEach
    void defaultBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    @AfterEach
    void reset() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
        TeamScoreCalculator.setTitanCombos(null);
    }

    private static Titan superTitan(String id, TitanElement element) {
        return new Titan(id, element, List.of(), true, null, null);
    }

    private static Titan titan(String id, TitanElement element) {
        return new Titan(id, element);
    }

    private static TitanTeam team(int power, List<Titan> titans) {
        return new TitanTeam("m1", 0, titans, power, null, Set.of());
    }

    private static double superTitanPercent(TitanTeam team, Fortification fortification) {
        return TeamScoreCalculator.titanBonus(team, fortification, TeamCombos.NONE).superTitanPercent();
    }

    @Test
    @DisplayName("0, 1 and 5 super titans: 0 / 0.6 / 3.0 %")
    void perSuperTitan() {
        TitanTeam none = team(1_000_000, List.of(titan("sigurd", TitanElement.WATER), titan("nova", TitanElement.WATER)));
        TitanTeam one = team(1_000_000, List.of(SUPER_TITANS.get(0), titan("sigurd", TitanElement.WATER)));
        TitanTeam five = team(1_000_000, SUPER_TITANS);

        assertEquals(0.0, superTitanPercent(none, PLAIN_FORT), EPS);
        assertEquals(0.6, superTitanPercent(one, PLAIN_FORT), EPS);
        assertEquals(3.0, superTitanPercent(five, PLAIN_FORT), EPS);
        assertEquals(3.0, TeamScoreCalculator.superTitanPercent(five), EPS);
        assertEquals(10.0 * 1.03, TeamScoreCalculator.scoreFor(five, PLAIN_FORT, TeamCombos.NONE).total(), EPS);
    }

    @Test
    @DisplayName("independent of the fortification and of marks - also for a negatively marked super titan")
    void independentOfFortificationAndMarks() {
        Titan negative = superTitan("eden", TitanElement.WATER)
                .withFortMarks(new FortMarks(Map.of("plain-fort", FortMark.NEGATIVE)));
        TitanTeam team = team(1_000_000, List.of(negative, titan("sigurd", TitanElement.WATER)));

        assertEquals(0.6, superTitanPercent(team, PLAIN_FORT), EPS);
        assertEquals(0.6, superTitanPercent(team, FIRE_FORT), EPS);
        assertEquals(-1.25, TeamScoreCalculator.titanBonus(team, PLAIN_FORT, TeamCombos.NONE).relationPercent(), EPS,
                "the mark still counts as relation");
    }

    @Test
    @DisplayName("in addition to the element buff: a matching super titan counts for both")
    void inAdditionToElementBuff() {
        TitanTeam team = team(1_000_000, List.of(superTitan("solaris", TitanElement.FIRE), titan("ignis", TitanElement.FIRE)));
        TeamScoreCalculator.TitanBonus bonus = TeamScoreCalculator.titanBonus(team, FIRE_FORT, TeamCombos.NONE);

        assertEquals(3.0, bonus.elementPercent(), EPS, "two fire titans");
        assertEquals(0.6, bonus.superTitanPercent(), EPS);
        assertEquals(3.6, bonus.totalPercent(), EPS);
    }

    @Test
    @DisplayName("memberScores holds five components, the super titan one last; total = powerTerm + sum")
    void breakdown() {
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(
                team(1_000_000, List.of(SUPER_TITANS.get(0), SUPER_TITANS.get(1))), PLAIN_FORT, TeamCombos.NONE);

        assertEquals(5, breakdown.memberScores().size());
        assertEquals(10.0 * 1.2 / 100, breakdown.memberScores().get(4), EPS);
        assertEquals(breakdown.powerTerm() + breakdown.memberScores().stream().mapToDouble(Double::doubleValue).sum(),
                breakdown.total(), EPS);
    }

    @Test
    @DisplayName("sortScore: + 0.6 % per super titan, on top of totems")
    void sortScore() {
        assertEquals(1.006, TeamScoreCalculator.sortScore(
                team(100_000, List.of(SUPER_TITANS.get(0), titan("sigurd", TitanElement.WATER))), TeamCombos.NONE), EPS);
        TitanTeam withTotem = new TitanTeam("m1", 0, List.of(SUPER_TITANS.get(0), SUPER_TITANS.get(1)), 100_000, null,
                Set.of(TitanElement.WATER));
        assertEquals(1.0 + (1.25 + 1.2) / 100, TeamScoreCalculator.sortScore(withTotem, TeamCombos.NONE), EPS);
        assertEquals(1.03, team(100_000, SUPER_TITANS).sortScore(), EPS, "with the registered (no) combos");
    }

    @Test
    @DisplayName("the five super titans with the shipped combo: 3 % + comboPercent")
    void superTitansWithCombo() {
        TeamCombos combos = new TeamCombos(List.of(new TeamCombo("solaris-araji-eden-hyperion-tenebris", null,
                List.of("solaris", "araji", "eden", "hyperion", "tenebris"), ComboSource.C2W, null)));
        TitanTeam team = team(1_000_000, SUPER_TITANS);

        TeamScoreCalculator.TitanBonus bonus = TeamScoreCalculator.titanBonus(team, PLAIN_FORT, combos);
        assertEquals(3.0, bonus.superTitanPercent(), EPS);
        assertEquals(1.25, bonus.comboPercent(), EPS);
        assertEquals(4.25, bonus.totalPercent(), EPS);
        assertEquals(10.0 * 1.0425, TeamScoreCalculator.sortScore(team, combos), EPS);
    }

    @Test
    @DisplayName("changed CowScore bonuses do not change the super titan bonus")
    void notAdjustable() {
        TeamScoreCalculator.setBonuses(new CowScoreBonuses(3.0, 3.0, 2.0, 2.0, 2.0, 2.0, 2.0));
        TitanTeam team = team(1_000_000, SUPER_TITANS);

        assertEquals(3.0, superTitanPercent(team, PLAIN_FORT), EPS);
        assertEquals(0.6, TeamScoreCalculator.SUPER_TITAN_PERCENT, EPS);
    }
}
