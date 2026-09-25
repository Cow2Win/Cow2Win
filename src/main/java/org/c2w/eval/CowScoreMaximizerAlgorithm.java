package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.util.TeamScoreCalculator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Third lineup strategy (added 2026-09-16 on Thorsten's request, see
 * Cow2Win todos 3.4 follow-up / "CowScore-Maximierer statt
 * Power-Maximierer").
 *
 * <p>{@link BestPossibleLineupAlgorithm} actually uses THREE different,
 * inconsistent metrics depending on the decision: raw {@code totalPower} for
 * the bridge, a simple integer role/element MATCH COUNT ({@link
 * HeroTeam#buffFitScore}/{@link TitanTeam#buffFitScore}) for a buffed
 * fortification - notably NOT each hero's/titan's actual, manually curated
 * {@link CowScore#buffFitScores()} tier - and {@link HeroTeam#sortScore()}/
 * {@link TitanTeam#sortScore()} for an unbuffed fortification. This
 * algorithm instead uses exactly ONE metric for every single decision,
 * bridge included: {@link TeamScoreCalculator#scoreFor}'s {@code total()} -
 * the same CowScore-plus-power figure the report and {@code
 * LineupSummaryPanel} show.
 *
 * <p>Same overall shape as {@link BestPossibleLineupAlgorithm} otherwise
 * (bridge first, breadth-first coverage before topping up remaining
 * capacity, strategicImportance/unlockDepth/id decide fortification
 * VISITATION order - only WHICH candidate wins a given fortification
 * changes) and additive in the same way. Bound to one {@link TeamSide} per
 * instance (since 2026-09-24). {@link #assignStrongestFirst}/{@link
 * #assignOne} are deliberately NOT used here, since their whole point is the
 * metric this algorithm replaces.
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class CowScoreMaximizerAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    public CowScoreMaximizerAlgorithm(TeamSide<T> side) {
        super(side);
    }

    @Override
    public String displayName() {
        return "CowScore maximizer";
    }

    @Override
    protected void fillFortifications(Fortification bridge, List<Fortification> unsortedOthers,
                                      List<Candidate<T>> pool, List<Lineup.Entry> updatedEntries,
                                      Guild guild, Map<String, Integer> unlockDepth) {
        // Same visitation order as BestPossibleLineupAlgorithm - this algorithm changes WHICH
        // candidate wins a fortification, not the order fortifications are visited in.
        List<Fortification> others = unsortedOthers.stream()
                .sorted(byImportanceThenDepth(unlockDepth))
                .toList();

        // Criterion 1 equivalent: the bridge still goes first, but by scoreFor(bridge).total()
        // instead of raw totalPower. Loops to fill the bridge's ENTIRE free capacity, since a
        // single assignBestByScore() call only ever assigns ONE team.
        if (bridge != null) {
            while (freeSlots(bridge, updatedEntries) > 0 && !pool.isEmpty()) {
                assignBestByScore(bridge, pool, updatedEntries);
            }
        }

        // Criterion 2 equivalent: breadth pass - every fortification gets its FIRST team, in
        // strategicImportance order, before any of them gets a second.
        for (Fortification fortification : others) {
            if (pool.isEmpty()) {
                break;
            }
            if (freeSlots(fortification, updatedEntries) <= 0 || isCovered(fortification, updatedEntries)) {
                continue;
            }
            assignBestByScore(fortification, pool, updatedEntries);
        }

        // Criteria 3-5 equivalent: fill remaining capacity, one extra team per fortification per
        // pass, same repeated-full-passes structure as BestPossibleLineupAlgorithm.
        boolean progress = true;
        while (progress && !pool.isEmpty()) {
            progress = false;
            for (Fortification fortification : others) {
                if (pool.isEmpty()) {
                    break;
                }
                if (freeSlots(fortification, updatedEntries) <= 0) {
                    continue;
                }
                assignBestByScore(fortification, pool, updatedEntries);
                progress = true;
            }
        }
    }

    /**
     * Assigns {@code fortification} the pool candidate with the HIGHEST
     * {@link TeamSide#cowScoreOf()} value. Ties broken by LOWEST totalPower
     * first (same "conserve high-power teams in the pool for later"
     * philosophy as {@link #assignOne}'s Criterion 3), then
     * memberId/teamIndex for a fully deterministic result.
     */
    private void assignBestByScore(Fortification fortification, List<Candidate<T>> pool,
                                   List<Lineup.Entry> updatedEntries) {
        Candidate<T> chosen = pool.stream()
                .sorted(Comparator.<Candidate<T>>comparingDouble(
                                candidate -> side().cowScoreOf().applyAsDouble(candidate.team(), fortification))
                        .reversed()
                        .thenComparingInt(Candidate::totalPower)
                        .thenComparing(Candidate::memberId)
                        .thenComparingInt(Candidate::teamIndex))
                .findFirst()
                .orElseThrow();

        pool.remove(chosen);
        updatedEntries.add(new Lineup.Entry(fortification.id(), chosen.memberId(), side().teamType(), chosen.teamIndex()));
    }
}
