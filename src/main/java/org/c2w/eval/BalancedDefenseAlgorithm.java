package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.util.TeamScoreCalculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/**
 * Second lineup algorithm (added 2026-09-15 on Thorsten's request, see
 * Cow2Win todos 3.4/"Ausgewogene Verteidigung"), offered alongside {@link
 * BestPossibleLineupAlgorithm} in {@link LineupAlgorithms#ALL} - same overall
 * shape (bridge special case, then hero/titan filled in two independent
 * passes, additive), but a genuinely different strategy for every
 * fortification OTHER than the bridge: instead of statically ranking
 * fortifications by {@link Fortification#strategicImportance()} once and
 * filling them in that fixed order, this algorithm repeatedly hands the NEXT
 * available team to whichever open fortification is currently WEAKEST - its
 * "current strength" being the sum of {@link TeamScoreCalculator#scoreFor}
 * over every team already assigned there (recomputed after every pick) -
 * aiming for an evenly-defended map instead of a small number of heavily
 * reinforced hubs. {@code strategicImportance}/unlock depth only survive as
 * a TIEBREAKER for forts that are exactly equally (usually: both still
 * empty) strong, same as {@link BestPossibleLineupAlgorithm} used them as
 * the primary criterion there.
 *
 * <p>Reuses {@link BestPossibleLineupAlgorithm}'s bridge-filling ({@link
 * BestPossibleLineupAlgorithm#assignStrongestFirst}), per-fortification team
 * selection ({@link BestPossibleLineupAlgorithm#assignOne} - buff fit still
 * decides WHICH team a buffed fortification gets, only WHICH fortification
 * goes next changes here), candidate pool building, unlock-depth computation
 * and free-slot counting rather than duplicating any of that - all widened
 * from {@code private} to package-private in {@link BestPossibleLineupAlgorithm}
 * specifically to allow this reuse.
 */
public class BalancedDefenseAlgorithm implements LineupAlgorithm {

    /** Same bridge id as {@link BestPossibleLineupAlgorithm#HERO_BRIDGE_ID} - kept in sync manually (see that field's Javadoc for why the bridge is special-cased). */
    private static final String HERO_BRIDGE_ID = "heros-bridge";

    /** Same bridge id as {@link BestPossibleLineupAlgorithm#TITAN_BRIDGE_ID}. */
    private static final String TITAN_BRIDGE_ID = "bridge";

    @Override
    public String displayName() {
        return "Balanced defense";
    }

    @Override
    public Lineup run(Lineup lineup, Guild guild) {
        // Same additive starting point as BestPossibleLineupAlgorithm.run() - only ADDS entries
        // for teams that don't have one yet, never touches/removes an existing entry.
        List<Lineup.Entry> updatedEntries = new ArrayList<>(lineup.entries());

        Map<String, Integer> unlockDepth =
                BestPossibleLineupAlgorithm.computeUnlockDepths(FortificationRepository.findAll());

        fillFortifications(FortificationType.HERO, Lineup.TeamType.HERO, HERO_BRIDGE_ID, guild, updatedEntries,
                GuildMember::heroTeams, HeroTeam::totalPower, HeroTeam::buffFitScore, HeroTeam::sortScore,
                (HeroTeam team, Fortification fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total(),
                unlockDepth);
        fillFortifications(FortificationType.TITAN, Lineup.TeamType.TITAN, TITAN_BRIDGE_ID, guild, updatedEntries,
                GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::buffFitScore, TitanTeam::sortScore,
                (TitanTeam team, Fortification fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total(),
                unlockDepth);

        return new Lineup(lineup.guildId(), lineup.guildName(), displayName(), lineup.createdAt(), updatedEntries);
    }

    private static <T> void fillFortifications(FortificationType fortificationType, Lineup.TeamType teamType,
                                                String bridgeId, Guild guild, List<Lineup.Entry> updatedEntries,
                                                Function<GuildMember, List<T>> teamsOf,
                                                ToIntFunction<T> totalPowerOf,
                                                ToIntBiFunction<T, Buff> buffFitScoreOf,
                                                ToDoubleFunction<T> sortScoreOf,
                                                ToDoubleBiFunction<T, Fortification> cowScoreOf,
                                                Map<String, Integer> unlockDepth) {

        List<BestPossibleLineupAlgorithm.Candidate<T>> pool = BestPossibleLineupAlgorithm.buildCandidatePool(
                teamType, guild, updatedEntries, teamsOf, totalPowerOf, sortScoreOf);

        List<Fortification> allOfType = FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == fortificationType)
                .toList();

        // Criterion 1 (unchanged from BestPossibleLineupAlgorithm): the bridge is still the one
        // deliberate exception to "balanced" - it is the prerequisite almost everything else on
        // this side needs unlocked, so it still claims the strongest remaining teams up front,
        // same as before. It is then excluded from the balancing loop below (see `others`) and
        // never revisited, exactly like in BestPossibleLineupAlgorithm.
        Fortification bridge = allOfType.stream()
                .filter(fortification -> fortification.id().equals(bridgeId))
                .findFirst()
                .orElse(null);
        if (bridge != null) {
            BestPossibleLineupAlgorithm.assignStrongestFirst(bridge, pool, updatedEntries, teamType);
        }

        List<Fortification> others = allOfType.stream()
                .filter(fortification -> bridge == null || !fortification.id().equals(bridge.id()))
                .toList();

        // Current CowScore-sum "strength" of every non-bridge fortification of this side,
        // seeded from whatever is ALREADY assigned there (manual picks, an earlier run, or -
        // for a fortification other than the bridge - even a previous algorithm's result), not
        // just from what this run itself adds. This is what makes the very first pick already
        // "balanced" rather than only the second/third team onward: a fortification that a human
        // already reinforced by hand starts this algorithm's competition for the next team at a
        // real disadvantage (i.e. deprioritized) compared to one nobody has touched yet.
        Map<String, Double> strength = new HashMap<>();
        for (Fortification fortification : others) {
            strength.put(fortification.id(),
                    currentStrength(fortification, updatedEntries, guild, teamType, teamsOf, cowScoreOf));
        }

        // The actual "balanced" loop: repeatedly find whichever open fortification (free
        // capacity > 0) currently has the LOWEST strength - ties broken by strategicImportance
        // descending, then unlockDepth ascending, then id, exactly BestPossibleLineupAlgorithm's
        // own tiebreaker order for `others` (see its Javadoc) - and hand it the next team via the
        // shared assignOne() (buff fit still decides which team, for a buffed fortification).
        // Unlike BestPossibleLineupAlgorithm's fixed two-phase (breadth pass, then capacity
        // passes in strategicImportance order), this single loop naturally produces breadth-first
        // coverage too EARLY ON, purely as a side effect: every still-untouched fortification
        // starts at strength 0, which is necessarily the minimum, so every fortification gets
        // its first team before any fortification gets a second - the difference only shows once
        // fortifications' strengths start to diverge (from differing team quality, buffs, or
        // pre-existing manual entries), which is exactly when "balanced" is supposed to kick in.
        while (!pool.isEmpty()) {
            Fortification target = others.stream()
                    .filter(fortification -> BestPossibleLineupAlgorithm.freeSlots(fortification, updatedEntries) > 0)
                    .min(Comparator.<Fortification>comparingDouble(fortification -> strength.get(fortification.id()))
                            .thenComparing(Comparator.comparingInt(Fortification::strategicImportance).reversed())
                            .thenComparingInt(fortification -> unlockDepth.getOrDefault(fortification.id(), Integer.MAX_VALUE))
                            .thenComparing(Fortification::id))
                    .orElse(null);
            if (target == null) {
                // No fortification of this side has any free capacity left - nothing more to do,
                // even if teams remain in the pool (e.g. more hero teams than total hero capacity).
                break;
            }
            BestPossibleLineupAlgorithm.assignOne(target, pool, updatedEntries, teamType, buffFitScoreOf);
            strength.put(target.id(), currentStrength(target, updatedEntries, guild, teamType, teamsOf, cowScoreOf));
        }
    }

    /**
     * Sums {@code cowScoreOf} (see {@link TeamScoreCalculator#scoreFor}) over
     * every team currently assigned to {@code fortification} in {@code
     * updatedEntries} - this fortification's "current strength" for the
     * balancing loop in {@link #fillFortifications}. Resolves each entry's
     * actual team object from {@code guild} rather than tracking scores
     * incrementally, so it correctly picks up pre-existing (e.g. manually
     * made) entries too, not just ones this run itself adds - see that
     * method's Javadoc on why that matters. An entry that can no longer be
     * resolved (unknown member, team index outside the current roster - a
     * stale/inconsistent lineup) is skipped rather than throwing, same
     * defensive handling as {@code BuffCalculationService}'s sum methods.
     */
    private static <T> double currentStrength(Fortification fortification, List<Lineup.Entry> updatedEntries,
                                               Guild guild, Lineup.TeamType teamType,
                                               Function<GuildMember, List<T>> teamsOf,
                                               ToDoubleBiFunction<T, Fortification> cowScoreOf) {
        double total = 0;
        for (Lineup.Entry entry : updatedEntries) {
            if (!entry.fortificationId().equals(fortification.id()) || entry.teamType() != teamType) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null) {
                continue;
            }
            List<T> teams = teamsOf.apply(member);
            if (entry.teamIndex() >= teams.size()) {
                continue;
            }
            total += cowScoreOf.applyAsDouble(teams.get(entry.teamIndex()), fortification);
        }
        return total;
    }

    /** Finds a guild member by their ID - same one-line lookup as {@code BuffCalculationService#findMemberById}, duplicated here since that one is private to its own class. */
    private static GuildMember findMemberById(Guild guild, String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .orElse(null);
    }
}
