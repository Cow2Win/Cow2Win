package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.domain.HodorSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The rating of {@link HodorEvaluation}, with synthetic fortifications and teams. */
class HodorEvaluationTest {

    private static final double EPS = 1e-6;

    /** A team: power and number of fire titans. */
    private record Team(int power, int fire) {
    }

    private static final Fortification PLAIN = fortification("plain", 100, null);
    private static final Fortification GATE_A = fortification("gate-a", 0, null);
    private static final Fortification GATE_B = fortification("gate-b", 0, null);
    private static final Fortification FIRE = fortification("fire", 0,
            new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 8));

    private static Fortification fortification(String id, int captureBonus, Buff buff) {
        return new Fortification(id, FortificationType.TITAN, 4, captureBonus, 0, 0, buff, List.of(), 1);
    }

    private static HodorEvaluation.Strength<Team> power(double healthWeight) {
        Map<BuffEffect, Double> weights = new EnumMap<>(HodorSettings.DEFAULTS.weights());
        weights.put(BuffEffect.HEALTH_INCREASE, healthWeight);
        return HodorEvaluation.power(Team::power, (team, fortification) -> team.fire(),
                new HodorSettings(3, weights));
    }

    private static HodorEvaluation<Team> evaluation(List<Fortification> fortifications, List<HodorGates.Gate> gates) {
        return new HodorEvaluation<>(fortifications, gates, power(1.0));
    }

    @Test
    @DisplayName("Gate strength = biggest S, slot sum = sum of all S")
    void gateStrengthAndSlotSum() {
        HodorEvaluation<Team> evaluation = evaluation(List.of(PLAIN), List.of());

        double rating = evaluation.evaluate(Map.of("plain", List.of(new Team(10, 0), new Team(30, 0))));

        assertEquals(100 * 30 + HodorEvaluation.SLOT_POINTS * 40, rating, EPS);
    }

    @Test
    @DisplayName("Gate value of an OR-group = smallest gate strength of its fortifications, weighted by points behind")
    void orGroupCountsTheWeakest() {
        HodorGates.Gate gate = new HodorGates.Gate(List.of("gate-a", "gate-b"), FortificationType.TITAN, 1000);
        HodorEvaluation<Team> evaluation = evaluation(List.of(GATE_A, GATE_B), List.of(gate));

        double rating = evaluation.evaluate(Map.of(
                "gate-a", List.of(new Team(50, 0), new Team(5, 0)),
                "gate-b", List.of(new Team(20, 0))));

        assertEquals(HodorEvaluation.GATE_WEIGHT * 1000 * 20 + HodorEvaluation.SLOT_POINTS * 75, rating, EPS);
    }

    @Test
    @DisplayName("An empty fortification counts 0 - also as gate strength")
    void emptyFortification() {
        HodorGates.Gate gate = new HodorGates.Gate(List.of("gate-a", "gate-b"), FortificationType.TITAN, 1000);
        HodorEvaluation<Team> evaluation = evaluation(List.of(GATE_A, GATE_B), List.of(gate));

        double rating = evaluation.evaluate(Map.of("gate-a", List.of(new Team(50, 0))));

        assertEquals(HodorEvaluation.SLOT_POINTS * 50, rating, EPS, "the open gate-b is worth nothing");
    }

    @Test
    @DisplayName("Gates of the other side or of unknown fortifications are ignored")
    void foreignGatesIgnored() {
        HodorGates.Gate gate = new HodorGates.Gate(List.of("elsewhere"), FortificationType.HERO, 1000);
        HodorEvaluation<Team> evaluation = evaluation(List.of(PLAIN), List.of(gate));

        assertEquals(List.of(), evaluation.gates());
        assertEquals(100 * 10 + HodorEvaluation.SLOT_POINTS * 10,
                evaluation.evaluate(Map.of("plain", List.of(new Team(10, 0)))), EPS);
    }

    @Test
    @DisplayName("The buff counts the matching units of the whole fortification: 2 teams x 2 fire = 4 x 8 % = 32 % for both")
    void buffCountsWholeFortification() {
        HodorEvaluation<Team> evaluation = evaluation(List.of(FIRE), List.of());

        double[] strengths = evaluation.strengths(FIRE, List.of(new Team(1000, 2), new Team(500, 2), new Team(100, 0)));

        assertArrayEquals(new double[]{1320, 660, 132}, strengths, EPS,
                "the team without fire units profits as well");
    }

    @Test
    @DisplayName("The weight of the effect scales the buff; weight 0 ignores it")
    void weightScalesBuff() {
        List<Team> teams = List.of(new Team(1000, 2), new Team(500, 2));

        assertEquals(1000 * 1.16, new HodorEvaluation<>(List.of(FIRE), List.of(), power(0.5))
                .strengths(FIRE, teams)[0], EPS);
        assertEquals(1000, new HodorEvaluation<>(List.of(FIRE), List.of(), power(0.0))
                .strengths(FIRE, teams)[0], EPS);
    }

    @Test
    @DisplayName("CowScore: S is the CowScore, the real buff is not added")
    void cowScore() {
        HodorEvaluation<Team> evaluation = new HodorEvaluation<>(List.of(FIRE), List.of(),
                HodorEvaluation.cowScore((team, fortification) -> team.power() / 100.0));

        assertArrayEquals(new double[]{10, 5}, evaluation.strengths(FIRE,
                List.of(new Team(1000, 2), new Team(500, 2))), EPS);
    }

    @Test
    @DisplayName("A null team (unresolvable existing entry) occupies its slot with S = 0")
    void nullTeam() {
        HodorEvaluation<Team> evaluation = evaluation(List.of(FIRE), List.of());
        List<Team> teams = new java.util.ArrayList<>();
        teams.add(null);
        teams.add(new Team(1000, 1));

        assertArrayEquals(new double[]{0, 1080}, evaluation.strengths(FIRE, teams), EPS);
    }
}
