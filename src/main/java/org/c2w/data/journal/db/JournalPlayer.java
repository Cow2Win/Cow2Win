package org.c2w.data.journal.db;

/**
 * A player as stored in the journal: the name exactly as in the log.
 *
 * @param id          database id
 * @param name        raw name from the log (case and spaces significant)
 * @param gameGuildId game guild id of the player's guild
 * @param ownGuild    true if the player belongs to the journal's own guild
 */
public record JournalPlayer(int id, String name, long gameGuildId, boolean ownGuild) {
}
