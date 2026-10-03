package org.c2w.service.journal;

/**
 * A reason why nothing of an import can be imported.
 *
 * @param kind        what is wrong
 * @param guildName   the Cow2Win guild the logs belong to ({@link Kind#LOGS_OF_OTHER_GUILD}), else {@code null}
 * @param gameGuildId the game guild id concerned, may be {@code null}
 */
public record PlanError(Kind kind, String guildName, Long gameGuildId) {

    /** What is wrong. */
    public enum Kind {
        /** No guild is open. */
        NO_GUILD_OPEN,
        /** None of the files can be imported. */
        NO_IMPORTABLE_FILE,
        /** The files were exported by different guilds. */
        MIXED_OWN_GUILDS,
        /** Another Cow2Win guild has this game guild id - switch to it first. */
        LOGS_OF_OTHER_GUILD,
        /** The open guild has another game guild id, and no Cow2Win guild has this one. */
        NO_GUILD_WITH_THIS_ID,
        /** The open guild's journal belongs to another game guild. */
        JOURNAL_OF_OTHER_GUILD
    }
}
