package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.util.TeamScoreCalculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Third lineup algorithm (added 2026-09-16 on Thorsten's request, see
 * Cow2Win todos 3.4 follow-up / "CowScore-Maximierer statt
 * Power-Maximierer"), offered alongside {@link BestPossibleLineupAlgorithm}
 * and {@link BalancedDefenseAlgorithm} in {@link LineupAlgorithms#ALL}.
 *
 * <p>{@link BestPossibleLineupAlgorithm} actually uses THREE different,
 * inconsistent metrics depending on the decision: raw {@code totalPower} for
 * the bridge (Criterion 1 - a pure "Power-Maximierer" for that one
 * fortification, hence this algorithm's name), a simple integer role/element
 * MATCH COUNT ({@link HeroTeam#buffFitScore}/{@link TitanTeam#buffFitScore})
 * for a buffed fortification (Criterion 3) - notably NOT each hero's/titan's
 * actual, manually curated {@link CowScore#buffFitScores()} tier, and
 * {@link HeroTeam#sortScore()}/{@link TitanTeam#sortScore()} (generalScore +
 * scaled-down power) for an unbuffed fortification (Criterion 5). This
 * algorithm instead uses exactly ONE metric for every single decision, bridge
 * included: {@link TeamScoreCalculator#scoreFor}'s {@code total()} - the same
 * CowScore-plus-power figure the "Statistics"/"Fortifications" sections of
 * {@code ReportGenerator}'s report and {@code LineupSummaryPanel} already
 * show, but which no algorithm used for the actual assignment decision until
 * now. Concretely this means: the bridge no longer simply grabs the highest-
 * power teams (an average-power team with excellent CowScore tiers can now
 * outrank a higher-power team with mediocre ones), and - the more consequential
 * change - a buffed fortification's team is chosen by each candidate's real,
 * manually curated buff-fit CowScore tier instead of a blunt "how many
 * heroes/titans merely happen to have the right role/element" count.
 *
 * <p>Same overall shape as {@link BestPossibleLineupAlgorithm} otherwise
 * (bridge special case, then hero/titan filled in two independent passes,
 * breadth-first coverage before topping up remaining capacity,
 * strategicImportance/unlockDepth/id still decide fortification VISITATION
 * order exactly as before - only WHICH candidate wins a given fortification
 * changes) and additive in the same way. Reuses {@link
 * BestPossibleLineupAlgorithm#buildCandidatePool}, {@link
 * BestPossibleLineupAlgorithm#freeSlots} and {@link
 * BestPossibleLineupAlgorithm#computeUnlockDepths} rather than duplicating
 * them - {@link #assignStrongestFirst}/{@link #assignOne} are NOT reused
 * here, since their whole point is the metric this algorithm deliberately
 * replaces.
 */
public class CowScoreMaximizerAlgorithm implements LineupAlgorithm {

    /** Same bridge id as {@link BestPossibleLineupAlgorithm#HERO_BRIDGE_ID}. */
    private static final String HERO_BRIDGE_ID = "heros-bridge";

    /** Same bridge id as {@link BestPossibleLineupAlgorithm#TITAN_BRIDGE_ID}. */
    private static final String TITAN_BRIDGE_ID = "bridge";

    @Override
    public String displayName() {
        return "CowScore maximizer";
    }

    @Override
    public Lineup run(Lineup lineup, Guild guild) {
        // Same additive starting point as the other two algorithms - only ADDS entries for teams
        // that don't have one yet, never touches/removes an existing entry.
        List<Lineup.Entry> updatedEntries = new ArrayList<>(lineup.entries());

        Map<String, Integer> unlockDepth =
                BestPossibleLineupAlgorithm.computeUnlockDepths(FortificationRepository.findAll());

        fillFortifications(FortificationType.HERO, Lineup.TeamType.HERO, HERO_BRIDGE_ID, guild, updatedEntries,
                GuildMember::heroTeams, HeroTeam::totalPower, HeroTeam::sortScore,
                (HeroTeam team, Fortification fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total(),
                unlockDepth);
        fillFortifications(FortificationType.TITAN, Lineup.TeamType.TITAN, TITAN_BRIDGE_ID, guild, updatedEntries,
                GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::sortScore,
                (TitanTeam team, Fortification fortification) -> TeamScoreCalculator.scoreFor(team, fortification).total(),
                unlockDepth);

        return new Lineup(lineup.guildId(), lineup.guildName(), displayName(), lineup.createdAt(), updatedEntries);
    }

    private static <T> void fillFortifications(FortificationType fortificationType, Lineup.TeamType teamType,
                                                String bridgeId, Guild guild, List<Lineup.Entry> updatedEntries,
                                                Function<GuildMember, List<T>> teamsOf,
                                                ToIntFunction<T> totalPowerOf,
                                                ToDoubleFunction<T> sortScoreOf,
                                                ToDoubleBiFunction<T, Fortification> cowScoreOf,
                                                Map<String, Integer> unlockDepth) {

        List<BestPossibleLineupAlgorithm.Candidate<T>> pool = BestPossibleLineupAlgorithm.buildCandidatePool(
                teamType, guild, updatedEntries, teamsOf, totalPowerOf, sortScoreOf);

        List<Fortification> allOfType = FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == fortificationType)
                .toList();

        Fortification bridge = allOfType.stream()
                .filter(fortification -> fortification.id().equals(bridgeId))
                .findFirst()
                .orElse(null);

        // Same strategicImportance-primary, unlockDepth/id-tiebreak visitation order as
        // BestPossibleLineupAlgorithm's `others` - this algorithm changes WHICH candidate wins a
        // fortification, not the order fortifications are visited in.
        List<Fortification> others = allOfType.stream()
                .filter(fortification -> bridge == null || !fortification.id().equals(bridge.id()))
                .sorted(Comparator.comparingInt(Fortification::strategicImportance).reversed()
                        .thenComparingInt(fortification -> unlockDepth.getOrDefault(fortification.id(), Integer.MAX_VALUE))
                        .thenComparing(Fortification::id))
                .toList();

        // Criterion 1 equivalent: the bridge still goes first, but by scoreFor(bridge).total()
        // instead of raw totalPower - see class Javadoc for why that can pick a different team
        // than BestPossibleLineupAlgorithm would. Must loop to fill the bridge's ENTIRE free
        // capacity here (mirroring BestPossibleLineupAlgorithm#assignStrongestFirst, which does
        // the same internally) - a single assignBestByScore() call only ever assigns ONE team.
        if (bridge != null) {
            while (BestPossibleLineupAlgorithm.freeSlots(bridge, updatedEntries) > 0 && !pool.isEmpty()) {
                assignBestByScore(bridge, pool, updatedEntries, teamType, cowScoreOf);
            }
        }

        // Criterion 2 equivalent: breadth pass - every fortification gets its FIRST team, in
        // strategicImportance order, before any of them gets a second (identical structure to
        // BestPossibleLineupAlgorithm's coverage pass, see its Javadoc for the reasoning).
        for (Fortification fortification : others) {
            if (pool.isEmpty()) {
                break;
            }
            if (BestPossibleLineupAlgorithm.freeSlots(fortification, updatedEntries) <= 0) {
                continue;
            }
            boolean alreadyCovered = updatedEntries.stream()
                    .anyMatch(entry -> entry.fortificationId().equals(fortification.id()));
            if (alreadyCovered) {
                continue;
            }
            assignBestByScore(fortification, pool, updatedEntries, teamType, cowScoreOf);
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
                if (BestPossibleLineupAlgorithm.freeSlots(fortification, updatedEntries) <= 0) {
                    continue;
                }
                assignBestByScore(fortification, pool, updatedEntries, teamType, cowScoreOf);
                progress = true;
            }
        }
    }

    /**
     * Assigns {@code fortification} the pool candidate with the HIGHEST
     * {@code cowScoreOf} value - {@link TeamScoreCalculator#scoreFor}'s
     * {@code total()}, the single metric this whole algorithm uses for every
     * decision (see class Javadoc). Ties broken by LOWEST totalPower first
     * (same "conserve high-power teams in the pool for later" philosophy as
     * {@link BestPossibleLineupAlgorithm#assignOne}'s Criterion 3), then
     * memberId/teamIndex for a fully deterministic result.
     *
     * <p>Sets the resulting {@link Lineup.Entry#buffFitScore()} to 0 -
     * unlike {@link BestPossibleLineupAlgorithm#assignOne}, this algorithm
     * never reasons in role/element MATCH COUNT units at any point, so there
     * is no meaningful count to store there; {@link
     * Lineup.Entry#weightedScore()} is set to the actual {@code cowScoreOf}
     * value that decided this pick - for EVERY fortification, bridge
     * included, unlike {@link BestPossibleLineupAlgorithm}, where the
     * bridge's {@code weightedScore} is raw totalPower (a much larger,
     * differently-scaled number than every other entry's). That makes every
     * entry's {@code weightedScore} genuinely comparable to every other one
     * in a lineup produced by this algorithm - useful for {@code
     * LineupComparisonService}, which reads {@code weightedScore} to decide
     * whether a team's assignment to the same fortification counts as
     * "UPDATED" between two lineups.
     */
    private static <T> void assignBestByScore(Fortification fortification, List<BestPossibleLineupAlgorithm.Candidate<T>> pool,
                                               List<Lineup.Entry> updatedEntries, Lineup.TeamType teamType,
                                               ToDoubleBiFunction<T, Fortification> cowScoreOf) {
        BestPossibleLineupAlgorithm.Candidate<T> chosen = pool.stream()
                .sorted(Comparator.<BestPossibleLineupAlgorithm.Candidate<T>>comparingDouble(
                                candidate -> cowScoreOf.applyAsDouble(candidate.team(), fortification))
                        .reversed()
                        .thenComparingInt(BestPossibleLineupAlgorithm.Candidate::totalPower)
                        .thenComparing(BestPossibleLineupAlgorithm.Candidate::memberId)
                        .thenComparingInt(BestPossibleLineupAlgorithm.Candidate::teamIndex))
                .findFirst()
                .orElseThrow();

        double score = cowScoreOf.applyAsDouble(chosen.team(), fortification);
        pool.remove(chosen);
        updatedEntries.add(new Lineup.Entry(fortification.id(), chosen.memberId(), teamType, chosen.teamIndex(),
                chosen.totalPower(), 0, score));
    }
}
