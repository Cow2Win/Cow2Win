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
 * make sure a battle log goes into the journal of the right guild.
 *
 * <p>{@code guildMaster} says whether the user is the guild master of this
 * guild - chosen once when the guild is created and not changeable in the app
 * afterwards (later, some menu items will only be available for a guild master).
 *
 * <p>Code that rebuilds a guild must keep {@code gameGuildId} and {@code
 * guildMaster} (see {@link #withMembers}).
 *
 * <p>{@link #MAX_NAME_LENGTH} only applies when a guild is created - an
 * existing guild with a longer name still loads.
 */
public record Guild(
        String id,
        String name,
        List<GuildMember> members,
        Long gameGuildId,
        boolean guildMaster
) {
    /** Maximum number of members of a guild (Hero Wars rule). */
    public static final int MAX_MEMBERS = 30;

    /** Maximum length of the name of a new guild (after trimming). */
    public static final int MAX_NAME_LENGTH = 20;

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

    /** A guild that is no guild master guild. */
    public Guild(String id, String name, List<GuildMember> members, Long gameGuildId) {
        this(id, name, members, gameGuildId, false);
    }

    /** A guild whose game guild id is not known (yet) and that is no guild master guild. */
    public Guild(String id, String name, List<GuildMember> members) {
        this(id, name, members, null, false);
    }

    /** The name to show to the user - the id if there is no name. */
    public String displayName() {
        return name == null || name.isBlank() ? id : name;
    }

    /** This guild with other members - id, name, game guild id and guild master stay. */
    public Guild withMembers(List<GuildMember> newMembers) {
        return new Guild(id, name, newMembers, gameGuildId, guildMaster);
    }

    /** This guild with the given game guild id - everything else stays. */
    public Guild withGameGuildId(Long newGameGuildId) {
        return new Guild(id, name, members, newGameGuildId, guildMaster);
    }
}
