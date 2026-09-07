package org.c2w.data.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A guild with its members. Per the user's requirements, a guild has 0-30
 * members (upper bound from Hero Wars, no lower bound - a new/empty guild is
 * a valid state).
 *
 * season / seasonStart: this guild's current Clash of Worlds season - entered
 * manually by the user via the GUI (see GuildEditorFrame), not determined
 * automatically. season = 0 and seasonStart = null mean "not set yet". The
 * season value additionally feeds into the suggested file name on save (see
 * GuildEditorFrame#onSaveGuild).
 */
public record Guild(
        String id,
        String name,
        List<GuildMember> members,
        int season,
        LocalDate seasonStart
) {
    private static final int MAX_MEMBERS = 30;

    public Guild {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Guild needs an id");
        }
        if (members != null && members.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("A guild has at most " + MAX_MEMBERS + " members");
        }
        if (season < 0) {
            throw new IllegalArgumentException("season must not be negative");
        }
        members = members == null ? List.of() : List.copyOf(members);

        long distinctIds = members.stream().map(GuildMember::id).distinct().count();
        if (distinctIds != members.size()) {
            throw new IllegalArgumentException("Guild '" + id + "' contains members with a duplicate id");
        }
    }

    /** Convenience constructor for guilds without a season specified (season=0, seasonStart=null). */
    public Guild(String id, String name, List<GuildMember> members) {
        this(id, name, members, 0, null);
    }
}
