package org.c2w.data.model;

import java.util.List;

/**
 * A guild member with their fielded teams. Per Clash of Worlds rules, up to 3
 * hero teams and 2 titan teams per member (0 is allowed - e.g. when a member
 * has not fielded any titan teams yet).
 *
 * Teams themselves have no name of their own - for display purposes, the
 * position in the respective list is enough, see {@link #teamLabel(int)}
 * ("Team1", "Team2", ...).
 */
public record GuildMember(
        String id,
        String name,
        List<HeroTeam> heroTeams,
        List<TitanTeam> titanTeams
) {
    public GuildMember {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("GuildMember needs an id");
        }
        if (heroTeams != null && heroTeams.size() > 3) {
            throw new IllegalArgumentException("At most 3 hero teams per member");
        }
        if (titanTeams != null && titanTeams.size() > 2) {
            throw new IllegalArgumentException("At most 2 titan teams per member");
        }
        heroTeams = heroTeams == null ? List.of() : List.copyOf(heroTeams);
        titanTeams = titanTeams == null ? List.of() : List.copyOf(titanTeams);

        for (HeroTeam team : heroTeams) {
            if (!id.equals(team.memberId())) {
                throw new IllegalArgumentException("Hero team with memberId '" + team.memberId()
                        + "' does not belong to member '" + id + "'");
            }
        }
        for (TitanTeam team : titanTeams) {
            if (!id.equals(team.memberId())) {
                throw new IllegalArgumentException("Titan team with memberId '" + team.memberId()
                        + "' does not belong to member '" + id + "'");
            }
        }
    }

    /** Generic, 1-based display name for a team based on its position in the list. */
    public static String teamLabel(int index) {
        return "Team" + (index + 1);
    }
}
