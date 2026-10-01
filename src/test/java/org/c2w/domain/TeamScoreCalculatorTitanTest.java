package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The titan-team score: totalPower / 100 000 plus every titan's tier value -
 * its buff-fit score at a fortification with an {@link ElementBuff}, its
 * general score otherwise.
 */
class TeamScoreCalculatorTitanTest {

    private static final double EPS = 1e-9;

    private static final Fortification FIRE_FORT = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
            new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification PLAIN_FORT = new Fortification("plain-fort", FortificationType.TITAN, 5, 0, 0, 0,
            null, List.of(), 0);

    private static TitanTeam team(int power, Titan... titans) {
        return new TitanTeam("m1", 0, List.of(titans), power, null);
    }

    @Test
    @DisplayName("without a buff: power term plus every titan's general score")
    void unbuffedUsesGeneralScore() {
        Titan great = new Titan("t1", TitanElement.WATER, null, new CowScore(CowScoreTier.GREAT, null));
        Titan standard = new Titan("t2", TitanElement.FIRE);
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team(1_000_000, great, standard), PLAIN_FORT);

        assertEquals(10.0, breakdown.powerTerm(), EPS);
        assertEquals(List.of(CowScoreTier.GREAT.value(), CowScoreTier.GOOD.value()), breakdown.memberScores());
        assertEquals(10.0 + CowScoreTier.GREAT.value() + CowScoreTier.GOOD.value(), breakdown.total(), EPS);
    }

    @Test
    @DisplayName("with an element buff: matching titans default to GOOD, others to AVERAGE")
    void elementMatchDefaults() {
        Titan fire = new Titan("t1", TitanElement.FIRE);
        Titan water = new Titan("t2", TitanElement.WATER);
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team(0, fire, water), FIRE_FORT);

        assertEquals(List.of(CowScoreTier.GOOD.value(), CowScoreTier.AVERAGE.value()), breakdown.memberScores());
    }

    @Test
    @DisplayName("an explicit buff-fit override wins over the element match")
    void overrideWins() {
        Titan fire = new Titan("t1", TitanElement.FIRE, null,
                new CowScore(null, Map.of("fire-fort", CowScoreTier.NEGATIVE)));
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team(0, fire), FIRE_FORT);

        assertEquals(List.of(CowScoreTier.NEGATIVE.value()), breakdown.memberScores());
    }
}
