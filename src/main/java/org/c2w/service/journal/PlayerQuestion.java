package org.c2w.service.journal;

import org.c2w.data.journal.TeamKind;

import java.util.List;
import java.util.Set;

/**
 * Asks how a defender of the own guild (from the DEFENSE log - the only source
 * for anything that touches the Cow2Win guild) relates to the guild members.
 * No answer = {@link PlayerAnswer.Kind#OPEN} (asked again next time).
 *
 * @param id                 stable question id
 * @param rawName            the player name exactly as in the log
 * @param nameSuggestions    members with a similar name (at most 2 different characters), closest first
 * @param renameSuggestions  members that may be this player under an old name, best first - only with evidence
 * @param allowedAnswers     the answers that may be given ({@code CREATE} only while the guild has room)
 */
public record PlayerQuestion(String id, String rawName, List<NameSuggestion> nameSuggestions,
                             List<RenameSuggestion> renameSuggestions, Set<PlayerAnswer.Kind> allowedAnswers) {

    public PlayerQuestion {
        nameSuggestions = nameSuggestions == null ? List.of() : List.copyOf(nameSuggestions);
        renameSuggestions = renameSuggestions == null ? List.of() : List.copyOf(renameSuggestions);
        allowedAnswers = allowedAnswers == null ? Set.of() : Set.copyOf(allowedAnswers);
    }

    /** The id of the question for a defender with this raw name. */
    public static String idFor(String rawName) {
        return "player:" + rawName;
    }

    /**
     * A member whose name is similar.
     *
     * @param memberId   Cow2Win member id
     * @param memberName member name
     * @param distance   Levenshtein distance of the normalized names (1 or 2)
     */
    public record NameSuggestion(String memberId, String memberName, int distance) {
    }

    /**
     * A member that may have renamed themselves to {@code rawName}: it does not occur in
     * this import's defense log under any name, and its defense teams match.
     *
     * @param memberId   Cow2Win member id
     * @param memberName current member name
     * @param evidence   why (at least one entry), strongest first
     */
    public record RenameSuggestion(String memberId, String memberName, List<RenameEvidence> evidence) {
        public RenameSuggestion {
            evidence = List.copyOf(evidence);
        }

        /** True if a team has exactly the same heroes/titans (strong evidence). */
        public boolean hasUnitMatch() {
            return evidence.stream().anyMatch(e -> e.kind() == RenameEvidence.Kind.SAME_UNITS);
        }
    }

    /**
     * One reason for a rename suggestion, e.g. "power 1.382.741 ≈ team 2 (1.380.000)".
     *
     * @param kind            power close to a stored team, or the same heroes/titans
     * @param teamKind        hero or titan team
     * @param teamIndex       0-based index of the member's team
     * @param logTeamPower    team power of the defender in the log
     * @param memberTeamPower stored power of the member's team
     */
    public record RenameEvidence(Kind kind, TeamKind teamKind, int teamIndex, int logTeamPower, int memberTeamPower) {

        /** Kind of evidence. */
        public enum Kind {
            /** A defender team of the player has the same heroes/titans as a team of the member. */
            SAME_UNITS,
            /** A defender team power is within the tolerance of a stored team power of the member. */
            POWER
        }
    }
}
