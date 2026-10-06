package org.c2w.data.model;

import java.util.List;

/**
 * A guild with its members. Per the user's requirements, a guild has 0-30
 * members (upper bound from Hero Wars, no lower bound - a new/empty guild is
 * a valid state).
 *
 * <p>{@code gameGuildId} is the guild's id in the game (the number in
 * parentheses in an exported battle log's file name), {@code null} while it is
 * not known yet - set by the Weltenschlacht journal import, which uses it to
 * make sure a battle log goes into the journal of the right guild. Code that
 * rebuilds a guild must keep it (see {@link #withMembers}).
 */
public record Guild(
        String id,
        String name,
        List<GuildMember> members,
        Long gameGuildId
) {
    /** Maximum number of members of a guild (Hero Wars rule). */
    public static final int MAX_MEMBERS = 30;

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

    /** A guild whose game guild id is not known (yet). */
    public Guild(String id, String name, List<GuildMember> members) {
        this(id, name, members, null);
    }

    /** The name to show to the user - the id if there is no name. */
    public String displayName() {
        return name == null || name.isBlank() ? id : name;
    }

    /** This guild with other members - id, name and game guild id stay. */
    public Guild withMembers(List<GuildMember> newMembers) {
        return new Guild(id, name, newMembers, gameGuildId);
    }

    /** This guild with the given game guild id. */
    public Guild withGameGuildId(Long newGameGuildId) {
        return new Guild(id, name, members, newGameGuildId);
    }
}
