package org.c2w.data.model;

import java.util.List;

/**
 * A guild with its members. Per the user's requirements, a guild has 0-30
 * members (upper bound from Hero Wars, no lower bound - a new/empty guild is
 * a valid state).
 */
public record Guild(
        String id,
        String name,
        List<GuildMember> members
) {
    private static final int MAX_MEMBERS = 30;

    public Guild {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Guild needs an id");
        }
        if (members != null && members.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("A guild has at most " + MAX_MEMBERS + " members");
        }
        members = members == null ? List.of() : List.copyOf(members);

        long distinctIds = members.stream().map(GuildMember::id).distinct().count();
        if (distinctIds != members.size()) {
            throw new IllegalArgumentException("Guild '" + id + "' contains members with a duplicate id");
        }
    }
}
