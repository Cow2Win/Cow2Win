package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class BestPossibleLineupAlgorithm implements LineupAlgorithm {


    private static final String HERO_BRIDGE_ID = "heros-bridge";

    /** TITAN-side counterpart of {@link #HERO_BRIDGE_ID} - same reasoning, same catalog, id "bridge". */
    private static final String TITAN_BRIDGE_ID = "bridge";

    @Override
    public String displayName() {
        return "Best possible lineup";
    }

    @Override
    public Lineup run(Lineup lineup, Guild guild) {
        // Start from whatever is already assigned (e.g. manual picks made via
        // the GUI) and only ADD entries for teams that don't have one yet -
        // fillFortifications() never touches/removes an existing entry, it
        // only ever appends new ones for still-unassigned teams/fortifications.
        List<Lineup.Entry> updatedEntries = new ArrayList<>(lineup.entries());

        // Unlock depth (see computeUnlockDepths' javadoc) depends on the FULL catalog, since
        // prerequisites can cross HERO/TITAN (e.g. "bastion" is HERO but only unlocks once one
        // of three TITAN bastions falls) - computed once here, up front, and handed to both
        // passes below instead of recomputing it separately per side.
        Map<String, Integer> unlockDepth = computeUnlockDepths(FortificationRepository.findAll());

        // Hero teams can only ever defend HERO fortifications and titan teams
        // only ever defend TITAN fortifications, so the two sides never
        // compete for the same slots - hence two fully independent passes,
        // each parameterized with its own bridge id and the right
        // accessor/scoring functions (via method references) for that side's
        // team type T (HeroTeam or TitanTeam). Hero pass runs first, but
        // since the passes don't interact, the order between them does not
        // matter.
        fillFortifications(FortificationType.HERO, Lineup.TeamType.HERO, HERO_BRIDGE_ID, guild, updatedEntries,
                GuildMember::heroTeams, HeroTeam::totalPower, HeroTeam::buffFitScore, HeroTeam::sortScore, unlockDepth);
        fillFortifications(FortificationType.TITAN, Lineup.TeamType.TITAN, TITAN_BRIDGE_ID, guild, updatedEntries,
                GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::buffFitScore, TitanTeam::sortScore, unlockDepth);

        return new Lineup(lineup.guildId(), lineup.guildName(), displayName(), lineup.createdAt(), updatedEntries);
    }

    private static <T> void fillFortifications(FortificationType fortificationType, Lineup.TeamType teamType,
                                               String bridgeId, Guild guild, List<Lineup.Entry> updatedEntries,
                                               Function<GuildMember, List<T>> teamsOf,
                                               ToIntFunction<T> totalPowerOf,
                                               ToIntBiFunction<T, Buff> buffFitScoreOf,
                                               ToDoubleFunction<T> sortScoreOf,
                                               Map<String, Integer> unlockDepth) {

        // Every team of this side (hero or titan) that isn't already sitting
        // on some fortification - these are the only teams this run is
        // allowed to place; see buildCandidatePool() below.
        List<Candidate<T>> pool = buildCandidatePool(teamType, guild, updatedEntries, teamsOf, totalPowerOf, sortScoreOf);

        // All catalog fortifications of this side, e.g. all HERO fortifications
        // when called for the hero pass. A full defensive lineup is planned for
        // EVERY fortification of this type, not just already-unlocked ones -
        // capture-order (see Fortification#prerequisites) only influences the
        // ORDER fortifications are picked from below (via unlockDepth), it
        // never excludes a not-yet-unlocked fortification from being planned.
        List<Fortification> allOfType = FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == fortificationType)
                .toList();

        // The one fortification ("heros-bridge" / "bridge") singled out for
        // special treatment in Criterion 1 below - see the class-level
        // reasoning on HERO_BRIDGE_ID/TITAN_BRIDGE_ID: it is the strategic
        // choke point almost everything else on the map depends on.
        Fortification bridge = allOfType.stream()
                .filter(fortification -> fortification.id().equals(bridgeId))
                .findFirst()
                .orElse(null);

        // Every other fortification of this side, ranked by strategicImportance
        // (catalog value, see Fortification#strategicImportance) descending -
        // this ordering drives both the coverage pass (Criterion 2) and the
        // fill-remaining-capacity pass (Criteria 3-5) below: the more
        // important a fortification is as a hub, the earlier it gets picked
        // from in each pass. strategicImportance stays the PRIMARY criterion
        // (per the user's decision) - unlockDepth (see computeUnlockDepths)
        // only breaks ties between fortifications of otherwise equal
        // strategicImportance, preferring the one reachable sooner (lower
        // depth), since that is the one an opponent can threaten first. id is
        // the final tiebreaker for a fully deterministic (repeatable) result
        // when both importance and depth are equal.
        List<Fortification> others = allOfType.stream()
                .filter(fortification -> bridge == null || !fortification.id().equals(bridge.id()))
                .sorted(Comparator.comparingInt(Fortification::strategicImportance).reversed()
                        .thenComparingInt(fortification -> unlockDepth.getOrDefault(fortification.id(), Integer.MAX_VALUE))
                        .thenComparing(Fortification::id))
                .toList();

        // Criterion 1: the bridge claims the strongest teams before anything else gets a look-in.
        // It never carries a buff itself (see assignStrongestFirst's javadoc), so there is no
        // "best fit" question here - pure totalPower is both the selection criterion and the
        // right one, since the bridge is the prerequisite almost every other fortification of
        // this side needs unlocked (highest strategicImportance in the catalog).
        if (bridge != null) {
            assignStrongestFirst(bridge, pool, updatedEntries, teamType);
        }

        // Criterion 2 (+4 for the order): every other fortification gets its FIRST team,
        // highest strategicImportance first, before any of them gets a second.
        // This is a single pass over `others` (already sorted by strategicImportance desc),
        // so it naturally gives every fortification exactly one shot at a team here - the
        // "alreadyCovered" check below is what turns that into "at most one", since a later
        // fortification could otherwise still see an entry from an EARLIER iteration of this
        // very loop and be skipped, ensuring breadth (every fortification defended at least
        // once) is prioritized over depth (one fortification maxed out) before capacity is
        // topped up in the loop below.
        for (Fortification fortification : others) {
            if (pool.isEmpty()) {
                // No teams left to assign at all - nothing more this pass (or the next one) can do.
                break;
            }
            if (freeSlots(fortification, updatedEntries) <= 0) {
                // Fully defended already (e.g. a fortification with capacity 3 that already
                // received 3 manual entries before this algorithm ran) - move on.
                continue;
            }
            boolean alreadyCovered = updatedEntries.stream()
                    .anyMatch(entry -> entry.fortificationId().equals(fortification.id()));
            if (alreadyCovered) {
                // Already has at least one defender (from a manual pick, or from this very
                // loop having reached it before) - its "first team" is taken care of, so this
                // pass leaves it alone and moves on to a fortification that still has none.
                continue;
            }
            assignOne(fortification, pool, updatedEntries, teamType, buffFitScoreOf);
        }

        // Criteria 3 + 4 + 5: fill whatever capacity is left, one extra team per fortification
        // per pass (so strategicImportance keeps deciding who gets the NEXT team), buffed
        // fortifications preferring the best-fitting remaining team, everyone else taking
        // whichever remaining team is weakest.
        // Repeats full passes over `others` (in the same strategicImportance order as above)
        // until either the pool is drained or a whole pass added nothing (progress stays
        // false, which can only happen once every fortification's capacity is exhausted).
        // Handing out at most one extra team per fortification PER PASS - rather than filling
        // one fortification to capacity before moving to the next - is what keeps
        // strategicImportance in control of who gets the NEXT team overall: the most important
        // still-open fortification always gets first refusal on each new pass, instead of the
        // first one in the list hoarding the whole remaining pool.
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
                // assignOne() itself decides buff-fit vs. lowest-totalPower (Criteria 3/5) -
                // see its javadoc - this loop only decides WHICH fortification gets the call.
                assignOne(fortification, pool, updatedEntries, teamType, buffFitScoreOf);
                progress = true;
            }
        }
    }

    /**
     * Collects one {@link Candidate} per team of this side (HERO or TITAN)
     * across the WHOLE guild, except teams that already have an entry in
     * {@code updatedEntries} (identified by memberId + teamType + teamIndex,
     * the same triple {@link Lineup.Entry} uses - see its javadoc). This is
     * what makes the algorithm additive: a team assigned manually (or by an
     * earlier run) before this call never even enters the pool, so it can
     * neither be reassigned nor counted twice, and every {@code assign*}
     * helper further down only ever removes from - never adds back to -
     * this same pool, so a team leaves it for good the moment it is placed.
     */
    private static <T> List<Candidate<T>> buildCandidatePool(Lineup.TeamType teamType, Guild guild,
                                                             List<Lineup.Entry> updatedEntries,
                                                             Function<GuildMember, List<T>> teamsOf,
                                                             ToIntFunction<T> totalPowerOf,
                                                             ToDoubleFunction<T> sortScoreOf) {
        List<Candidate<T>> pool = new ArrayList<>();
        for (GuildMember member : guild.members()) {
            // teamsOf is GuildMember::heroTeams or GuildMember::titanTeams (picked by the
            // caller in run()) - so `teams` holds at most 3 hero teams or 2 titan teams,
            // per the Clash of Worlds cap enforced in GuildMember's constructor.
            List<T> teams = teamsOf.apply(member);
            for (int teamIndex = 0; teamIndex < teams.size(); teamIndex++) {
                final int currentTeamIndex = teamIndex;
                boolean alreadyAssigned = updatedEntries.stream().anyMatch(entry ->
                        entry.teamMemberId().equals(member.id())
                                && entry.teamType() == teamType
                                && entry.teamIndex() == currentTeamIndex);
                if (alreadyAssigned) {
                    // Skip: this exact team already defends some fortification (manual pick or
                    // earlier run) - leave that assignment untouched and don't offer it again.
                    continue;
                }
                T team = teams.get(currentTeamIndex);
                // totalPowerOf/sortScoreOf are HeroTeam::totalPower/HeroTeam::sortScore or their
                // TitanTeam counterparts - cached here once so every later comparator can sort on
                // Candidate::totalPower/Candidate::sortScore directly instead of re-applying the
                // accessor function on every comparison.
                pool.add(new Candidate<>(member.id(), team, currentTeamIndex, totalPowerOf.applyAsInt(team),
                        sortScoreOf.applyAsDouble(team)));
            }
        }
        return pool;
    }

    /**
     * Criterion 1: fills {@code bridge}'s free slots with the strongest
     * remaining candidates (highest {@link Candidate#totalPower()} first,
     * ties broken by memberId/teamIndex purely for a deterministic result -
     * the catalog has no other ordering for equal-power teams either).
     * Assigned candidates are removed from {@code pool} so no later pass can
     * pick them again. New entries get buffFitScore=0 (the bridge never has
     * a buff in this catalog - both {@link #HERO_BRIDGE_ID} and
     * {@link #TITAN_BRIDGE_ID} have {@code buff() == null}) and
     * weightedScore = totalPower (the value this pick was actually made on).
     */
    private static <T> void assignStrongestFirst(Fortification bridge, List<Candidate<T>> pool,
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
            updatedEntries.add(new Lineup.Entry(bridge.id(), candidate.memberId(), teamType, candidate.teamIndex(),
                    candidate.totalPower(), 0, candidate.totalPower()));
            pool.remove(candidate);
            free--;
        }
    }

    /**
     * Assigns exactly ONE remaining candidate to {@code fortification} -
     * used both by the coverage pass and the fill-remaining-capacity pass in
     * {@link #fillFortifications}. If the fortification has a buff
     * (criterion 3), the candidate with the highest {@code buffFitScoreOf}
     * for that buff is picked (ties broken by lowest totalPower first, then
     * memberId/teamIndex, so strong teams are kept in the pool as long as
     * possible even when several candidates fit a buff equally well).
     * Otherwise (criterion 5), the candidate with the lowest {@code sortScore}
     * (see {@link HeroTeam#sortScore()}/{@link TitanTeam#sortScore()}) is
     * picked outright, so the ordinary, unbuffed fortifications are the ones
     * that end up with whatever teams are weakest/least valuable. Either
     * way, the chosen candidate is removed from {@code pool} and a new
     * {@link Lineup.Entry} is appended to {@code updatedEntries} with a real
     * buffFitScore (0 when there is no buff to fit) and weightedScore set to
     * whichever value the pick was actually made on (buffFitScore for a
     * buff-driven pick, sortScore for an unbuffed one).
     */
    private static <T> void assignOne(Fortification fortification, List<Candidate<T>> pool,
                                      List<Lineup.Entry> updatedEntries, Lineup.TeamType teamType,
                                      ToIntBiFunction<T, Buff> buffFitScoreOf) {
        Buff buff = fortification.buff();
        Candidate<T> chosen;
        int buffFitScore;

        if (buff != null) {
            // Criterion 3: buffed fortification - best buff fit wins, totalPower only breaks
            // ties (so among equally-fitting candidates the WEAKEST is picked, keeping the
            // strongest teams in the pool for other buffed fortifications for as long as
            // possible instead of "wasting" them on a fort whose buff they satisfy just as
            // well as a weaker team would).
            chosen = pool.stream()
                    .sorted(Comparator.<Candidate<T>>comparingInt(candidate -> buffFitScoreOf.applyAsInt(candidate.team(), buff))
                            .reversed()
                            .thenComparingInt(Candidate::totalPower)
                            .thenComparing(Candidate::memberId)
                            .thenComparingInt(Candidate::teamIndex))
                    .findFirst()
                    .orElseThrow();
            buffFitScore = buffFitScoreOf.applyAsInt(chosen.team(), buff);
        } else {
            // Criterion 5: unbuffed fortification - no buff to optimize for, so it gets
            // whatever is left over at the bottom of the pool (by sortScore, not raw
            // totalPower - see HeroTeam#sortScore/TitanTeam#sortScore), letting the
            // strongest/most valuable remaining teams stay available for buffed
            // fortifications (Criterion 3) and the bridge (Criterion 1) instead.
            chosen = pool.stream()
                    .sorted(Comparator.<Candidate<T>>comparingDouble(Candidate::sortScore)
                            .thenComparing(Candidate::memberId)
                            .thenComparingInt(Candidate::teamIndex))
                    .findFirst()
                    .orElseThrow();
            buffFitScore = 0;
        }

        pool.remove(chosen);
        double weightedScore = buff != null ? buffFitScore : chosen.sortScore();
        updatedEntries.add(new Lineup.Entry(fortification.id(), chosen.memberId(), teamType, chosen.teamIndex(),
                chosen.totalPower(), buffFitScore, weightedScore));
    }

    /** Free capacity of {@code fortification} given every entry already in {@code updatedEntries} (any source). */
    private static int freeSlots(Fortification fortification, List<Lineup.Entry> updatedEntries) {
        long used = updatedEntries.stream()
                .filter(entry -> entry.fortificationId().equals(fortification.id()))
                .count();
        return (int) (fortification.capacity() - used);
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
     * prerequisites can cross HERO/TITAN (see {@link #fillFortifications}'s
     * call site in {@link #run}), this is computed once over the FULL
     * catalog rather than per side.
     *
     * Used purely as a tiebreaker in {@link #fillFortifications} (after
     * strategicImportance, before id) so that, among equally important
     * fortifications, the one an opponent can reach sooner is prioritized
     * for team assignment - it does not affect WHICH fortifications get
     * planned for, only in what order equally-important ones are picked
     * from (see the {@code others} javadoc comment in fillFortifications).
     *
     * Package-private (not private) so {@code BestPossibleLineupAlgorithmTest}
     * can exercise it directly instead of via reflection.
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
     * {@link #computeUnlockDepths}. {@code memo} carries results across
     * calls so every fortification is only ever computed once even though
     * it may be visited as someone else's prerequisite many times over.
     * {@code inProgress} guards against a cycle in the catalog's
     * prerequisites graph (not expected in the current data, but the
     * catalog is hand-maintained JSON - see fortifications.json - so a
     * future editing mistake should not cause infinite recursion): if a
     * fortification is reached again while its own depth is still being
     * computed, it is treated as already unlocked (depth 0) for that path
     * rather than recursing forever.
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
     * is a {@link HeroTeam} or {@link TitanTeam} depending on which
     * {@link #fillFortifications} call built the pool; {@code totalPower}
     * and {@code sortScore} are cached at pool-build time (via
     * {@code totalPowerOf}/{@code sortScoreOf}) purely so the comparators in
     * {@link #assignStrongestFirst}/{@link #assignOne} do not need a
     * {@code ToIntFunction<T>}/{@code ToDoubleFunction<T>} passed all the way
     * down as well.
     */
    private record Candidate<T>(String memberId, T team, int teamIndex, int totalPower, double sortScore) {
    }
}

