package org.c2w.domain;

import org.c2w.data.model.*;

import java.util.List;

/**
 * Computes a team's aggregate score (its "CowScore") against one specific
 * fortification - shared by every place that needs to rank/display a team's
 * fit for a fortification, e.g. {@code org.c2w.gui.fort.FortificationEntryDialog},
 * the value overview dialogs, the lineup algorithms (via {@code TeamSide})
 * and the report.
 *
 * <h2>Hero teams (CowScore concept of 2026-09-30)</h2>
 * <pre>
 *     CowScore = totalPower / 100 000 x (1 + B)
 * </pre>
 * where the bonus {@code B} is the sum of (see {@link HeroBonus}):
 * <ul>
 *     <li><b>role buff</b>: {@link #ROLE_MATCH_PERCENT} per hero whose role
 *     matches the fortification's {@link RoleBuff} (the automatic BUFF mark);</li>
 *     <li><b>hero/fortification relation</b>: +{@link #RELATION_PERCENT} once
 *     if at least one hero is marked {@link FortMark#POSITIVE} for this
 *     fortification, -{@link #RELATION_PERCENT} once if at least one hero is
 *     marked {@link FortMark#NEGATIVE} (both: net 0);</li>
 *     <li><b>pet</b>: {@link #PET_MARKED_PERCENT} if the team's pet is marked
 *     for this fortification (its strength is already part of the power, so
 *     merely having one adds nothing);</li>
 *     <li><b>war flag</b>: {@link #WAR_FLAG_MARKED_PERCENT} if the team's war
 *     flag is marked for this fortification, otherwise
 *     {@link #WAR_FLAG_PRESENT_PERCENT} for merely having one (its strength
 *     is not part of the power);</li>
 *     <li><b>team combos</b>: not implemented yet (planned: +1.25 % once).</li>
 * </ul>
 * Theoretical range of B: -1.25 % ... +11.25 % (+12.5 % once combos exist).
 * Power stays the dominating factor - the bonus only decides between teams
 * of similar power.
 *
 * <h2>Titan teams (unchanged for now)</h2>
 * Sum of every titan's {@link CowScoreTier} value (buffFitScore at a buffed
 * fortification, generalScore otherwise) plus totalPower / {@link #POWER_DIVISOR}.
 * Titans will be moved to the new model separately.
 */
public final class TeamScoreCalculator {

    /**
     * Divisor a team's totalPower is scaled down by - totalPower is typically
     * six/seven digits, this turns it into a handy single/double digit figure.
     * The single source of this value.
     */
    public static final double POWER_DIVISOR = 100_000.0;

    /** Bonus in percent per hero whose role matches the fortification's {@link RoleBuff}. */
    public static final double ROLE_MATCH_PERCENT = 1.5;

    /** Bonus (or malus) in percent for a positively (negatively) marked hero/fortification relation - once per team. */
    public static final double RELATION_PERCENT = 1.25;

    /** Bonus in percent for a pet marked for the fortification. */
    public static final double PET_MARKED_PERCENT = 1.25;

    /** Bonus in percent for a war flag marked for the fortification. */
    public static final double WAR_FLAG_MARKED_PERCENT = 1.25;

    /** Bonus in percent for merely fielding a war flag (not marked for the fortification). */
    public static final double WAR_FLAG_PRESENT_PERCENT = 0.6;

    private TeamScoreCalculator() {
    }

    /**
     * The individual bonus components of a hero team at one fortification,
     * each in percent - see the class Javadoc for how each is determined.
     */
    public record HeroBonus(double rolePercent, double relationPercent, double petPercent, double warFlagPercent) {

        /** The bonus B in percent - the sum of all components. */
        public double totalPercent() {
            return rolePercent + relationPercent + petPercent + warFlagPercent;
        }

        /** The components in a fixed order (role, relation, pet, war flag) - see {@link Breakdown#memberScores()}. */
        List<Double> asList() {
            return List.of(rolePercent, relationPercent, petPercent, warFlagPercent);
        }
    }

    /** The bonus components of {@code team} at {@code fortification} - see the class Javadoc. */
    public static HeroBonus heroBonus(HeroTeam team, Fortification fortification) {
        Buff buff = fortification.buff();
        String fortificationId = fortification.id();

        long roleMatches = team.heroes().stream().filter(h -> h.matchesBuff(buff)).count();
        double role = roleMatches * ROLE_MATCH_PERCENT;

        boolean anyPositive = team.heroes().stream().anyMatch(h -> h.fortMark(fortificationId) == FortMark.POSITIVE);
        boolean anyNegative = team.heroes().stream().anyMatch(h -> h.fortMark(fortificationId) == FortMark.NEGATIVE);
        double relation = (anyPositive ? RELATION_PERCENT : 0) - (anyNegative ? RELATION_PERCENT : 0);

        double pet = team.pet() != null && team.pet().isMarkedFor(fortificationId) ? PET_MARKED_PERCENT : 0;

        double warFlag = 0;
        if (team.warFlag() != null) {
            warFlag = team.warFlag().isMarkedFor(fortificationId) ? WAR_FLAG_MARKED_PERCENT : WAR_FLAG_PRESENT_PERCENT;
        }
        return new HeroBonus(role, relation, pet, warFlag);
    }

    /**
     * Scores {@code team} against {@code fortification}: totalPower / 100 000
     * x (1 + B) - see the class Javadoc. {@link Breakdown#memberScores()}
     * holds the four bonus components (role, relation, pet, war flag)
     * converted into score points (powerTerm x percent / 100), so that
     * total = powerTerm + sum(memberScores) holds for hero and titan teams alike.
     */
    public static Breakdown scoreFor(HeroTeam team, Fortification fortification) {
        HeroBonus bonus = heroBonus(team, fortification);
        double powerTerm = team.totalPower() / POWER_DIVISOR;
        List<Double> bonusPoints = bonus.asList().stream().map(percent -> powerTerm * percent / 100.0).toList();
        return breakdownFor(bonusPoints, powerTerm);
    }

    /**
     * The fortification-independent score of {@code team} - used by the
     * lineup algorithms for fortifications without a buff and as a general
     * ranking (see {@code HeroTeam#sortScore()}): totalPower / 100 000 x
     * (1 + {@link #WAR_FLAG_PRESENT_PERCENT} if the team fields a war flag).
     * Everything else in the bonus depends on a specific fortification.
     */
    public static double sortScore(HeroTeam team) {
        double percent = team.warFlag() != null ? WAR_FLAG_PRESENT_PERCENT : 0;
        return team.totalPower() / POWER_DIVISOR * (1 + percent / 100.0);
    }

    /** The TITAN-side counterpart of {@link #scoreFor(HeroTeam, Fortification)} - still the tier-based formula, see class Javadoc. */
    public static Breakdown scoreFor(TitanTeam team, Fortification fortification) {
        List<Double> memberScores;
        if (fortification.buff() != null) {
            TitanTeamBuffFitScore buffFitScore = TitanTeamBuffFitScore.of(team, fortification);
            memberScores = team.titans().stream()
                    .map(t -> buffFitScore.memberScores().get(t.id()).value())
                    .toList();
        } else {
            memberScores = team.titans().stream().map(t -> t.generalScore().value()).toList();
        }
        return breakdownFor(memberScores, team.totalPower() / POWER_DIVISOR);
    }

    private static Breakdown breakdownFor(List<Double> memberScores, double powerTerm) {
        double total = powerTerm + memberScores.stream().mapToDouble(Double::doubleValue).sum();
        return new Breakdown(memberScores, powerTerm, total);
    }

    /**
     * One team's score breakdown against one fortification: total =
     * powerTerm + sum(memberScores). For a titan team memberScores are the
     * per-titan tier values; for a hero team they are the four bonus
     * components (role, relation, pet, war flag) in score points - see
     * {@link #scoreFor(HeroTeam, Fortification)}.
     */
    public record Breakdown(List<Double> memberScores, double powerTerm, double total) {
    }
}
