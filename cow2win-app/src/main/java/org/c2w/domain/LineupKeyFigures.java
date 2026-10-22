package org.c2w.domain;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The key figures of one lineup for one fortification type - shown by the lineup summary of the
 * fortification map ({@code org.c2w.gui.fort.LineupSummaryPanel}) and compared side by side in
 * the lineup comparison ({@code org.c2w.gui.LineupComparisonDialog}), so both show the same values.
 *
 * @param power         total power of the placed teams of the type (see {@link BuffCalculationService#totalPowerOf})
 * @param cowScore      summed CowScore of the placed teams (see {@link BuffCalculationService#sumHeroCowScore} /
 *                      {@link BuffCalculationService#sumTitanCowScore})
 * @param buffHits      placed heroes/titans matching the buff of their fortification (see
 *                      {@link BuffCalculationService#countHeroesIncreasingBuff} /
 *                      {@link BuffCalculationService#countTitansIncreasingBuff})
 * @param occupiedSlots taken slots of all fortifications of the type (at most each one's capacity)
 * @param capacity      summed capacity of all fortifications of the type
 * @param teamsOutside  teams of the type in the guild that are not placed in the lineup
 */
public record LineupKeyFigures(int power, double cowScore, int buffHits, int occupiedSlots, int capacity,
                               int teamsOutside) {

    /** How the compared value stands against the current one. */
    public enum Direction { BETTER, WORSE, EQUAL }

    /** The key figures of {@code lineup} for {@code type}, with the fortifications of the catalog. */
    public static LineupKeyFigures of(Lineup lineup, Guild guild, FortificationType type) {
        return of(lineup, guild, type, FortificationRepository.findAll());
    }

    /**
     * The key figures of {@code lineup} for {@code type}, resolved against {@code guild}; only the
     * fortifications of {@code type} in {@code catalog} count for the slots.
     */
    public static LineupKeyFigures of(Lineup lineup, Guild guild, FortificationType type,
                                      Collection<Fortification> catalog) {
        if (lineup == null || guild == null || type == null || catalog == null) {
            throw new IllegalArgumentException("lineup, guild, type and catalog must not be null");
        }
        Lineup.TeamType teamType = teamTypeOf(type);
        int power = 0;
        int placedTeams = 0;
        Map<String, Integer> entriesByFortification = new HashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() != teamType) {
                continue;
            }
            power += BuffCalculationService.totalPowerOf(entry, guild);
            if (resolves(entry, guild)) {
                placedTeams++;
            }
            entriesByFortification.merge(entry.fortificationId(), 1, Integer::sum);
        }

        int occupiedSlots = 0;
        int capacity = 0;
        for (Fortification fort : catalog) {
            if (fort.type() == type) {
                capacity += fort.capacity();
                occupiedSlots += Math.min(entriesByFortification.getOrDefault(fort.id(), 0), fort.capacity());
            }
        }

        boolean hero = type == FortificationType.HERO;
        double cowScore = hero ? BuffCalculationService.sumHeroCowScore(lineup, guild)
                : BuffCalculationService.sumTitanCowScore(lineup, guild);
        int buffHits = hero ? BuffCalculationService.countHeroesIncreasingBuff(lineup, guild)
                : BuffCalculationService.countTitansIncreasingBuff(lineup, guild);
        int teamsOutside = Math.max(0, teamsOf(guild, type) - placedTeams);
        return new LineupKeyFigures(power, cowScore, buffHits, occupiedSlots, capacity, teamsOutside);
    }

    /**
     * Whether {@code compared} is better than {@code current}: more is better, unless
     * {@code lessIsBetter} (e.g. {@link #teamsOutside}).
     */
    public static Direction direction(double current, double compared, boolean lessIsBetter) {
        int sign = Double.compare(compared, current);
        if (sign == 0) {
            return Direction.EQUAL;
        }
        return (sign > 0) != lessIsBetter ? Direction.BETTER : Direction.WORSE;
    }

    /** The team type placed in fortifications of {@code type}. */
    private static Lineup.TeamType teamTypeOf(FortificationType type) {
        return type == FortificationType.HERO ? Lineup.TeamType.HERO : Lineup.TeamType.TITAN;
    }

    /** True if {@code entry}'s team still exists in {@code guild}. */
    private static boolean resolves(Lineup.Entry entry, Guild guild) {
        return entry.teamType() == Lineup.TeamType.HERO
                ? BuffCalculationService.resolveHeroTeam(entry, guild).isPresent()
                : BuffCalculationService.resolveTitanTeam(entry, guild).isPresent();
    }

    /** All hero or titan teams of the guild's members. */
    private static int teamsOf(Guild guild, FortificationType type) {
        int teams = 0;
        for (GuildMember member : guild.members()) {
            teams += type == FortificationType.HERO ? member.heroTeams().size() : member.titanTeams().size();
        }
        return teams;
    }
}
