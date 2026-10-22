package org.c2w.domain;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the power of one fortification type sits, relative to the whole type - the data behind the
 * heatmap in the top row of the fortification map (see {@code org.c2w.gui.fort.FortificationHeatmapPanel}).
 *
 * <p>The value of a fortification is the pure power of its teams (no buff, no CowScore, see
 * {@link BuffCalculationService#totalPowerOf}) divided by its {@linkplain Fortification#capacity()
 * capacity} - empty slots count as 0. The reference is the power of all fortifications of the type
 * divided by the sum of their capacities. A fortification without a team is {@link Level#EMPTY};
 * otherwise its share of the reference decides the level:
 * <ul>
 *   <li>above {@link #VERY_HIGH_ABOVE_PERCENT} (110 %): {@link Level#VERY_HIGH},</li>
 *   <li>from {@link #HIGH_FROM_PERCENT} (100 %) up to 110 %, both included: {@link Level#HIGH},</li>
 *   <li>from {@link #LOW_BELOW_PERCENT} (90 %, included) to below 100 %: {@link Level#MEDIUM},</li>
 *   <li>below 90 %: {@link Level#LOW}.</li>
 * </ul>
 * If every fortification of the type is empty, all are {@link Level#EMPTY}.
 *
 * @param type   the fortification type this heatmap covers
 * @param levels one {@link Rating} per fortification id of {@code type}, in catalog order
 */
public record FortificationHeatmap(FortificationType type, Map<String, Rating> levels) {

    /** A fortification above this share (in percent, excluded) of the reference is {@link Level#VERY_HIGH}. */
    public static final int VERY_HIGH_ABOVE_PERCENT = 110;
    /** A fortification from this share (in percent, included) of the reference is at least {@link Level#HIGH}. */
    public static final int HIGH_FROM_PERCENT = 100;
    /** A fortification below this share (in percent, excluded) of the reference is {@link Level#LOW}. */
    public static final int LOW_BELOW_PERCENT = 90;

    /** Heatmap level of one fortification, from weak to strong. */
    public enum Level { EMPTY, LOW, MEDIUM, HIGH, VERY_HIGH }

    /**
     * Rating of one fortification.
     *
     * @param level        its heatmap level
     * @param value        its power per capacity slot
     * @param shareOfReference its value divided by the reference value (0 if the reference is 0) - not shown
     *                         anywhere; the tests check the reference with it
     */
    public record Rating(Level level, double value, double shareOfReference) {
    }

    public FortificationHeatmap {
        levels = Map.copyOf(levels);
    }

    /** The rating of fortification {@code fortificationId}, or null if it is not of this heatmap's type. */
    public Rating rating(String fortificationId) {
        return levels.get(fortificationId);
    }

    /** The level of fortification {@code fortificationId}, or null if it is not of this heatmap's type. */
    public Level level(String fortificationId) {
        Rating rating = levels.get(fortificationId);
        return rating == null ? null : rating.level();
    }

    /**
     * Computes the heatmap of the fortifications of {@code type} in {@code catalog} (others are
     * ignored, also for the reference) for the teams of {@code lineup}.
     */
    public static FortificationHeatmap of(FortificationType type, Collection<Fortification> catalog,
                                          Lineup lineup, Guild guild) {
        if (type == null || catalog == null || lineup == null || guild == null) {
            throw new IllegalArgumentException("type, catalog, lineup and guild must not be null");
        }
        Map<String, Long> powerById = new HashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            powerById.merge(entry.fortificationId(), (long) BuffCalculationService.totalPowerOf(entry, guild), Long::sum);
        }
        Map<String, Integer> teamsById = new HashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            teamsById.merge(entry.fortificationId(), 1, Integer::sum);
        }

        long totalPower = 0;
        long totalCapacity = 0;
        for (Fortification fort : catalog) {
            if (fort.type() == type) {
                totalPower += powerById.getOrDefault(fort.id(), 0L);
                totalCapacity += fort.capacity();
            }
        }

        Map<String, Rating> levels = new LinkedHashMap<>();
        for (Fortification fort : catalog) {
            if (fort.type() != type) {
                continue;
            }
            long power = powerById.getOrDefault(fort.id(), 0L);
            double value = (double) power / fort.capacity();
            double reference = totalCapacity == 0 ? 0 : (double) totalPower / totalCapacity;
            double share = reference == 0 ? 0 : value / reference;
            levels.put(fort.id(), new Rating(level(teamsById.getOrDefault(fort.id(), 0), power, fort.capacity(),
                    totalPower, totalCapacity), value, share));
        }
        return new FortificationHeatmap(type, levels);
    }

    /**
     * The level of a fortification with {@code teams} teams of {@code power} power on {@code capacity}
     * slots - compared in whole numbers ({@code power/capacity} against {@code percent} % of
     * {@code totalPower/totalCapacity}), so a share of exactly a limit is never off by rounding.
     */
    private static Level level(int teams, long power, int capacity, long totalPower, long totalCapacity) {
        if (teams == 0 || totalPower == 0) {
            return Level.EMPTY;
        }
        long scaledValue = power * totalCapacity * 100;
        if (scaledValue > VERY_HIGH_ABOVE_PERCENT * totalPower * capacity) {
            return Level.VERY_HIGH;
        }
        if (scaledValue >= HIGH_FROM_PERCENT * totalPower * capacity) {
            return Level.HIGH;
        }
        if (scaledValue >= LOW_BELOW_PERCENT * totalPower * capacity) {
            return Level.MEDIUM;
        }
        return Level.LOW;
    }
}
