package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The titan-team CowScore: totalPower / 100 000 x (1 + B), B = element buff
 * (1.5 % per matching titan) + titan/fortification relation (+-1.25 % once
 * per team) + totems (1.25 % per totem) - see {@link TeamScoreCalculator}.
 */
class TeamScoreCalculatorTitanTest {

    private static final double EPS = 1e-9;

    private static final Fortification FIRE_FORT = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
            new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification PLAIN_FORT = new Fortification("plain-fort", FortificationType.TITAN, 5, 0, 0, 0,
            null, List.of(), 0);

    private static Titan titan(String id, TitanElement element) {
        return new Titan(id, element);
    }

    private static Titan marked(String id, TitanElement element, String fortificationId, FortMark mark) {
        return new Titan(id, element, null, new FortMarks(Map.of(fortificationId, mark)));
    }

    private static TitanTeam team(int power, Set<TitanElement> totems, Titan... titans) {
        return new TitanTeam("m1", 0, List.of(titans), power, null, totems);
    }

    private static TitanTeam team(int power, Titan... titans) {
        return team(power, Set.of(), titans);
    }

    private static Fortification fortification(String id) {
        return FortificationRepository.findById(id).orElseThrow();
    }

    @Nested
    @DisplayName("examples from the requirement")
    class Examples {

        @Test
        @DisplayName("600 000 power, 3 fire titans, 1 positive, 2 totems at bastion-of-fire: B = 8.25 % -> 6.495")
        void bastionOfFire() {
            Fortification bastion = fortification("bastion-of-fire");
            TitanTeam team = team(600_000, Set.of(TitanElement.FIRE, TitanElement.WATER),
                    marked("f1", TitanElement.FIRE, bastion.id(), FortMark.POSITIVE), titan("f2", TitanElement.FIRE),
                    titan("f3", TitanElement.FIRE), titan("w1", TitanElement.WATER), titan("w2", TitanElement.WATER));

            assertEquals(8.25, TeamScoreCalculator.titanBonus(team, bastion).totalPercent(), EPS);
            assertEquals(6.495, TeamScoreCalculator.scoreFor(team, bastion).total(), EPS);
        }

        @Test
        @DisplayName("600 000 power, no marks, 1 totem at the bridge: B = 1.25 % -> 6.075")
        void bridgeWithTotem() {
            TitanTeam team = team(600_000, Set.of(TitanElement.WATER),
                    titan("w1", TitanElement.WATER), titan("w2", TitanElement.WATER), titan("e1", TitanElement.EARTH));

            assertEquals(6.075, TeamScoreCalculator.scoreFor(team, fortification("bridge")).total(), EPS);
        }

        @Test
        @DisplayName("500 000 power, 2 dark titans, 1 positive + 1 negative at moon-temple: B = 3 % -> 5.15")
        void moonTemple() {
            Fortification moonTemple = fortification("moon-temple");
            TitanTeam team = team(500_000,
                    marked("d1", TitanElement.DARK, moonTemple.id(), FortMark.POSITIVE),
                    marked("l1", TitanElement.LIGHT, moonTemple.id(), FortMark.NEGATIVE),
                    titan("d2", TitanElement.DARK));

            assertEquals(3.0, TeamScoreCalculator.titanBonus(team, moonTemple).totalPercent(), EPS);
            assertEquals(5.15, TeamScoreCalculator.scoreFor(team, moonTemple).total(), EPS);
        }

        @Test
        @DisplayName("500 000 power, 1 titan negative at the bridge: B = -1.25 % -> 4.9375")
        void bridgeNegative() {
            TitanTeam team = team(500_000, marked("f1", TitanElement.FIRE, "bridge", FortMark.NEGATIVE));

            assertEquals(4.9375, TeamScoreCalculator.scoreFor(team, fortification("bridge")).total(), EPS);
        }

        @Test
        @DisplayName("sortScore: 600 000 power, 2 totems -> 6.15")
        void sortScore() {
            TitanTeam team = team(600_000, Set.of(TitanElement.FIRE, TitanElement.WATER),
                    titan("f1", TitanElement.FIRE), titan("f2", TitanElement.FIRE),
                    titan("w1", TitanElement.WATER), titan("w2", TitanElement.WATER));

            assertEquals(6.15, TeamScoreCalculator.sortScore(team), EPS);
            assertEquals(6.15, team.sortScore(), EPS);
        }
    }

    @ParameterizedTest(name = "{0} matching titan(s)")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    @DisplayName("element buff: 1.5 % per titan whose element matches")
    void elementMatches(int matches) {
        List<Titan> titans = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            titans.add(titan("t" + i, i < matches ? TitanElement.FIRE : TitanElement.WATER));
        }
        TitanTeam team = new TitanTeam("m1", 0, titans, 1_000_000);

        TeamScoreCalculator.TitanBonus bonus = TeamScoreCalculator.titanBonus(team, FIRE_FORT);
        assertEquals(matches * TeamScoreCalculator.ELEMENT_MATCH_PERCENT, bonus.elementPercent(), EPS);
        assertEquals(10.0 * (1 + matches * 1.5 / 100), TeamScoreCalculator.scoreFor(team, FIRE_FORT).total(), EPS);
    }

    @Test
    @DisplayName("a double titan (one catalog entry for two titans) counts as one element match")
    void doubleTitanCountsOnce() {
        TitanTeam team = team(1_000_000, titan("aherona-and-pyro", TitanElement.FIRE));
        assertEquals(1.5, TeamScoreCalculator.titanBonus(team, FIRE_FORT).elementPercent(), EPS);
    }

    @Test
    @DisplayName("a fortification without a buff gives no element bonus")
    void noBuffNoElementBonus() {
        TitanTeam team = team(1_000_000, titan("f1", TitanElement.FIRE), titan("f2", TitanElement.FIRE));
        assertEquals(0.0, TeamScoreCalculator.titanBonus(team, PLAIN_FORT).elementPercent(), EPS);
        assertEquals(10.0, TeamScoreCalculator.scoreFor(team, PLAIN_FORT).total(), EPS);
    }

    @Nested
    @DisplayName("titan/fortification relation (once per team)")
    class Relation {

        private double relation(Titan... titans) {
            return TeamScoreCalculator.titanBonus(team(1_000_000, titans), PLAIN_FORT).relationPercent();
        }

        @Test
        @DisplayName("only positive: +1.25 %")
        void onlyPositive() {
            assertEquals(1.25, relation(marked("t1", TitanElement.FIRE, "plain-fort", FortMark.POSITIVE),
                    titan("t2", TitanElement.FIRE)), EPS);
        }

        @Test
        @DisplayName("only negative: -1.25 %")
        void onlyNegative() {
            assertEquals(-1.25, relation(marked("t1", TitanElement.FIRE, "plain-fort", FortMark.NEGATIVE),
                    titan("t2", TitanElement.FIRE)), EPS);
        }

        @Test
        @DisplayName("positive and negative: net 0")
        void both() {
            assertEquals(0.0, relation(marked("t1", TitanElement.FIRE, "plain-fort", FortMark.POSITIVE),
                    marked("t2", TitanElement.FIRE, "plain-fort", FortMark.NEGATIVE)), EPS);
        }

        @Test
        @DisplayName("several positive titans still count only once")
        void severalPositive() {
            assertEquals(1.25, relation(marked("t1", TitanElement.FIRE, "plain-fort", FortMark.POSITIVE),
                    marked("t2", TitanElement.WATER, "plain-fort", FortMark.POSITIVE),
                    marked("t3", TitanElement.EARTH, "plain-fort", FortMark.POSITIVE)), EPS);
        }

        @Test
        @DisplayName("marks for another fortification do not count")
        void otherFortification() {
            assertEquals(0.0, relation(marked("t1", TitanElement.FIRE, "fire-fort", FortMark.POSITIVE)), EPS);
        }
    }

    @Test
    @DisplayName("breakdown: three components (element, relation, totems) in score points, total = powerTerm + sum")
    void breakdown() {
        TitanTeam team = team(1_000_000, Set.of(TitanElement.FIRE),
                marked("f1", TitanElement.FIRE, "fire-fort", FortMark.NEGATIVE), titan("f2", TitanElement.FIRE),
                titan("w1", TitanElement.WATER));
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team, FIRE_FORT);

        assertEquals(10.0, breakdown.powerTerm(), EPS);
        List<Double> memberScores = breakdown.memberScores();
        assertEquals(3, memberScores.size());
        assertEquals(10.0 * 3.0 / 100, memberScores.get(0), EPS);   // element: 2 fire titans
        assertEquals(10.0 * -1.25 / 100, memberScores.get(1), EPS); // relation: negative
        assertEquals(10.0 * 1.25 / 100, memberScores.get(2), EPS);  // totems: 1
        assertEquals(breakdown.powerTerm() + memberScores.stream().mapToDouble(Double::doubleValue).sum(),
                breakdown.total(), EPS);
        assertEquals(breakdown.total() - breakdown.powerTerm(), breakdown.scoreWithoutPower(), EPS);
    }
}
