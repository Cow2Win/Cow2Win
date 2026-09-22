package org.c2w.gui.guild;

import org.c2w.data.model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public final class GuildDraftConverter {

    private GuildDraftConverter() {
    }

    /** Builds an editable draft from an immutable guild (see {@link GuildEditorDialog}). */
    public static GuildDraft fromGuild(Guild guild) {
        GuildDraft draft = new GuildDraft();
        draft.id = guild.id();
        draft.name = guild.name();
        draft.season = guild.season();
        draft.seasonStart = guild.seasonStart();

        for (GuildMember member : guild.members()) {
            MemberDraft memberDraft = new MemberDraft(member.id(), member.name());
            for (HeroTeam team : member.heroTeams()) {
                memberDraft.heroTeams.add(toTeamDraft(team.heroes(), team.totalPower(), team.lastModified()));
            }
            for (TitanTeam team : member.titanTeams()) {
                memberDraft.titanTeams.add(toTeamDraft(team.titans(), team.totalPower(), team.lastModified()));
            }
            draft.members.add(memberDraft);
        }
        return draft;
    }

    private static <T> TeamDraft<T> toTeamDraft(List<T> members, int totalPower, LocalDate lastModified) {
        TeamDraft<T> teamDraft = new TeamDraft<>();
        teamDraft.members.addAll(members);
        teamDraft.totalPower = totalPower;
        teamDraft.lastModified = lastModified;
        return teamDraft;
    }

    /**
     * Builds an immutable guild from the current state of the draft. A team
     * with totalPower == 0 counts as "empty": it does not need to be removed
     * by hand first, it is simply dropped here on save, along with whatever
     * slot selections it might still carry.
     */
    public static Guild toGuild(GuildDraft draft) {
        List<GuildMember> members = new ArrayList<>();
        for (MemberDraft memberDraft : draft.members) {
            List<HeroTeam> heroTeams = new ArrayList<>();
            for (TeamDraft<Hero> teamDraft : memberDraft.heroTeams) {
                if (teamDraft.totalPower == 0) {
                    continue;
                }
                heroTeams.add(new HeroTeam(memberDraft.id, heroTeams.size(), teamDraft.members, teamDraft.totalPower,
                        teamDraft.lastModified));
            }
            List<TitanTeam> titanTeams = new ArrayList<>();
            for (TeamDraft<Titan> teamDraft : memberDraft.titanTeams) {
                if (teamDraft.totalPower == 0) {
                    continue;
                }
                titanTeams.add(new TitanTeam(memberDraft.id, titanTeams.size(), teamDraft.members, teamDraft.totalPower,
                        teamDraft.lastModified));
            }
            members.add(new GuildMember(memberDraft.id, memberDraft.name, heroTeams, titanTeams));
        }
        return new Guild(draft.id, draft.name, members, draft.season, draft.seasonStart);
    }
}
