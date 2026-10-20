package org.c2w.domain;

import org.c2w.data.model.BuffEffect;

import java.util.EnumMap;
import java.util.Map;

/**
 * The adjustable values of the HODOR algorithms (see {@code org.c2w.eval.HodorAlgorithm}) - set in
 * the settings and registered via {@code HodorAlgorithm#setSettings}. Knows neither the config file
 * nor Swing, like {@link CowScoreBonuses}.
 *
 * @param reserve how many teams beyond the free slots enter the pool (heroes and titans alike) - only
 *                HODOR and HODOR (CowScore), S.H.I.E.L.D has no reserve,
 *                {@link #MIN_RESERVE}-{@link #MAX_RESERVE}
 * @param weights weight of each {@link BuffEffect} in HODOR's strength formula
 *                {@code totalPower x (1 + weight x buffPercent / 100)}, {@link #MIN_WEIGHT}-{@link #MAX_WEIGHT};
 *                used by HODOR and S.H.I.E.L.D, not by HODOR (CowScore)
 */
public record HodorSettings(int reserve, Map<BuffEffect, Double> weights) {

    public static final int DEFAULT_RESERVE = 3;
    public static final int MIN_RESERVE = 0;
    public static final int MAX_RESERVE = 10;

    public static final double MIN_WEIGHT = 0.0;
    public static final double MAX_WEIGHT = 2.0;

    /** Step of the settings' weight spinners. */
    public static final double STEP = 0.05;

    /** Default weight per effect - estimated from the journal. */
    private static final Map<BuffEffect, Double> DEFAULT_WEIGHTS = defaultWeights();

    /** The values the algorithm was designed with. */
    public static final HodorSettings DEFAULTS = new HodorSettings(DEFAULT_RESERVE, DEFAULT_WEIGHTS);

    public HodorSettings {
        EnumMap<BuffEffect, Double> copy = new EnumMap<>(BuffEffect.class);
        if (weights != null) {
            copy.putAll(weights);
        }
        weights = Map.copyOf(copy);
    }

    /** The default weight of {@code effect}. */
    public static double defaultWeight(BuffEffect effect) {
        return DEFAULT_WEIGHTS.get(effect);
    }

    /** The weight of {@code effect} - its default if none is set. */
    public double weight(BuffEffect effect) {
        Double weight = weights.get(effect);
        return weight == null ? defaultWeight(effect) : weight;
    }

    /**
     * Every value within its range, weights rounded to two decimals; a missing or invalid
     * (NaN/infinite) weight is its default.
     */
    public HodorSettings normalized() {
        EnumMap<BuffEffect, Double> normalized = new EnumMap<>(BuffEffect.class);
        for (BuffEffect effect : BuffEffect.values()) {
            Double weight = weights.get(effect);
            normalized.put(effect, weight == null || !Double.isFinite(weight)
                    ? defaultWeight(effect)
                    : round(Math.clamp(weight, MIN_WEIGHT, MAX_WEIGHT)));
        }
        return new HodorSettings(Math.clamp(reserve, MIN_RESERVE, MAX_RESERVE), normalized);
    }

    private static Map<BuffEffect, Double> defaultWeights() {
        EnumMap<BuffEffect, Double> weights = new EnumMap<>(BuffEffect.class);
        weights.put(BuffEffect.DAMAGE_RESIST, 1.0);
        weights.put(BuffEffect.ENERGY_GAIN, 0.5);
        weights.put(BuffEffect.SKILL_COOLDOWN_DECREASE, 0.5);
        weights.put(BuffEffect.HEALTH_INCREASE, 0.25);
        weights.put(BuffEffect.HEALING_INCREASE, 0.2);
        weights.put(BuffEffect.ARMOR_INCREASE, 0.1);
        weights.put(BuffEffect.MAGIC_DEFENSE_INCREASE, 0.0);
        return Map.copyOf(weights);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
