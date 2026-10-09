package org.c2w.data.journal.db;

import java.time.LocalDateTime;

/**
 * Link of an own-guild player to a Cow2Win guild member.
 *
 * @param playerId    journal player id
 * @param memberId    Cow2Win member id ({@code GuildMember#id}, not the name), {@code null} unless assigned
 * @param status      assignment status
 * @param confirmedAt when the status was last set
 */
public record PlayerAssignment(int playerId, String memberId, AssignmentStatus status, LocalDateTime confirmedAt) {
}
