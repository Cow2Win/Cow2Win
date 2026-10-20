package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

import java.util.*;

/**
 * Shared base of every "real" lineup strategy ({@link HodorAlgorithm}). Each instance is bound to
 * one {@link TeamSide}, so e.g. {@code new HodorAlgorithm<>(TeamSide.HERO, ...)} and
 * {@code new HodorAlgorithm<>(TeamSide.TITAN, ...)} can fill the two halves of the same lineup.
 *
 * <p>{@link #run} is the common template: start from the existing entries (additive - never
 * touches an existing entry), build the candidate pool of still-unassigned teams of this side,
 * collect this side's fortifications and hand those to the strategy-specific
 * {@link #fillFortifications}.
 *
 * <p>The older strategies "Best possible lineup", "Balanced defense", "CowScore maximizer" and
 * "Consensus picks" were removed in version 1.0.10 in favour of HODOR; a stored algorithm name of
 * one of them falls back to the side's default (see {@link LineupAlgorithms#findOrDefault}).
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

        // Every team of this side that isn't already sitting on some fortification.
        List<Candidate<T>> pool = buildCandidatePool(side, guild, updatedEntries);

        // All catalog fortifications of this side. A full defensive lineup is planned for EVERY
        // fortification of this type, not just already-unlocked ones.
        List<Fortification> fortifications = FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == side.fortificationType())
                .toList();

        fillFortifications(fortifications, pool, updatedEntries, guild);

        return new Lineup(lineup.guildId(), lineup.guildName(),
                LineupAlgorithms.combinedAlgorithmName(lineup.algorithmName(), teamType(), displayName()),
                lineup.createdAt(), updatedEntries);
    }

    /**
     * The actual strategy: assigns teams from {@code pool} to {@code fortifications} by appending
     * to {@code updatedEntries}.
     *
     * @param fortifications every fortification of this side, in catalog order
     * @param pool           still-unassigned teams of this side (mutable)
     * @param updatedEntries every entry so far, both sides (mutable, append-only)
     * @param guild          the guild the teams belong to
     */
    protected abstract void fillFortifications(List<Fortification> fortifications, List<Candidate<T>> pool,
                                               List<Lineup.Entry> updatedEntries, Guild guild);

    // --- shared helpers ---------------------------------------------------

    /**
     * Collects one {@link Candidate} per team of {@code side} across the WHOLE
     * guild, except teams that already have an entry in {@code updatedEntries}
     * (identified by memberId + teamType + teamIndex, the same triple {@link
     * Lineup.Entry} uses). This is what makes every algorithm additive: a team
     * assigned manually (or by an earlier run) never even enters the pool, so
     * it can neither be reassigned nor counted twice.
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

    /** Finds a guild member by their ID, or {@code null} if there is none. */
    static GuildMember findMemberById(Guild guild, String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .orElse(null);
    }

    /**
     * One not-yet-assigned team still available for this run - {@code team}
     * is a {@link HeroTeam} or {@link TitanTeam} depending on the side;
     * {@code totalPower} and {@code sortScore} are cached at pool-build time
     * purely so the comparators do not need to re-apply the accessor on every
     * comparison.
     */
    record Candidate<T>(String memberId, T team, int teamIndex, int totalPower, double sortScore) {
    }
}
