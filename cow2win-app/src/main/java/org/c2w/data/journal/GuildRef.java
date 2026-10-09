package org.c2w.data.journal;

/**
 * A guild as named in a battle log's file name: {@code <name> Server <server> (<gameGuildId>)}.
 *
 * @param name        guild name exactly as in the file name (may contain spaces, digits, Cyrillic)
 * @param server      game server number
 * @param gameGuildId the guild's id in the game (number in parentheses)
 */
public record GuildRef(String name, int server, long gameGuildId) {
    public GuildRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("GuildRef needs a name");
        }
    }
}
