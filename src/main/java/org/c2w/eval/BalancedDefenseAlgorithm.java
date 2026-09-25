package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.util.TeamScoreCalculator;

import java.util.*;

/**
 * Second lineup strategy (added 2026-09-15 on Thorsten's request, see
 * Cow2Win todos 3.4/"Ausgewogene Verteidigung") - same overall shape as
 * {@link BestPossibleLineupAlgorithm} (bridge special case, additive), but a
 * genuinely different strategy for every fortification OTHER than the
 * bridge: instead of statically ranking fortifications by {@link
 * Fortification#strategicImportance()} once and filling them in that fixed
 * order, this algorithm repeatedly hands the NEXT available team to
 * whichever open fortification is currently WEAKEST - its "current
 * strength" being the sum of {@link TeamScoreCalculator#scoreFor} over every
 * team already assigned there (recomputed after every pick) - aiming for an
 * evenly-defended map instead of a small number of heavily reinforced hubs.
 * {@code strategicImportance}/unlock depth only survive as a TIEBREAKER for
 * forts that are exactly equally (usually: both still empty) strong.
 *
 * <p>Bound to one {@link TeamSide} per instance (since 2026-09-24). Reuses
 * {@link AbstractLineupAlgorithm}'s bridge-filling ({@link #assignStrongestFirst})
 * and per-fortification team selection ({@link #assignOne} - buff fit still
 * decides WHICH team a buffed fortification gets, only WHICH fortification
 * goes next changes here).
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class BalancedDefenseAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    public BalancedDefenseAlgorithm(TeamSide<T> side) {
        super(side);
    }

    @Override
    public String displayName() {
        return "Balanced defense";
    }

    @Override
    protected void fillFortifications(Fortification bridge, List<Fortification> others,
                                      List<Candidate<T>> pool, List<Lineup.Entry> updatedEntries,
                                      Guild guild, Map<String, Integer> unlockDepth) {
        Lineup.TeamType teamType = side().teamType();

        // Criterion 1 (unchanged from BestPossibleLineupAlgorithm): the bridge is the one
        // deliberate exception to "balanced" - it is the prerequisite almost everything else on
        // this side needs unlocked, so it still claims the strongest remaining teams up front.
        // It is not part of `others` and is never revisited.
        if (bridge != null) {
            assignStrongestFirst(bridge, pool, updatedEntries, teamType);
        }

        // Current CowScore-sum "strength" of every non-bridge fortification of this side,
        // seeded from whatever is ALREADY assigned there (manual picks, an earlier run), not
        // just from what this run itself adds - a fortification that a human already reinforced
        // by hand starts this algorithm's competition for the next team at a real disadvantage.
        Map<String, Double> strength = new HashMap<>();
        for (Fortification fortification : others) {
            strength.put(fortification.id(), currentStrength(fortification, updatedEntries, guild));
        }

        Comparator<Fortification> tiebreak = byImportanceThenDepth(unlockDepth);

        // The actual "balanced" loop: repeatedly find whichever open fortification (free
        // capacity > 0) currently has the LOWEST strength - ties broken by strategicImportance
        // descending, then unlockDepth ascending, then id - and hand it the next team via the
        // shared assignOne(). Every still-untouched fortification starts at strength 0, so every
        // fortification gets its first team before any gets a second; the difference to
        // BestPossibleLineupAlgorithm only shows once strengths start to diverge.
        while (!pool.isEmpty()) {
            Fortification target = others.stream()
                    .filter(fortification -> freeSlots(fortification, updatedEntries) > 0)
                    .min(Comparator.<Fortification>comparingDouble(fortification -> strength.get(fortification.id()))
                            .thenComparing(tiebreak))
                    .orElse(null);
            if (target == null) {
                // No fortification of this side has any free capacity left.
                break;
            }
            assignOne(target, pool, updatedEntries, teamType, side().buffFitScoreOf());
            strength.put(target.id(), currentStrength(target, updatedEntries, guild));
        }
    }

    /**
     * Sums {@link TeamSide#cowScoreOf()} over every team of this side
     * currently assigned to {@code fortification} - this fortification's
     * "current strength" for the balancing loop. Resolves each entry's actual
     * team object from {@code guild} rather than tracking scores
     * incrementally, so it correctly picks up pre-existing (e.g. manually
     * made) entries too. An entry that can no longer be resolved (unknown
     * member, team index outside the current roster) is skipped rather than
     * throwing, same defensive handling as {@code BuffCalculationService}'s
     * sum methods.
     */
    private double currentStrength(Fortification fortification, List<Lineup.Entry> updatedEntries, Guild guild) {
        double total = 0;
        for (Lineup.Entry entry : updatedEntries) {
            if (!entry.fortificationId().equals(fortification.id()) || entry.teamType() != side().teamType()) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null) {
                continue;
            }
            List<T> teams = side().teamsOf().apply(member);
            if (entry.teamIndex() >= teams.size()) {
                continue;
            }
            total += side().cowScoreOf().applyAsDouble(teams.get(entry.teamIndex()), fortification);
        }
        return total;
    }
}
