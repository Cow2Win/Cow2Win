package org.c2w.data.journal.db;

import java.time.LocalDate;
import java.util.List;

/**
 * An own-guild player with its assignment and what the DEFENSE logs say about
 * it (the attack log never counts for the own members).
 *
 * @param player         the player (raw name as in the log)
 * @param assignment     the assignment, {@code null} if none was ever set
 * @param defenses       number of single fights the player defended (all defense logs)
 * @param lastSeen       latest battle day with a defense of the player, {@code null} if none
 * @param lastTeamPowers the player's defender team powers on {@code lastSeen}, in file order
 */
public record OwnPlayerStats(
        JournalPlayer player,
        PlayerAssignment assignment,
        int defenses,
        LocalDate lastSeen,
        List<Integer> lastTeamPowers
) {
    public OwnPlayerStats {
        lastTeamPowers = lastTeamPowers == null ? List.of() : List.copyOf(lastTeamPowers);
    }

    /** The assignment status; {@link AssignmentStatus#OPEN} without an assignment. */
    public AssignmentStatus status() {
        return assignment == null ? AssignmentStatus.OPEN : assignment.status();
    }

    /** The assigned member id, {@code null} unless assigned. */
    public String memberId() {
        return assignment == null || assignment.status() != AssignmentStatus.ASSIGNED ? null : assignment.memberId();
    }
}
