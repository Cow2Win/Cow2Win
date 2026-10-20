package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.domain.TeamScoreCalculator;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Everything a {@link LineupAlgorithm} needs to know about ONE side of the
 * map - heroes or titans - bundled into a single object: which team type and
 * fortification type belong together, and the accessor/scoring functions for that side's team class
 * {@code T} ({@link HeroTeam} or {@link TitanTeam}).
 *
 * <p>A side is fixed per algorithm INSTANCE (see {@link AbstractLineupAlgorithm#side()}),
 * so the hero part and the titan part of a lineup can be filled by different
 * strategies - or one of them left to manual picks entirely (see {@link
 * ManualLineupAlgorithm}).
 *
 * <p>In production code, only the two constants {@link #HERO} and
 * {@link #TITAN} are meant to exist - hero teams can only ever defend HERO fortifications and titan teams
 * only TITAN fortifications, so the two sides never compete for the same
 * slots.
 *
 * @param teamType         team type written into every {@link Lineup.Entry} this side produces
 * @param fortificationType fortification type this side's teams are allowed to defend
 * @param teamsOf          a member's teams of this side ({@link GuildMember#heroTeams()}/{@link GuildMember#titanTeams()})
 * @param totalPowerOf     raw power of one team
 * @param sortScoreOf      fortification-independent score of one team (see {@link HeroTeam#sortScore()}/{@link TitanTeam#sortScore()})
 * @param cowScoreOf       full CowScore-plus-power figure of one team on one fortification
 *                         (see {@link TeamScoreCalculator#scoreFor})
 * @param <T>              {@link HeroTeam} or {@link TitanTeam}
 */
public record TeamSide<T>(
        Lineup.TeamType teamType,
        FortificationType fortificationType,
        Function<GuildMember, List<T>> teamsOf,
        ToIntFunction<T> totalPowerOf,
        ToDoubleFunction<T> sortScoreOf,
        ToDoubleBiFunction<T, Fortification> cowScoreOf
) {

    /** The hero side: HERO teams on HERO fortifications. */
    public static final TeamSide<HeroTeam> HERO = new TeamSide<>(
            Lineup.TeamType.HERO, FortificationType.HERO,
            GuildMember::heroTeams, HeroTeam::totalPower, HeroTeam::sortScore,
            (team, fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total());

    /** The titan side: TITAN teams on TITAN fortifications. */
    public static final TeamSide<TitanTeam> TITAN = new TeamSide<>(
            Lineup.TeamType.TITAN, FortificationType.TITAN,
            GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::sortScore,
            (team, fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total());
}
