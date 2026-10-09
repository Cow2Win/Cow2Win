package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The team combo bonus of the titan-team CowScore: +comboPercent (shared with the hero
 * combos, default 1.25 %) once if at least one active {@link TeamCombo} matches,
 * independent of the fortification.
 */
class TeamScoreCalculatorTitanComboTest {

    private static final double EPS = 1e-9;

    private static final Fortification FIRE_FORT = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
            new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification PLAIN_FORT = new Fortification("plain-fort", FortificationType.TITAN, 5, 0, 0, 0,
            null, List.of(), 0);

    private static final TeamCombo SUPER_TITANS = combo("solaris-araji-eden-hyperion-tenebris", null,
            "solaris", "araji", "eden", "hyperion", "tenebris");
    private static final TeamCombo SIGURD_NOVA = combo("sigurd-nova", null, "sigurd", "nova");
    private static final TeamCombo ASHERONA_PYRO_MOLOCH = combo("asherona-and-pyro-moloch", null,
            "asherona-and-pyro", "moloch");
    private static final TeamCombos COMBOS = new TeamCombos(List.of(SUPER_TITANS, SIGURD_NOVA, ASHERONA_PYRO_MOLOCH));

    /** The expected values below use the default percentages - set explicitly, the test order must not matter. */
    @BeforeEach
    void defaultBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    @AfterEach
    void reset() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
        TeamScoreCalculator.setTitanCombos(null);
        TeamScoreCalculator.setHeroCombos(null);
    }

    private static TeamCombo combo(String id, LocalDate deactivated, String... titanIds) {
        return new TeamCombo(id, null, List.of(titanIds), ComboSource.C2W, deactivated);
    }

    /** A team of WATER titans (no element match at the fire fort), without totems. */
    private static TitanTeam team(int power, String... titanIds) {
        return team(power, Set.of(), titanIds);
    }

    private static TitanTeam team(int power, Set<TitanElement> totems, String... titanIds) {
        List<Titan> titans = Arrays.stream(titanIds).map(id -> new Titan(id, TitanElement.WATER)).toList();
        return new TitanTeam("m1", 0, titans, power, null, totems);
    }

    @Test
    @DisplayName("no combo matches: no combo bonus")
    void noCombo() {
        TitanTeam team = team(1_000_000, "sigurd", "solaris", "moloch");

        assertTrue(TeamScoreCalculator.matchingCombos(team, COMBOS).isEmpty());
        assertEquals(0.0, TeamScoreCalculator.titanBonus(team, FIRE_FORT, COMBOS).comboPercent(), EPS);
        assertEquals(10.0, TeamScoreCalculator.scoreFor(team, FIRE_FORT, COMBOS).total(), EPS);
    }

    @Test
    @DisplayName("one combo matches: +1.25 %, at every fortification")
    void oneCombo() {
        TitanTeam team = team(1_000_000, "solaris", "araji", "eden", "hyperion", "tenebris");

        assertEquals(List.of(SUPER_TITANS), TeamScoreCalculator.matchingCombos(team, COMBOS));
        assertEquals(1.25, TeamScoreCalculator.titanBonus(team, FIRE_FORT, COMBOS).comboPercent(), EPS);
        assertEquals(1.25, TeamScoreCalculator.titanBonus(team, PLAIN_FORT, COMBOS).comboPercent(), EPS);
        assertEquals(10.0 * 1.0125, TeamScoreCalculator.scoreFor(team, FIRE_FORT, COMBOS).total(), EPS);
        assertEquals(10.0 * 1.0125, TeamScoreCalculator.scoreFor(team, PLAIN_FORT, COMBOS).total(), EPS);
    }

    @Test
    @DisplayName("two combos match: both are reported, but the bonus counts only once")
    void twoCombosCountOnce() {
        TitanTeam team = team(1_000_000, "sigurd", "nova", "asherona-and-pyro", "moloch");

        assertEquals(List.of(SIGURD_NOVA, ASHERONA_PYRO_MOLOCH), TeamScoreCalculator.matchingCombos(team, COMBOS));
        assertEquals(1.25, TeamScoreCalculator.titanBonus(team, FIRE_FORT, COMBOS).comboPercent(), EPS);
        assertEquals(10.0 * 1.0125, TeamScoreCalculator.scoreFor(team, FIRE_FORT, COMBOS).total(), EPS);
    }

    @Test
    @DisplayName("a combo only partly in the team does not count")
    void partialCombo() {
        TitanTeam team = team(1_000_000, "solaris", "araji", "eden", "hyperion", "sigurd");

        assertTrue(TeamScoreCalculator.matchingCombos(team, COMBOS).isEmpty());
        assertEquals(0.0, TeamScoreCalculator.titanBonus(team, FIRE_FORT, COMBOS).comboPercent(), EPS);
    }

    @Test
    @DisplayName("a deactivated combo does not count")
    void deactivatedCombo() {
        TeamCombos combos = new TeamCombos(List.of(combo("sigurd-nova", LocalDate.of(2026, 9, 30), "sigurd", "nova")));
        TitanTeam team = team(100_000, "sigurd", "nova");

        assertTrue(TeamScoreCalculator.matchingCombos(team, combos).isEmpty());
        assertEquals(0.0, TeamScoreCalculator.titanBonus(team, FIRE_FORT, combos).comboPercent(), EPS);
        assertEquals(1.0, TeamScoreCalculator.sortScore(team, combos), EPS);
    }

    @Test
    @DisplayName("a double titan is one member: the combo needs it as one entry, not its two halves")
    void doubleTitanIsOneMember() {
        assertEquals(List.of(ASHERONA_PYRO_MOLOCH), TeamScoreCalculator.matchingCombos(
                team(1_000_000, "asherona-and-pyro", "moloch"), COMBOS));
        assertTrue(TeamScoreCalculator.matchingCombos(team(1_000_000, "asherona", "pyro", "moloch"), COMBOS).isEmpty());
    }

    @Test
    @DisplayName("sortScore: power / 100 000 x (1 + 1.25 % per totem + 1.25 % if combo)")
    void sortScore() {
        assertEquals(1.0, TeamScoreCalculator.sortScore(team(100_000, "sigurd"), COMBOS), EPS);
        assertEquals(1.0125, TeamScoreCalculator.sortScore(team(100_000, "sigurd", "nova"), COMBOS), EPS);
        assertEquals(1.025, TeamScoreCalculator.sortScore(
                team(100_000, Set.of(TitanElement.WATER), "sigurd", "nova"), COMBOS), EPS);
    }

    @Test
    @DisplayName("memberScores holds four bonus components, the combo one last; total = powerTerm + sum")
    void breakdownHasFourComponents() {
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(
                team(1_000_000, "sigurd", "nova"), FIRE_FORT, COMBOS);

        assertEquals(4, breakdown.memberScores().size());
        assertEquals(10.0 * 1.25 / 100, breakdown.memberScores().get(3), EPS);
        assertEquals(breakdown.total(),
                breakdown.powerTerm() + breakdown.memberScores().stream().mapToDouble(Double::doubleValue).sum(), EPS);
    }

    @Test
    @DisplayName("the overloads without combos use the registered titan combos (none by default)")
    void registeredCombos() {
        TitanTeam team = team(100_000, "sigurd", "nova");
        assertEquals(1.0, team.sortScore(), EPS, "no combos registered");

        TeamScoreCalculator.setTitanCombos(COMBOS);

        assertEquals(List.of(SIGURD_NOVA), TeamScoreCalculator.matchingCombos(team));
        assertEquals(1.0125, team.sortScore(), EPS);
        assertEquals(1.0125, TeamScoreCalculator.scoreFor(team, PLAIN_FORT).total(), EPS);
        assertEquals(1.25, TeamScoreCalculator.titanBonus(team, PLAIN_FORT).comboPercent(), EPS);
    }

    @Test
    @DisplayName("a changed comboPercent applies to hero and titan combos alike")
    void sharedComboPercent() {
        TeamScoreCalculator.setBonuses(new CowScoreBonuses(2.0, 2.0, 1.25, 1.25, 1.25, 1.75, 1.25));
        TitanTeam titans = team(100_000, "sigurd", "nova");
        HeroTeam heroes = new HeroTeam("m1", 0, List.of(new Hero("sebastian", List.of(Role.MAGE)),
                new Hero("nebula", List.of(Role.MAGE))), null, null, 100_000, null);
        TeamCombos heroCombos = new TeamCombos(List.of(
                new TeamCombo("sebastian-nebula", null, List.of("sebastian", "nebula"), ComboSource.C2W, null)));

        assertEquals(1.75, TeamScoreCalculator.titanBonus(titans, PLAIN_FORT, COMBOS).comboPercent(), EPS);
        assertEquals(1.0175, TeamScoreCalculator.sortScore(titans, COMBOS), EPS);
        assertEquals(1.75, TeamScoreCalculator.heroBonus(heroes, PLAIN_FORT, heroCombos).comboPercent(), EPS);
        assertEquals(1.0175, TeamScoreCalculator.sortScore(heroes, heroCombos), EPS);
    }

    @Test
    @DisplayName("hero and titan combos are registered separately")
    void separateRegistration() {
        TeamScoreCalculator.setTitanCombos(COMBOS);

        assertEquals(TeamCombos.NONE, TeamScoreCalculator.heroCombos());
        assertEquals(COMBOS, TeamScoreCalculator.titanCombos());
    }
}
