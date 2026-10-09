package org.c2w.data.journal.db;

/**
 * A battle log was exported by a different guild (first guild in the file
 * name) than the one this journal belongs to. Each journal holds the battles
 * of exactly one own guild.
 */
public class OwnGuildMismatchException extends JournalException {

    private final long storedGameGuildId;
    private final long logGameGuildId;

    public OwnGuildMismatchException(long storedGameGuildId, long logGameGuildId) {
        super("This journal belongs to the guild with game id " + storedGameGuildId
                + ", but the battle log was exported by the guild with game id " + logGameGuildId);
        this.storedGameGuildId = storedGameGuildId;
        this.logGameGuildId = logGameGuildId;
    }

    /** Game guild id of the journal's own guild. */
    public long storedGameGuildId() {
        return storedGameGuildId;
    }

    /** Game guild id of the exporting guild in the rejected log. */
    public long logGameGuildId() {
        return logGameGuildId;
    }
}
