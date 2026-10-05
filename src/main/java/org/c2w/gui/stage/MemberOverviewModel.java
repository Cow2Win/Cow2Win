package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.TitanTeam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GUI-free data of the member overview of {@link InputStageView}: per member of a guild the
 * teams of one fortification type - count, total power, strongest team, last change.
 */
public final class MemberOverviewModel {

    private MemberOverviewModel() {
    }

    /**
     * One member's teams of one fortification type.
     *
     * @param lastModified the newest {@code lastModified} of these teams, null if none is known
     */
    public record MemberRow(String memberId, String name, int teamCount, int totalPower, int strongestTeamPower,
                            LocalDate lastModified) {
    }

    /** One row per member of {@code guild}, in the guild's order, for the teams of {@code fortificationType}. */
    public static List<MemberRow> rows(Guild guild, FortificationType fortificationType) {
        List<MemberRow> rows = new ArrayList<>();
        if (guild == null || guild.members() == null) {
            return rows;
        }
        for (GuildMember member : guild.members()) {
            List<TeamValues> teams = teams(member, fortificationType);
            int total = 0;
            int strongest = 0;
            LocalDate newest = null;
            for (TeamValues team : teams) {
                total += team.power();
                strongest = Math.max(strongest, team.power());
                if (team.lastModified() != null && (newest == null || team.lastModified().isAfter(newest))) {
                    newest = team.lastModified();
                }
            }
            rows.add(new MemberRow(member.id(), member.name(), teams.size(), total, strongest, newest));
        }
        return rows;
    }

    /** Power and last change of one team, whatever its type. */
    private record TeamValues(int power, LocalDate lastModified) {
    }

    private static List<TeamValues> teams(GuildMember member, FortificationType fortificationType) {
        if (fortificationType == FortificationType.TITAN) {
            return member.titanTeams() == null ? List.of() : member.titanTeams().stream()
                    .filter(Objects::nonNull)
                    .map((TitanTeam t) -> new TeamValues(t.totalPower(), t.lastModified())).toList();
        }
        return member.heroTeams() == null ? List.of() : member.heroTeams().stream()
                .filter(Objects::nonNull)
                .map((HeroTeam t) -> new TeamValues(t.totalPower(), t.lastModified())).toList();
    }
}
