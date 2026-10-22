package org.c2w.eval;

import org.c2w.data.model.Buff;
import org.c2w.data.model.Fortification;
import org.c2w.domain.HodorSettings;

import java.util.*;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/**
 * The rating of one side's lineup used by {@link HodorAlgorithm} - independent of the search, so it
 * can be tested on its own:
 * <pre>
 * rating = GATE_WEIGHT x sum over gates         ( points behind x gate value )
 *        +               sum over fortifications ( captureBonus  x gate strength )
 *        +               sum over fortifications ( SLOT_POINTS   x slot sum )
 * </pre>
 * <ul>
 *     <li><b>gate strength</b> of a fortification: the biggest strength {@code S} of its teams, 0 if empty;</li>
 *     <li><b>slot sum</b> of a fortification: the sum of all {@code S};</li>
 *     <li><b>gate value</b> of a {@link HodorGates.Gate}: the SMALLEST gate strength of its
 *     fortifications - the attacker takes the weakest one.</li>
 * </ul>
 * Only the fortifications and gates of this side count. {@code S} comes from a {@link Strength}:
 * {@link #power} (HODOR) or {@link #cowScore} (HODOR (CowScore)).
 *
 * @param <T> the team type - a {@code null} team (an existing entry that can no longer be resolved)
 *            occupies its slot with {@code S = 0}
 */
final class HodorEvaluation<T> {

    /** Points of the attacker per slot. */
    static final int SLOT_POINTS = 35;

    /** How much more a gate counts than its own capture bonus - deliberately not adjustable. */
    static final double GATE_WEIGHT = 2;

    /** The strength {@code S} of a team in a fortification. */
    interface Strength<T> {

        /** Units of {@code team} matching {@code fortification}'s buff - 0 if it has none or they don't matter. */
        int matchingUnits(T team, Fortification fortification);

        /**
         * {@code S} of {@code team} in {@code fortification}, where {@code matchingUnitsOfFortification}
         * is the sum of {@link #matchingUnits} over every team of that fortification (as in the game,
         * the buff counts the whole fortification).
         */
        double of(T team, Fortification fortification, int matchingUnitsOfFortification);
    }

    /**
     * HODOR: {@code S = totalPower x (1 + weight(effect) x buffPercent / 100)}, buffPercent =
     * bonusPercent x matching units of the whole fortification ({@code matchingUnitsOf} is only asked
     * for a buffed fortification); {@code S = totalPower} without buff.
     */
    static <T> Strength<T> power(ToIntFunction<T> totalPowerOf, ToIntBiFunction<T, Fortification> matchingUnitsOf,
                                 HodorSettings settings) {
        return new Strength<>() {
            @Override
            public int matchingUnits(T team, Fortification fortification) {
                return fortification.buff() == null ? 0 : matchingUnitsOf.applyAsInt(team, fortification);
            }

            @Override
            public double of(T team, Fortification fortification, int matchingUnitsOfFortification) {
                double power = totalPowerOf.applyAsInt(team);
                Buff buff = fortification.buff();
                if (buff == null) {
                    return power;
                }
                double buffPercent = buff.bonusPercent() * matchingUnitsOfFortification;
                return power * (1 + settings.weight(buff.effect()) * buffPercent / 100);
            }
        };
    }

    /** HODOR (CowScore): {@code S} is the CowScore, which already contains the role/element bonus. */
    static <T> Strength<T> cowScore(ToDoubleBiFunction<T, Fortification> cowScoreOf) {
        return new Strength<>() {
            @Override
            public int matchingUnits(T team, Fortification fortification) {
                return 0;
            }

            @Override
            public double of(T team, Fortification fortification, int matchingUnitsOfFortification) {
                return cowScoreOf.applyAsDouble(team, fortification);
            }
        };
    }

    /** Gate strength and slot sum of one fortification. */
    record FortificationValue(double gateStrength, double slotSum) {
        static final FortificationValue EMPTY = new FortificationValue(0, 0);
    }

    private final List<Fortification> fortifications;
    private final List<HodorGates.Gate> gates;
    /** Per gate the indexes (into {@link #fortifications}) of its fortifications of this side. */
    private final int[][] gateIndexes;
    private final Strength<T> strength;

    /**
     * @param fortifications the fortifications of this side - their order is the index order of {@link #total}
     * @param gates          the gates; only those with at least one fortification in {@code fortifications} count
     */
    HodorEvaluation(List<Fortification> fortifications, List<HodorGates.Gate> gates, Strength<T> strength) {
        this.fortifications = List.copyOf(fortifications);
        this.strength = Objects.requireNonNull(strength, "strength");
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < this.fortifications.size(); i++) {
            indexById.put(this.fortifications.get(i).id(), i);
        }
        List<HodorGates.Gate> ownGates = new ArrayList<>();
        List<int[]> indexes = new ArrayList<>();
        for (HodorGates.Gate gate : gates) {
            int[] own = gate.fortificationIds().stream()
                    .filter(indexById::containsKey)
                    .mapToInt(indexById::get)
                    .toArray();
            if (own.length > 0) {
                ownGates.add(gate);
                indexes.add(own);
            }
        }
        this.gates = List.copyOf(ownGates);
        this.gateIndexes = indexes.toArray(new int[0][]);
    }

    /** The gates of this side, in the order given to the constructor - for tests. */
    List<HodorGates.Gate> gates() {
        return gates;
    }

    /**
     * The rating of a whole lineup: {@code teamsByFortificationId} maps a fortification id to its teams.
     * For tests - the search rates incrementally with {@link #valueOf} and {@link #total}.
     */
    double evaluate(Map<String, List<T>> teamsByFortificationId) {
        FortificationValue[] values = new FortificationValue[fortifications.size()];
        for (int i = 0; i < values.length; i++) {
            Fortification fortification = fortifications.get(i);
            values[i] = valueOf(fortification, teamsByFortificationId.getOrDefault(fortification.id(), List.of()));
        }
        return total(values);
    }

    /** {@code S} of every team of {@code teams} in {@code fortification}, in the same order. */
    double[] strengths(Fortification fortification, List<T> teams) {
        int matching = 0;
        for (T team : teams) {
            if (team != null) {
                matching += strength.matchingUnits(team, fortification);
            }
        }
        double[] result = new double[teams.size()];
        for (int i = 0; i < result.length; i++) {
            T team = teams.get(i);
            result[i] = team == null ? 0 : strength.of(team, fortification, matching);
        }
        return result;
    }

    /** Gate strength (biggest {@code S}) and slot sum (sum of {@code S}) of {@code fortification} with {@code teams}. */
    FortificationValue valueOf(Fortification fortification, List<T> teams) {
        if (teams.isEmpty()) {
            return FortificationValue.EMPTY;
        }
        double max = 0;
        double sum = 0;
        for (double s : strengths(fortification, teams)) {
            max = Math.max(max, s);
            sum += s;
        }
        return new FortificationValue(max, sum);
    }

    /** The rating from one {@link FortificationValue} per fortification, indexed like {@link #fortifications}. */
    double total(FortificationValue[] values) {
        double rating = 0;
        for (int g = 0; g < gateIndexes.length; g++) {
            double gateValue = Double.MAX_VALUE;
            for (int index : gateIndexes[g]) {
                gateValue = Math.min(gateValue, values[index].gateStrength());
            }
            rating += GATE_WEIGHT * gates.get(g).pointsBehind() * gateValue;
        }
        for (int i = 0; i < values.length; i++) {
            Fortification fortification = fortifications.get(i);
            rating += fortification.captureBonus() * values[i].gateStrength();
            rating += SLOT_POINTS * values[i].slotSum();
        }
        return rating;
    }
}
