package org.c2w.service.journal;

import org.c2w.data.journal.GuildRef;

/**
 * "Link the game guild &lt;name&gt; (server n, id X) to this Cow2Win guild?" - asked
 * when the open guild has no game guild id yet. Default answer: yes.
 *
 * @param id        stable question id
 * @param gameGuild the exporting guild from the file name(s)
 */
public record GuildLinkQuestion(String id, GuildRef gameGuild) {

    /** The id of the (only) guild link question. */
    public static final String ID = "guild-link";
}
