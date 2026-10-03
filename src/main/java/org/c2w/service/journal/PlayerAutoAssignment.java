package org.c2w.service.journal;

/**
 * A defender assigned to a member without asking (shown as information).
 *
 * @param rawName    the player name exactly as in the log
 * @param memberId   the member it is assigned to
 * @param normalized true if only the normalized names match (case, spaces, apostrophes) - marked in the dialog
 */
public record PlayerAutoAssignment(String rawName, String memberId, boolean normalized) {
}
