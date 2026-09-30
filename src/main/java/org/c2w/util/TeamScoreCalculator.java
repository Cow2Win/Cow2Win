package org.c2w.util;

import org.c2w.data.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes a team's aggregate score against one specific fortification -
 * shared by every place that needs to rank/display a team's fit for a
 * fortification, e.g. {@code org.c2w.gui.fort.FortificationEntryDialog}
 * (per-row buff-count label and debug log) and
 * {@code org.c2w.gui.hero.HeroValueOverviewDialog}/{@code org.c2w.gui.titan.TitanValueOverviewDialog} (one cell per
 * team/fortification combination) - added 2026-09-11 to give both a single,
 * shared implementation instead of two independently-maintained copies of
 * the same formula (see git history: the two had briefly diverged on
 * whether the power term applies to a buffed fortification).
 *
 * <p>Per-member score is every hero's/titan's
 * {@link Hero#buffFitScore(String, boolean)}/{@link Titan#buffFitScore(String, boolean)}
 * when {@code fortification} has a buff (see
 * {@link HeroTeamBuffFitScore#of(HeroTeam, Fortification)}/
 * {@link TitanTeamBuffFitScore#of(TitanTeam, Fortification)}), or every
 * hero's/titan's {@link Hero#generalScore()}/{@link Titan#generalScore()}
 * otherwise. Either way, {@link HeroTeam#totalPower()}/{@link TitanTeam#totalPower()}
 * scaled down by {@link #POWER_DIVISOR} is added on top - per the user's
 * explicit clarification (2026-09-11): the power term applies uniformly,
 * regardless of which per-member score is in play. This is therefore NOT
 * the same total as {@link HeroTeamBuffFitScore#total()}/
 * {@link TitanTeamBuffFitScore#total()} (which deliberately omit the power
 * term) nor exactly {@link HeroTeam#sortScore()}/{@link TitanTeam#sortScore()}
 * (which only ever uses generalScore) - it is the union of both, chosen per
 * fortification.
 */
public final class TeamScoreCalculator {

    /**
     * Divisor a team's totalPower is scaled down by before it is added to a
     * score - totalPower is typically five/six digits, while every
     * per-member score lives on {@link org.c2w.data.model.CowScoreTier}'s
     * 0.4-0.9 grid, so this brings both terms to a comparable order of
     * magnitude. The single source of this value: {@link HeroTeam#sortScore()}
     * and {@link TitanTeam#sortScore()} use it too.
     */
    public static final double POWER_DIVISOR = 100_000.0;

    private TeamScoreCalculator() {
    }

    /**
     * Scores {@code team} against {@code fortification} - see class Javadoc.
     * The team's war flag and pet (if any) count as members too, at their
     * lower value (see {@link HeroTeam#petWarFlagScores(Fortification)}) -
     * {@link Breakdown#memberScores()} lists them first, in row order (war
     * flag, pet, heroes).
     */
    public static Breakdown scoreFor(HeroTeam team, Fortification fortification) {
        List<Double> memberScores = new ArrayList<>(team.petWarFlagScores(fortification));
        if (fortification.buff() != null) {
            HeroTeamBuffFitScore buffFitScore = HeroTeamBuffFitScore.of(team, fortification);
            team.heroes().forEach(h -> memberScores.add(buffFitScore.memberScores().get(h.id()).value()));
        } else {
            team.heroes().forEach(h -> memberScores.add(h.generalScore().value()));
        }
        return breakdownFor(memberScores, team.totalPower());
    }

    /** The TITAN-side counterpart of {@link #scoreFor(HeroTeam, Fortification)} - identical reasoning, element match instead of role match. */
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
        return breakdownFor(memberScores, team.totalPower());
    }

    private static Breakdown breakdownFor(List<Double> memberScores, int totalPower) {
        double powerTerm = totalPower / POWER_DIVISOR;
        double total = powerTerm + memberScores.stream().mapToDouble(Double::doubleValue).sum();
        return new Breakdown(memberScores, powerTerm, total);
    }

    /**
     * One team's score breakdown against one fortification. memberScores
     * are the per-member values (buffFitScore or generalScore, depending on
     * whether the fortification has a buff) that sum into total, in team
     * order - for a hero team, its war flag and pet (if any) come first, see
     * {@link #scoreFor(HeroTeam, Fortification)}; powerTerm is the totalPower-derived term added on top; total is
     * powerTerm plus the sum of memberScores.
     */
    public record Breakdown(List<Double> memberScores, double powerTerm, double total) {
    }
}
