package org.c2w.service.journal;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.TitanElement;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * What {@code JournalTeamBuilderService#prepare} found: per guild member the
 * team proposals from the journal's defense logs, matched against the member's
 * current teams. Writes nothing; the GUI shows it and answers with a
 * {@link TeamBuildSelection}.
 *
 * @param members                per member with log data: its proposals (members by name)
 * @param skippedPlayers         defenders not linked to an existing member, with the reason
 * @param membersWithoutLogData  ids of guild members that defend in no stored defense log
 * @param defenseLogs            number of battles with a defense log in the journal
 * @param guildFile              the guild the plan was made for
 */
public record TeamBuildPlan(List<MemberPlan> members, List<SkippedPlayer> skippedPlayers,
                            List<String> membersWithoutLogData, int defenseLogs, Path guildFile) {

    public TeamBuildPlan {
        members = List.copyOf(members);
        skippedPlayers = List.copyOf(skippedPlayers);
        membersWithoutLogData = List.copyOf(membersWithoutLogData);
    }

    /** Where the composition of a proposal comes from. */
    public enum CompositionSource {
        /** The defense log has the defender's units for this fight (rare, e.g. 17.09.2026). */
        DEFENSE_UNITS,
        /** An attack team of the same player in the same battle has exactly the same team power. */
        ATTACK_EXACT_POWER,
        /** Only the team power is known. */
        POWER_ONLY
    }

    /**
     * The heroes (with pet) or titans (with totems) of a team, as found in a log.
     *
     * @param kind       hero or titan team
     * @param unitIds    hero or titan catalog ids in log order
     * @param petId      hero team only: the pet, {@code null} if none
     * @param totems     titan team only: the totems as in the log (validated when applied)
     * @param source     {@link CompositionSource#DEFENSE_UNITS} or {@link CompositionSource#ATTACK_EXACT_POWER}
     * @param battleDate day of the battle it was seen in
     * @param power      team power then
     */
    public record Composition(TeamKind kind, List<String> unitIds, String petId, Set<TitanElement> totems,
                              CompositionSource source, LocalDate battleDate, int power) {
        public Composition {
            unitIds = List.copyOf(unitIds);
            totems = totems == null || totems.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(totems));
        }

        /** True if both have the same heroes/titans (order does not matter). */
        public boolean sameUnits(Composition other) {
            return other != null && kind == other.kind && new HashSet<>(unitIds).equals(new HashSet<>(other.unitIds));
        }
    }

    /** Where a proposal goes in the member's teams. */
    public record Target(Kind kind, int teamIndex) {

        /** A new team at the next free index, or filling an existing empty team. */
        public enum Kind {NEW, FILL}

        public static final Target NEW = new Target(Kind.NEW, -1);

        public static Target fill(int teamIndex) {
            return new Target(Kind.FILL, teamIndex);
        }
    }

    /** Whether a proposal can be applied. */
    public enum ProposalStatus {
        /** Can be taken over (new team or filling an empty one). */
        AVAILABLE,
        /** Matches a team with heroes/titans the member already has - not selectable. */
        ALREADY_PRESENT,
        /** More teams of this kind than a member may have - not selectable. */
        OVER_LIMIT
    }

    /**
     * One team seen in a defense log.
     *
     * @param id                 stable id
     * @param memberId           the guild member
     * @param kind               hero or titan team (from the fortification type)
     * @param power              team power in that battle
     * @param fortificationId    catalog id of the fortification ({@code null} if unknown)
     * @param fortificationName  raw fortification name
     * @param position           position in the fortification
     * @param battleId           journal battle id
     * @param battleDate         battle day
     * @param fromSourceBattle   true if seen in the member's newest battle, false for an older one
     * @param composition        heroes/titans if known, else {@code null} ({@link #source()} POWER_ONLY)
     * @param knownCompositions  compositions of the same kind seen in other fights of the member (newest
     *                           first) - offered for a proposal without composition, never set automatically
     * @param status             whether it can be applied
     * @param existingTeamIndex  {@link ProposalStatus#ALREADY_PRESENT}: the matching team, else -1
     * @param suggestedTarget    {@link ProposalStatus#AVAILABLE}: the suggested target, else {@code null}
     * @param preselected        selected by default (available and from the source battle)
     */
    public record Proposal(String id, String memberId, TeamKind kind, int power, String fortificationId,
                           String fortificationName, int position, int battleId, LocalDate battleDate,
                           boolean fromSourceBattle, Composition composition, List<Composition> knownCompositions,
                           ProposalStatus status, int existingTeamIndex, Target suggestedTarget,
                           boolean preselected) {
        public Proposal {
            knownCompositions = List.copyOf(knownCompositions);
        }

        public CompositionSource source() {
            return composition == null ? CompositionSource.POWER_ONLY : composition.source();
        }

        public boolean selectable() {
            return status == ProposalStatus.AVAILABLE;
        }
    }

    /**
     * One member with its proposals.
     *
     * @param memberId     Cow2Win member id
     * @param memberName   member name
     * @param logNames     the member's names in the defense logs (several after renames)
     * @param sourceDate   day of the newest battle the member defends in
     * @param proposals    source battle first (hero teams, then titan teams), then older battles (newest first)
     * @param freeHeroSlots free hero team slots (no team yet)
     * @param freeTitanSlots free titan team slots
     * @param emptyHeroTeams indices of hero teams without heroes
     * @param emptyTitanTeams indices of titan teams without titans
     */
    public record MemberPlan(String memberId, String memberName, List<String> logNames, LocalDate sourceDate,
                             List<Proposal> proposals, int freeHeroSlots, int freeTitanSlots,
                             List<Integer> emptyHeroTeams, List<Integer> emptyTitanTeams) {
        public MemberPlan {
            logNames = List.copyOf(logNames);
            proposals = List.copyOf(proposals);
            emptyHeroTeams = List.copyOf(emptyHeroTeams);
            emptyTitanTeams = List.copyOf(emptyTitanTeams);
        }

        public int freeSlots(TeamKind kind) {
            return kind == TeamKind.HERO ? freeHeroSlots : freeTitanSlots;
        }

        public List<Integer> emptyTeams(TeamKind kind) {
            return kind == TeamKind.HERO ? emptyHeroTeams : emptyTitanTeams;
        }
    }

    /** Why a defender's teams are not proposed. */
    public enum SkipReason {
        /** No assignment, or still open. */
        NOT_ASSIGNED,
        /** Deliberately not in Cow2Win. */
        NOT_IN_COW2WIN,
        /** Former member. */
        FORMER,
        /** Assigned to a member id the guild does not have (any more). */
        MEMBER_MISSING
    }

    /** A defender whose teams are not proposed. */
    public record SkippedPlayer(String rawName, SkipReason reason, String memberId) {
    }

    // --- convenience ---

    /** All proposals of all members. */
    public List<Proposal> proposals() {
        return members.stream().flatMap(m -> m.proposals().stream()).toList();
    }

    /** The proposal with this id. */
    public Optional<Proposal> proposal(String id) {
        return proposals().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** The plan of a member. */
    public Optional<MemberPlan> member(String memberId) {
        return members.stream().filter(m -> m.memberId().equals(memberId)).findFirst();
    }

    /** Number of proposals per composition source. */
    public Map<CompositionSource, Integer> countBySource() {
        Map<CompositionSource, Integer> counts = new EnumMap<>(CompositionSource.class);
        for (CompositionSource s : CompositionSource.values()) {
            counts.put(s, 0);
        }
        proposals().forEach(p -> counts.merge(p.source(), 1, Integer::sum));
        return counts;
    }
}
