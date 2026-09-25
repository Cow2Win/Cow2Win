package org.c2w.eval;

import org.c2w.data.model.*;

import java.util.List;
import java.util.Map;

/**
 * First lineup strategy: bridge first with the strongest teams, then
 * breadth-first coverage of every other fortification in a fixed
 * strategicImportance/unlockDepth/id order, then remaining capacity topped
 * up in that same order. Buffed fortifications get the best buff fit,
 * unbuffed ones the weakest remaining team.
 *
 * <p>Bound to one {@link TeamSide} per instance (since 2026-09-24) - use
 * {@code new BestPossibleLineupAlgorithm<>(TeamSide.HERO)} for the hero part
 * and {@code new BestPossibleLineupAlgorithm<>(TeamSide.TITAN)} for the titan
 * part of a lineup (see {@link LineupAlgorithms}). All pool/assignment
 * helpers live in {@link AbstractLineupAlgorithm}.
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class BestPossibleLineupAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    public BestPossibleLineupAlgorithm(TeamSide<T> side) {
        super(side);
    }

    @Override
    public String displayName() {
        return "Best possible lineup";
    }

    @Override
    protected void fillFortifications(Fortification bridge, List<Fortification> unsortedOthers,
                                      List<Candidate<T>> pool, List<Lineup.Entry> updatedEntries,
                                      Guild guild, Map<String, Integer> unlockDepth) {
        Lineup.TeamType teamType = side().teamType();

        // Every other fortification of this side, ranked by strategicImportance descending
        // (unlockDepth/id as tiebreakers, see byImportanceThenDepth) - this ordering drives both
        // the coverage pass (Criterion 2) and the fill-remaining-capacity pass (Criteria 3-5):
        // the more important a fortification is as a hub, the earlier it gets picked from.
        List<Fortification> others = unsortedOthers.stream()
                .sorted(byImportanceThenDepth(unlockDepth))
                .toList();

        // Criterion 1: the bridge claims the strongest teams before anything else gets a look-in.
        if (bridge != null) {
            assignStrongestFirst(bridge, pool, updatedEntries, teamType);
        }

        // Criterion 2 (+4 for the order): every other fortification gets its FIRST team,
        // highest strategicImportance first, before any of them gets a second - breadth (every
        // fortification defended at least once) is prioritized over depth before capacity is
        // topped up in the loop below.
        for (Fortification fortification : others) {
            if (pool.isEmpty()) {
                break;
            }
            if (freeSlots(fortification, updatedEntries) <= 0) {
                // Fully defended already (e.g. by manual entries made before this run).
                continue;
            }
            if (isCovered(fortification, updatedEntries)) {
                // Already has at least one defender (manual pick, or this very loop) - its
                // "first team" is taken care of.
                continue;
            }
            assignOne(fortification, pool, updatedEntries, teamType, side().buffFitScoreOf());
        }

        // Criteria 3 + 4 + 5: fill whatever capacity is left, one extra team per fortification
        // per pass (so strategicImportance keeps deciding who gets the NEXT team), buffed
        // fortifications preferring the best-fitting remaining team, everyone else taking
        // whichever remaining team is weakest. Repeats full passes until either the pool is
        // drained or a whole pass added nothing (every fortification's capacity exhausted).
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
                // assignOne() itself decides buff-fit vs. lowest sortScore (Criteria 3/5).
                assignOne(fortification, pool, updatedEntries, teamType, side().buffFitScoreOf());
                progress = true;
            }
        }
    }
}
