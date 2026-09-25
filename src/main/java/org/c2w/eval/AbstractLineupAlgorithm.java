package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

/**
 * Shared base of every "real" lineup strategy ({@link BestPossibleLineupAlgorithm},
 * {@link BalancedDefenseAlgorithm}, {@link CowScoreMaximizerAlgorithm}) -
 * extracted (2026-09-24) when the algorithms were split into separate
 * HERO-only and TITAN-only variants. Each instance is bound to one {@link
 * TeamSide}, so e.g. {@code new BestPossibleLineupAlgorithm<>(TeamSide.HERO)}
 * and {@code new BalancedDefenseAlgorithm<>(TeamSide.TITAN)} can fill the two
 * halves of the same lineup.
 *
 * <p>{@link #run} is the common template every strategy shares: start from
 * the existing entries (additive - never touches an existing entry), build
 * the candidate pool of still-unassigned teams of this side, split this
 * side's fortifications into its bridge and all others, and hand those to
 * the strategy-specific {@link #fillFortifications}. The static helpers
 * below (pool building, bridge filling, per-fortification team selection,
 * free-slot counting, unlock depths) used to live in {@link
 * BestPossibleLineupAlgorithm} and were widened to package-private there so
 * the other strategies could reuse them; they now live here instead.
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}, matching {@link #side()}
 */
public abstract class AbstractLineupAlgorithm<T> implements LineupAlgorithm {

    private final TeamSide<T> side;

    protected AbstractLineupAlgorithm(TeamSide<T> side) {
        this.side = Objects.requireNonNull(side, "side");
    }

    /** The side (hero or titan) this instance assigns teams for. */
    public TeamSide<T> side() {
        return side;
    }

    @Override
    public Lineup.TeamType teamType() {
        return side.teamType();
    }

    @Override
    public Lineup run(Lineup lineup, Guild guild) {
        // Start from whatever is already assigned (manual picks made via the GUI, the other
        // side's entries, an earlier run) and only ADD entries for teams of THIS side that don't
        // have one yet - fillFortifications() never touches/removes an existing entry.
        List<Lineup.Entry> updatedEntries = new ArrayList<>(lineup.entries());

        // Unlock depth (see computeUnlockDepths' javadoc) depends on the FULL catalog, since
        // prerequisites can cross HERO/TITAN (e.g. "bastion" is HERO but only unlocks once one
        // of three TITAN bastions falls) - so it is always computed over both sides, even
        // though this instance only fills one of them.
        List<Fortification> catalog = FortificationRepository.findAll();
        Map<String, Integer> unlockDepth = computeUnlockDepths(catalog);

        // Every team of this side that isn't already sitting on some fortification.
        List<Candidate<T>> pool = buildCandidatePool(side, guild, updatedEntries);

        // All catalog fortifications of this side. A full defensive lineup is planned for EVERY
        // fortification of this type, not just already-unlocked ones - capture order only
        // influences the ORDER fortifications are picked from (via unlockDepth).
        List<Fortification> allOfType = catalog.stream()
                .filter(fortification -> fortification.type() == side.fortificationType())
                .toList();

        // The strategic choke point almost everything else on this side depends on - every
        // strategy special-cases it.
        Fortification bridge = allOfType.stream()
                .filter(fortification -> fortification.id().equals(side.bridgeId()))
                .findFirst()
                .orElse(null);

        List<Fortification> others = allOfType.stream()
                .filter(fortification -> bridge == null || !fortification.id().equals(bridge.id()))
                .toList();

        fillFortifications(bridge, others, pool, updatedEntries, guild, unlockDepth);

        return new Lineup(lineup.guildId(), lineup.guildName(),
                LineupAlgorithms.combinedAlgorithmName(lineup.algorithmName(), teamType(), displayName()),
                lineup.createdAt(), updatedEntries);
    }

    /**
     * The actual strategy: assigns teams from {@code pool} to {@code bridge}
     * and {@code others} by appending to {@code updatedEntries}, removing
     * every placed team from {@code pool}.
     *
     * @param bridge         this side's bridge, or {@code null} if the catalog has none
     * @param others         every other fortification of this side, in catalog order (unsorted)
     * @param pool           still-unassigned teams of this side (mutable)
     * @param updatedEntries every entry so far, both sides (mutable, append-only)
     * @param guild          the guild the teams belong to
     * @param unlockDepth    see {@link #computeUnlockDepths}
     */
    protected abstract void fillFortifications(Fortification bridge, List<Fortification> others,
                                               List<Candidate<T>> pool, List<Lineup.Entry> updatedEntries,
                                               Guild guild, Map<String, Integer> unlockDepth);

    // --- shared helpers ---------------------------------------------------

    /**
     * The fixed fortification visitation order shared by {@link
     * BestPossibleLineupAlgorithm} and {@link CowScoreMaximizerAlgorithm}:
     * strategicImportance descending (PRIMARY criterion, per the user's
     * decision), then unlockDepth ascending (among equally important
     * fortifications prefer the one an opponent can reach sooner), then id
     * for a fully deterministic result. {@link BalancedDefenseAlgorithm} uses
     * the same comparator as its tiebreaker.
     */
    static Comparator<Fortification> byImportanceThenDepth(Map<String, Integer> unlockDepth) {
        return Comparator.comparingInt(Fortification::strategicImportance).reversed()
                .thenComparingInt((Fortification fortification) ->
                        unlockDepth.getOrDefault(fortification.id(), Integer.MAX_VALUE))
                .thenComparing(Fortification::id);
    }

    /** True if {@code fortification} already has at least one defender in {@code updatedEntries} (any source). */
    static boolean isCovered(Fortification fortification, List<Lineup.Entry> updatedEntries) {
        return updatedEntries.stream().anyMatch(entry -> entry.fortificationId().equals(fortification.id()));
    }

    /**
     * Collects one {@link Candidate} per team of {@code side} across the WHOLE
     * guild, except teams that already have an entry in {@code updatedEntries}
     * (identified by memberId + teamType + teamIndex, the same triple {@link
     * Lineup.Entry} uses). This is what makes every algorithm additive: a team
     * assigned manually (or by an earlier run) never even enters the pool, so
     * it can neither be reassigned nor counted twice, and every {@code assign*}
     * helper only ever removes from - never adds back to - this same pool.
     */
    static <T> List<Candidate<T>> buildCandidatePool(TeamSide<T> side, Guild guild,
                                                     List<Lineup.Entry> updatedEntries) {
        List<Candidate<T>> pool = new ArrayList<>();
        for (GuildMember member : guild.members()) {
            // At most 3 hero teams or 2 titan teams, per the Clash of Worlds cap enforced in
            // GuildMember's constructor.
            List<T> teams = side.teamsOf().apply(member);
            for (int teamIndex = 0; teamIndex < teams.size(); teamIndex++) {
                final int currentTeamIndex = teamIndex;
                boolean alreadyAssigned = updatedEntries.stream().anyMatch(entry ->
                        entry.teamMemberId().equals(member.id())
                                && entry.teamType() == side.teamType()
                                && entry.teamIndex() == currentTeamIndex);
                if (alreadyAssigned) {
                    // This exact team already defends some fortification - leave that assignment
                    // untouched and don't offer it again.
                    continue;
                }
                T team = teams.get(currentTeamIndex);
                // totalPower/sortScore cached once so the comparators can sort on them directly.
                pool.add(new Candidate<>(member.id(), team, currentTeamIndex,
                        side.totalPowerOf().applyAsInt(team), side.sortScoreOf().applyAsDouble(team)));
            }
        }
        return pool;
    }

    /**
     * Criterion 1 of {@link BestPossibleLineupAlgorithm}/{@link BalancedDefenseAlgorithm}:
     * fills {@code bridge}'s free slots with the strongest remaining
     * candidates (highest {@link Candidate#totalPower()} first, ties broken by
     * memberId/teamIndex purely for a deterministic result). Assigned
     * candidates are removed from {@code pool}. The bridge never carries a
     * buff itself, so pure totalPower is the selection criterion here.
     *
     * <p>Package-private so {@code BestPossibleLineupAlgorithmAssignmentTest}
     * can exercise it directly with a synthetic {@link Fortification}/pool.
     */
    static <T> void assignStrongestFirst(Fortification bridge, List<Candidate<T>> pool,
                                         List<Lineup.Entry> updatedEntries, Lineup.TeamType teamType) {
        List<Candidate<T>> strongestFirst = pool.stream()
                .sorted(Comparator.<Candidate<T>>comparingInt(Candidate::totalPower).reversed()
                        .thenComparing(Candidate::memberId)
                        .thenComparingInt(Candidate::teamIndex))
                .toList();

        int free = freeSlots(bridge, updatedEntries);
        for (Candidate<T> candidate : strongestFirst) {
            if (free <= 0) {
                break;
            }
            updatedEntries.add(new Lineup.Entry(bridge.id(), candidate.memberId(), teamType, candidate.teamIndex()));
            pool.remove(candidate);
            free--;
        }
    }

    /**
     * Assigns exactly ONE remaining candidate to {@code fortification}. If the
     * fortification has a buff (criterion 3), the candidate with the highest
     * {@code buffFitScoreOf} for that buff is picked (ties broken by lowest
     * totalPower first, then memberId/teamIndex, so strong teams are kept in
     * the pool as long as possible even when several candidates fit a buff
     * equally well). Otherwise (criterion 5), the candidate with the lowest
     * {@code sortScore} (see {@link HeroTeam#sortScore()}/{@link
     * TitanTeam#sortScore()}) is picked outright, so the ordinary, unbuffed
     * fortifications end up with whatever teams are weakest/least valuable.
     * Either way, the chosen candidate is removed from {@code pool} and a new
     * {@link Lineup.Entry} is appended to {@code updatedEntries}.
     *
     * <p>Package-private so {@code BestPossibleLineupAlgorithmAssignmentTest}
     * can exercise it directly with a synthetic {@link Fortification}/pool.
     */
    static <T> void assignOne(Fortification fortification, List<Candidate<T>> pool,
                              List<Lineup.Entry> updatedEntries, Lineup.TeamType teamType,
                              ToIntBiFunction<T, Buff> buffFitScoreOf) {
        Buff buff = fortification.buff();
        Candidate<T> chosen;

        if (buff != null) {
            // Criterion 3: buffed fortification - best buff fit wins, totalPower only breaks
            // ties (so among equally-fitting candidates the WEAKEST is picked, keeping the
            // strongest teams in the pool for other buffed fortifications).
            chosen = pool.stream()
                    .sorted(Comparator.<Candidate<T>>comparingInt(candidate -> buffFitScoreOf.applyAsInt(candidate.team(), buff))
                            .reversed()
                            .thenComparingInt(Candidate::totalPower)
                            .thenComparing(Candidate::memberId)
                            .thenComparingInt(Candidate::teamIndex))
                    .findFirst()
                    .orElseThrow();
        } else {
            // Criterion 5: unbuffed fortification - it gets whatever is left over at the bottom
            // of the pool (by sortScore, not raw totalPower), letting the strongest/most valuable
            // remaining teams stay available for buffed fortifications and the bridge.
            chosen = pool.stream()
                    .sorted(Comparator.<Candidate<T>>comparingDouble(Candidate::sortScore)
                            .thenComparing(Candidate::memberId)
                            .thenComparingInt(Candidate::teamIndex))
                    .findFirst()
                    .orElseThrow();
        }

        pool.remove(chosen);
        updatedEntries.add(new Lineup.Entry(fortification.id(), chosen.memberId(), teamType, chosen.teamIndex()));
    }

    /** Free capacity of {@code fortification} given every entry already in {@code updatedEntries} (any source). */
    static int freeSlots(Fortification fortification, List<Lineup.Entry> updatedEntries) {
        long used = updatedEntries.stream()
                .filter(entry -> entry.fortificationId().equals(fortification.id()))
                .count();
        return (int) (fortification.capacity() - used);
    }

    /** Finds a guild member by their ID, or {@code null} if there is none. */
    static GuildMember findMemberById(Guild guild, String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Computes, for every fortification in {@code catalog}, its "unlock
     * depth": the minimum number of captures needed before it can be
     * attacked, with an {@link Fortification#isInitiallyAttackable()}
     * fortification (no prerequisites) at depth 0. Per
     * {@link Fortification#prerequisites()}'s javadoc, prerequisites are an
     * OR-relation - capturing ANY ONE of them unlocks the fortification - so
     * a fortification's depth is 1 + the SHALLOWEST of its prerequisites'
     * depths, not the deepest (e.g. "heros-bridge" needs only one of its
     * three titan-bastion prerequisites captured, not all three). Since
     * prerequisites can cross HERO/TITAN, this is computed over the FULL
     * catalog rather than per side.
     *
     * <p>Used purely as a tiebreaker (after strategicImportance, before id) so
     * that, among equally important fortifications, the one an opponent can
     * reach sooner is prioritized for team assignment.
     *
     * <p>Package-private so {@code BestPossibleLineupAlgorithmTest} can
     * exercise it directly.
     */
    static Map<String, Integer> computeUnlockDepths(List<Fortification> catalog) {
        Map<String, Fortification> byId = catalog.stream()
                .collect(Collectors.toMap(Fortification::id, Function.identity()));
        Map<String, Integer> depths = new HashMap<>();
        for (String id : byId.keySet()) {
            computeUnlockDepth(id, byId, depths, new HashSet<>());
        }
        return depths;
    }

    /**
     * Recursive, memoized depth lookup for one fortification - see
     * {@link #computeUnlockDepths}. {@code inProgress} guards against a cycle
     * in the catalog's prerequisites graph (not expected in the current data,
     * but the catalog is hand-maintained JSON - see fortifications.json - so
     * a future editing mistake should not cause infinite recursion): if a
     * fortification is reached again while its own depth is still being
     * computed, it is treated as already unlocked (depth 0) for that path.
     */
    private static int computeUnlockDepth(String id, Map<String, Fortification> byId,
                                          Map<String, Integer> memo, Set<String> inProgress) {
        Integer cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        if (!inProgress.add(id)) {
            return 0;
        }
        Fortification fortification = byId.get(id);
        int depth;
        if (fortification == null || fortification.prerequisites().isEmpty()) {
            depth = 0;
        } else {
            int shallowestPrerequisite = fortification.prerequisites().stream()
                    .mapToInt(prerequisiteId -> computeUnlockDepth(prerequisiteId, byId, memo, inProgress))
                    .min()
                    .orElse(0);
            depth = shallowestPrerequisite + 1;
        }
        inProgress.remove(id);
        memo.put(id, depth);
        return depth;
    }

    /**
     * One not-yet-assigned team still available for this run - {@code team}
     * is a {@link HeroTeam} or {@link TitanTeam} depending on the side;
     * {@code totalPower} and {@code sortScore} are cached at pool-build time
     * purely so the comparators do not need to re-apply the accessor on every
     * comparison.
     *
     * <p>Package-private so {@code BestPossibleLineupAlgorithmAssignmentTest}
     * can construct candidates directly.
     */
    record Candidate<T>(String memberId, T team, int teamIndex, int totalPower, double sortScore) {
    }
}
