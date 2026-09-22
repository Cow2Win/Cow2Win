package org.c2w.data.model;

import java.util.List;

/**
 * A guild member with their fielded teams. Per Clash of Worlds rules, up to 3
 * hero teams and 2 titan teams per member (0 is allowed - e.g. when a member
 * has not fielded any titan teams yet).
 *
 * Teams themselves have no name of their own - for display purposes, the
 * position in the respective list is enough, see {@link #teamLabel(int)}
 * ("Team1", "Team2", ...). Every team's own {@link HeroTeam#index()}/
 * {@link TitanTeam#index()} must match its position in the respective list
 * here (0-based) - enforced below - so a team can be looked up/referenced by
 * memberId + index alone (see {@link Lineup.Entry#teamIndex()}) without
 * depending on callers to track list position separately.
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
        if (heroTeams != null && heroTeams.size() > HeroTeam.MAX_TEAMS_PER_MEMBER) {
            throw new IllegalArgumentException("At most " + HeroTeam.MAX_TEAMS_PER_MEMBER + " hero teams per member");
        }
        if (titanTeams != null && titanTeams.size() > TitanTeam.MAX_TEAMS_PER_MEMBER) {
            throw new IllegalArgumentException("At most " + TitanTeam.MAX_TEAMS_PER_MEMBER + " titan teams per member");
        }
        heroTeams = heroTeams == null ? List.of() : List.copyOf(heroTeams);
        titanTeams = titanTeams == null ? List.of() : List.copyOf(titanTeams);

        for (int i = 0; i < heroTeams.size(); i++) {
            HeroTeam team = heroTeams.get(i);
            if (!id.equals(team.memberId())) {
                throw new IllegalArgumentException("Hero team with memberId '" + team.memberId()
                        + "' does not belong to member '" + id + "'");
            }
            if (team.index() != i) {
                throw new IllegalArgumentException("Hero team at position " + i + " must have index " + i
                        + ", was: " + team.index());
            }
        }
        for (int i = 0; i < titanTeams.size(); i++) {
            TitanTeam team = titanTeams.get(i);
            if (!id.equals(team.memberId())) {
                throw new IllegalArgumentException("Titan team with memberId '" + team.memberId()
                        + "' does not belong to member '" + id + "'");
            }
            if (team.index() != i) {
                throw new IllegalArgumentException("Titan team at position " + i + " must have index " + i
                        + ", was: " + team.index());
            }
        }
    }

    /** Generic, 1-based display name for a team based on its position in the list. */
    public static String teamLabel(int index) {
        return "Team" + (index + 1);
    }
}
