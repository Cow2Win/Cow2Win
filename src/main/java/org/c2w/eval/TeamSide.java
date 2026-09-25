package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.util.TeamScoreCalculator;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/**
 * Everything a {@link LineupAlgorithm} needs to know about ONE side of the
 * map - heroes or titans - bundled into a single object: which team type and
 * fortification type belong together, which fortification is that side's
 * bridge, and the accessor/scoring functions for that side's team class
 * {@code T} ({@link HeroTeam} or {@link TitanTeam}).
 *
 * <p>Introduced (2026-09-24) when the lineup algorithms were split into
 * separate HERO-only and TITAN-only algorithms: before that, every algorithm
 * ran both sides back to back inside one {@code run()} and passed these same
 * values around as a long list of method-reference parameters. A side is now
 * fixed per algorithm INSTANCE (see {@link AbstractLineupAlgorithm#side()}),
 * so the hero part and the titan part of a lineup can be filled by different
 * strategies - or one of them left to manual picks entirely (see {@link
 * ManualLineupAlgorithm}).
 *
 * <p>Only the two constants {@link #HERO} and {@link #TITAN} are meant to
 * exist - hero teams can only ever defend HERO fortifications and titan teams
 * only TITAN fortifications, so the two sides never compete for the same
 * slots.
 *
 * @param teamType         team type written into every {@link Lineup.Entry} this side produces
 * @param fortificationType fortification type this side's teams are allowed to defend
 * @param bridgeId         id of this side's bridge - the strategic choke point almost every other
 *                         fortification of this side depends on, special-cased by every strategy
 * @param teamsOf          a member's teams of this side ({@link GuildMember#heroTeams()}/{@link GuildMember#titanTeams()})
 * @param totalPowerOf     raw power of one team
 * @param buffFitScoreOf   simple role/element match count of one team for a buff
 * @param sortScoreOf      generalScore + scaled-down power of one team
 * @param cowScoreOf       full CowScore-plus-power figure of one team on one fortification
 *                         (see {@link TeamScoreCalculator#scoreFor})
 * @param <T>              {@link HeroTeam} or {@link TitanTeam}
 */
public record TeamSide<T>(
        Lineup.TeamType teamType,
        FortificationType fortificationType,
        String bridgeId,
        Function<GuildMember, List<T>> teamsOf,
        ToIntFunction<T> totalPowerOf,
        ToIntBiFunction<T, Buff> buffFitScoreOf,
        ToDoubleFunction<T> sortScoreOf,
        ToDoubleBiFunction<T, Fortification> cowScoreOf
) {

    /** The hero side: HERO teams on HERO fortifications, bridge "heros-bridge". */
    public static final TeamSide<HeroTeam> HERO = new TeamSide<>(
            Lineup.TeamType.HERO, FortificationType.HERO, "heros-bridge",
            GuildMember::heroTeams, HeroTeam::totalPower, HeroTeam::buffFitScore, HeroTeam::sortScore,
            (team, fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total());

    /** The titan side: TITAN teams on TITAN fortifications, bridge "bridge". */
    public static final TeamSide<TitanTeam> TITAN = new TeamSide<>(
            Lineup.TeamType.TITAN, FortificationType.TITAN, "bridge",
            GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::buffFitScore, TitanTeam::sortScore,
            (team, fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total());

    /** {@link #HERO} or {@link #TITAN}, whichever matches {@code teamType}. */
    public static TeamSide<?> of(Lineup.TeamType teamType) {
        return teamType == Lineup.TeamType.HERO ? HERO : TITAN;
    }
}
