package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

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
        List<Lineup.Entry> updatedEntries = new ArrayList<>(lineup.entries());

        fillFortifications(FortificationType.HERO, Lineup.TeamType.HERO, HERO_BRIDGE_ID, guild, updatedEntries,
                GuildMember::heroTeams, HeroTeam::totalPower, HeroTeam::buffFitScore);
        fillFortifications(FortificationType.TITAN, Lineup.TeamType.TITAN, TITAN_BRIDGE_ID, guild, updatedEntries,
                GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::buffFitScore);

        return new Lineup(lineup.guildId(), lineup.guildName(), displayName(), lineup.createdAt(), updatedEntries);
    }

    private static <T> void fillFortifications(FortificationType fortificationType, Lineup.TeamType teamType,
                                               String bridgeId, Guild guild, List<Lineup.Entry> updatedEntries,
                                               Function<GuildMember, List<T>> teamsOf,
                                               ToIntFunction<T> totalPowerOf,
                                               ToIntBiFunction<T, Buff> buffFitScoreOf) {

        List<Candidate<T>> pool = buildCandidatePool(teamType, guild, updatedEntries, teamsOf, totalPowerOf);

        List<Fortification> allOfType = FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == fortificationType)
                .toList();

        Fortification bridge = allOfType.stream()
                .filter(fortification -> fortification.id().equals(bridgeId))
                .findFirst()
                .orElse(null);

        List<Fortification> others = allOfType.stream()
                .filter(fortification -> bridge == null || !fortification.id().equals(bridge.id()))
                .sorted(Comparator.comparingInt(Fortification::strategicImportance).reversed()
                        .thenComparing(Fortification::id))
                .toList();

        // Criterion 1: the bridge claims the strongest teams before anything else gets a look-in.
        if (bridge != null) {
            assignStrongestFirst(bridge, pool, updatedEntries, teamType);
        }

        // Criterion 2 (+4 for the order): every other fortification gets its FIRST team,
        // highest strategicImportance first, before any of them gets a second.
        for (Fortification fortification : others) {
            if (pool.isEmpty()) {
                break;
            }
            if (freeSlots(fortification, updatedEntries) <= 0) {
                continue;
            }
            boolean alreadyCovered = updatedEntries.stream()
                    .anyMatch(entry -> entry.fortificationId().equals(fortification.id()));
            if (alreadyCovered) {
                continue;
            }
            assignOne(fortification, pool, updatedEntries, teamType, buffFitScoreOf);
        }

        // Criteria 3 + 4 + 5: fill whatever capacity is left, one extra team per fortification
        // per pass (so strategicImportance keeps deciding who gets the NEXT team), buffed
        // fortifications preferring the best-fitting remaining team, everyone else taking
        // whichever remaining team is weakest.
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
                assignOne(fortification, pool, updatedEntries, teamType, buffFitScoreOf);
                progress = true;
            }
        }
    }

    private static <T> List<Candidate<T>> buildCandidatePool(Lineup.TeamType teamType, Guild guild,
                                                             List<Lineup.Entry> updatedEntries,
                                                             Function<GuildMember, List<T>> teamsOf,
                                                             ToIntFunction<T> totalPowerOf) {
        List<Candidate<T>> pool = new ArrayList<>();
        for (GuildMember member : guild.members()) {
            List<T> teams = teamsOf.apply(member);
            for (int teamIndex = 0; teamIndex < teams.size(); teamIndex++) {
                final int currentTeamIndex = teamIndex;
                boolean alreadyAssigned = updatedEntries.stream().anyMatch(entry ->
                        entry.teamMemberId().equals(member.id())
                                && entry.teamType() == teamType
                                && entry.teamIndex() == currentTeamIndex);
                if (alreadyAssigned) {
                    continue;
                }
                T team = teams.get(currentTeamIndex);
                pool.add(new Candidate<>(member.id(), team, currentTeamIndex, totalPowerOf.applyAsInt(team)));
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
     * Otherwise (criterion 5), the candidate with the lowest totalPower is
     * picked outright, so the ordinary, unbuffed fortifications are the ones
     * that end up with whatever teams are weakest. Either way, the chosen
     * candidate is removed from {@code pool} and a new {@link Lineup.Entry}
     * is appended to {@code updatedEntries} with a real buffFitScore
     * (0 when there is no buff to fit) and weightedScore set to whichever
     * value the pick was actually made on (buffFitScore for a buff-driven
     * pick, totalPower for a power-driven one).
     */
    private static <T> void assignOne(Fortification fortification, List<Candidate<T>> pool,
                                      List<Lineup.Entry> updatedEntries, Lineup.TeamType teamType,
                                      ToIntBiFunction<T, Buff> buffFitScoreOf) {
        Buff buff = fortification.buff();
        Candidate<T> chosen;
        int buffFitScore;

        if (buff != null) {
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
            chosen = pool.stream()
                    .sorted(Comparator.<Candidate<T>>comparingInt(Candidate::totalPower)
                            .thenComparing(Candidate::memberId)
                            .thenComparingInt(Candidate::teamIndex))
                    .findFirst()
                    .orElseThrow();
            buffFitScore = 0;
        }

        pool.remove(chosen);
        double weightedScore = buff != null ? buffFitScore : chosen.totalPower();
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
     * One not-yet-assigned team still available for this run - {@code team}
     * is a {@link HeroTeam} or {@link TitanTeam} depending on which
     * {@link #fillFortifications} call built the pool; {@code totalPower} is
     * cached at pool-build time (via {@code totalPowerOf}) purely so the
     * comparators in {@link #assignStrongestFirst}/{@link #assignOne} do not
     * need a {@code ToIntFunction<T>} passed all the way down as well.
     */
    private record Candidate<T>(String memberId, T team, int teamIndex, int totalPower) {
    }
}

