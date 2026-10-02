package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The totem bonus of a titan team: {@link TeamScoreCalculator#TOTEM_PERCENT}
 * per totem, relative to the power term, fortification-independent - in
 * {@link TeamScoreCalculator#scoreFor(TitanTeam, Fortification)} and
 * {@link TitanTeam#sortScore()} - as the third component of the titan bonus.
 */
class TeamScoreCalculatorTotemTest {

    private static final double EPS = 1e-9;

    private static final Fortification FIRE_FORT = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
            new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification PLAIN_FORT = new Fortification("plain-fort", FortificationType.TITAN, 5, 0, 0, 0,
            null, List.of(), 0);

    /** 2 fire + 2 water titans - allows the fire and the water totem. */
    private static final List<Titan> TITANS = List.of(
            new Titan("f1", TitanElement.FIRE), new Titan("f2", TitanElement.FIRE),
            new Titan("w1", TitanElement.WATER), new Titan("w2", TitanElement.WATER));

    private static Set<TitanElement> totems(int count) {
        Set<TitanElement> totems = EnumSet.noneOf(TitanElement.class);
        if (count >= 1) {
            totems.add(TitanElement.FIRE);
        }
        if (count >= 2) {
            totems.add(TitanElement.WATER);
        }
        return totems;
    }

    private static TitanTeam team(int totemCount) {
        return new TitanTeam("m1", 0, TITANS, 1_000_000, null, totems(totemCount));
    }

    @ParameterizedTest(name = "{0} totem(s)")
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("scoreFor: totem component = powerTerm x 0 / 1.25 / 2.5 %, powerTerm and other components unchanged")
    void scoreFor(int totemCount) {
        for (Fortification fortification : List.of(FIRE_FORT, PLAIN_FORT)) {
            TeamScoreCalculator.Breakdown without = TeamScoreCalculator.scoreFor(team(0), fortification);
            TeamScoreCalculator.Breakdown with = TeamScoreCalculator.scoreFor(team(totemCount), fortification);

            double expectedBonus = 10.0 * totemCount * 1.25 / 100;
            assertEquals(10.0, with.powerTerm(), EPS);
            assertEquals(without.memberScores().subList(0, 2), with.memberScores().subList(0, 2));
            assertEquals(expectedBonus, with.memberScores().get(2), EPS);
            assertEquals(without.total() + expectedBonus, with.total(), EPS);
            assertEquals(with.total(), with.powerTerm()
                    + with.memberScores().stream().mapToDouble(Double::doubleValue).sum(), EPS);
            assertEquals(with.total() - with.powerTerm(), with.scoreWithoutPower(), EPS);
        }
    }

    @ParameterizedTest(name = "{0} totem(s)")
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("sortScore: powerTerm x (1 + n x 1.25 %)")
    void sortScore(int totemCount) {
        assertEquals(10.0 * (1 + totemCount * 1.25 / 100), team(totemCount).sortScore(), EPS);
    }

    @Test
    @DisplayName("totems do not count as buff matches")
    void buffFitScoreUnaffected() {
        assertEquals(team(0).buffFitScore(FIRE_FORT.buff()), team(2).buffFitScore(FIRE_FORT.buff()));
        assertEquals(TeamScoreCalculator.titanBonus(team(0), FIRE_FORT).elementPercent(),
                TeamScoreCalculator.titanBonus(team(2), FIRE_FORT).elementPercent(), EPS);
    }

    @Test
    @DisplayName("hero teams: total unchanged")
    void heroTeamsUnaffected() {
        HeroTeam heroTeam = new HeroTeam("m1", 0, List.of(new Hero("h1", List.of(Role.TANK))), 1_000_000);
        Fortification heroFort = new Fortification("hero-fort", FortificationType.HERO, 5, 0, 0, 0, null, List.of(), 0);
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(heroTeam, heroFort, TeamCombos.NONE);
        assertEquals(10.0, breakdown.total(), EPS);
    }
}
