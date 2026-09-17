package org.c2w.eval;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fourth lineup algorithm (added 2026-09-16 on Thorsten's request, see
 * Cow2Win todos 3.4 follow-up / "Meta-Algorithmus"), offered alongside
 * {@link BestPossibleLineupAlgorithm}, {@link BalancedDefenseAlgorithm} and
 * {@link CowScoreMaximizerAlgorithm} in {@link LineupAlgorithms#ALL}. Unlike
 * those three, this is not a fourth independent strategy for deciding which
 * team defends which fortification - it is a META-algorithm that runs all
 * three of them and only keeps a team's assignment when ALL THREE agree on
 * the same fortification for it. Named "Consensus picks" (Thorsten left the
 * naming to Claude): every included entry is a pick no reasonable strategy
 * among the three implemented ones disputes, so Thorsten can trust it
 * without second-guessing which algorithm's opinion to prefer.
 *
 * <p>Every team the three sub-algorithms disagree on is deliberately left
 * WITHOUT an entry - a gap in the resulting {@link Lineup}, on purpose (see
 * Thorsten's own framing of this request): those are exactly the close
 * calls where the three strategies' different priorities (raw power vs.
 * balanced defense vs. real CowScore fit) lead to different answers, so
 * this algorithm does not silently pick a "winner" between them - it hands
 * the decision back to Thorsten to make by hand in the GUI. A lineup this
 * algorithm produces is therefore, by design, usually NOT a full lineup
 * (every fortification's every slot filled) - that is the expected,
 * intentional outcome, not a bug.
 *
 * <p>Like the other three, this is additive: a team that already has an
 * entry in the {@link Lineup} handed to {@link #run} keeps that entry
 * completely untouched (it is never re-examined against the three
 * sub-algorithms' opinions - it was Thorsten's own decision, or an earlier
 * run's, already). Only teams WITHOUT an existing entry go through the
 * three-way agreement check below.
 */
public class ConsensusAlgorithm implements LineupAlgorithm {

    @Override
    public String displayName() {
        return "Consensus picks";
    }

    @Override
    public Lineup run(Lineup lineup, Guild guild) {
        List<Lineup.Entry> preexisting = lineup.entries();
        Set<TeamKey> preexistingKeys = keysOf(preexisting);

        // Each sub-algorithm runs independently from the SAME starting lineup - since all three
        // are themselves additive (never touch an existing entry), every preexisting entry
        // trivially "agrees" across all three without needing to be compared at all; only the
        // NEWLY added entries (one per still-unassigned team) can actually differ between them.
        Lineup bestPossible = new BestPossibleLineupAlgorithm().run(lineup, guild);
        Lineup balancedDefense = new BalancedDefenseAlgorithm().run(lineup, guild);
        Lineup cowScoreMaximizer = new CowScoreMaximizerAlgorithm().run(lineup, guild);

        Map<TeamKey, String> bestPossibleNew = newAssignments(preexistingKeys, bestPossible.entries());
        Map<TeamKey, String> balancedDefenseNew = newAssignments(preexistingKeys, balancedDefense.entries());
        Map<TeamKey, String> cowScoreMaximizerNew = newAssignments(preexistingKeys, cowScoreMaximizer.entries());
        // The actual Entry objects to reuse for a confirmed consensus pick - see the field-choice
        // reasoning below.
        Map<TeamKey, Lineup.Entry> cowScoreMaximizerEntries = newEntriesByKey(preexistingKeys, cowScoreMaximizer.entries());

        Set<TeamKey> everyNewlyPlacedTeam = new HashSet<>();
        everyNewlyPlacedTeam.addAll(bestPossibleNew.keySet());
        everyNewlyPlacedTeam.addAll(balancedDefenseNew.keySet());
        everyNewlyPlacedTeam.addAll(cowScoreMaximizerNew.keySet());

        List<Lineup.Entry> updatedEntries = new ArrayList<>(preexisting);
        for (TeamKey key : everyNewlyPlacedTeam) {
            String fortificationId = bestPossibleNew.get(key);
            boolean allThreeAgree = fortificationId != null
                    && fortificationId.equals(balancedDefenseNew.get(key))
                    && fortificationId.equals(cowScoreMaximizerNew.get(key));
            if (!allThreeAgree) {
                // The three strategies picked (at least) two different fortifications for this
                // team, or one of them didn't manage to place it at all - deliberately left open,
                // see class javadoc.
                continue;
            }
            // Which of the three Entry objects to keep: totalPower/fortificationId/teamIndex are
            // guaranteed identical across all three (that's what "agree" means above) - only
            // buffFitScore/weightedScore can differ, since each sub-algorithm computes those from
            // its own metric. CowScoreMaximizerAlgorithm's is the only one of the three that
            // already stores a single, genuinely comparable score for EVERY entry (bridge
            // included, see its own class Javadoc/todos Abschnitt 14) rather than a raw power
            // number or a role/element match count - so its Entry is reused here, not because its
            // strategy "won", but because its weightedScore is the only one that stays meaningful
            // across a consensus lineup mixing bridge and non-bridge picks.
            updatedEntries.add(cowScoreMaximizerEntries.get(key));
        }

        return new Lineup(lineup.guildId(), lineup.guildName(), displayName(), lineup.createdAt(), updatedEntries);
    }

    /**
     * {@code resultEntries} minus whatever is already covered by {@code preexistingKeys} - i.e.
     * only the entries THIS sub-algorithm run actually added on top of the shared starting
     * lineup - keyed by team for the three-way comparison in {@link #run}.
     */
    private static Map<TeamKey, String> newAssignments(Set<TeamKey> preexistingKeys, List<Lineup.Entry> resultEntries) {
        Map<TeamKey, String> assignments = new HashMap<>();
        for (Lineup.Entry entry : resultEntries) {
            TeamKey key = keyOf(entry);
            if (!preexistingKeys.contains(key)) {
                assignments.put(key, entry.fortificationId());
            }
        }
        return assignments;
    }

    /** Same filtering as {@link #newAssignments}, but keeping the full {@link Lineup.Entry} instead of just its fortificationId - see the field-choice comment in {@link #run}. */
    private static Map<TeamKey, Lineup.Entry> newEntriesByKey(Set<TeamKey> preexistingKeys, List<Lineup.Entry> resultEntries) {
        Map<TeamKey, Lineup.Entry> entries = new HashMap<>();
        for (Lineup.Entry entry : resultEntries) {
            TeamKey key = keyOf(entry);
            if (!preexistingKeys.contains(key)) {
                entries.put(key, entry);
            }
        }
        return entries;
    }

    private static Set<TeamKey> keysOf(List<Lineup.Entry> entries) {
        Set<TeamKey> keys = new HashSet<>();
        for (Lineup.Entry entry : entries) {
            keys.add(keyOf(entry));
        }
        return keys;
    }

    private static TeamKey keyOf(Lineup.Entry entry) {
        return new TeamKey(entry.teamMemberId(), entry.teamType(), entry.teamIndex());
    }

    /** Identifies one team the same way {@link Lineup.Entry} itself does - memberId + teamType + teamIndex, see its javadoc. */
    private record TeamKey(String memberId, Lineup.TeamType teamType, int teamIndex) {
    }
}
