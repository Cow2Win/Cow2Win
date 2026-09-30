package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.infra.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


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
                TeamDraft<Hero> teamDraft = toTeamDraft(team.heroes(), team.totalPower(), team.lastModified());
                teamDraft.pet = team.pet();
                teamDraft.warFlag = team.warFlag();
                memberDraft.heroTeams.add(teamDraft);
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

    /** A member using the pet/war flag with id {@code itemId} in more than one (non-empty) hero team - see {@link #findPetWarFlagConflict}. */
    public record PetWarFlagConflict(MemberDraft member, String itemId) {
    }

    /**
     * The first member (if any) whose non-empty hero team drafts (totalPower
     * != 0 - empty ones are dropped by {@link #toGuild} anyway) use the same
     * pet or war flag more than once - which {@link GuildMember} forbids.
     * Used by the editing dialogs to refuse a save with a warning (see
     * {@link TeamExtras#confirmNoConflict}) before {@link #toGuild} would
     * silently drop the duplicate.
     */
    public static Optional<PetWarFlagConflict> findPetWarFlagConflict(GuildDraft draft) {
        for (MemberDraft memberDraft : draft.members) {
            Set<String> usedPetIds = new HashSet<>();
            Set<String> usedWarFlagIds = new HashSet<>();
            for (TeamDraft<Hero> teamDraft : memberDraft.heroTeams) {
                if (teamDraft.totalPower == 0) {
                    continue;
                }
                if (teamDraft.pet != null && !usedPetIds.add(teamDraft.pet.id())) {
                    return Optional.of(new PetWarFlagConflict(memberDraft, teamDraft.pet.id()));
                }
                if (teamDraft.warFlag != null && !usedWarFlagIds.add(teamDraft.warFlag.id())) {
                    return Optional.of(new PetWarFlagConflict(memberDraft, teamDraft.warFlag.id()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Builds an immutable guild from the current state of the draft. A team
     * with totalPower == 0 counts as "empty": it does not need to be removed
     * by hand first, it is simply dropped here on save, along with whatever
     * slot selections it might still carry.
     *
     * A pet/war flag already used by an earlier hero team of the same
     * member (e.g. after moving a team to another member in
     * {@link GuildHeroEntryDialog}) is logged and dropped from the later
     * team instead of failing the save on {@link GuildMember}'s "at most
     * once per member" rule - same treatment as {@code GuildRepository}
     * gives a guild file on load.
     */
    public static Guild toGuild(GuildDraft draft) {
        List<GuildMember> members = new ArrayList<>();
        for (MemberDraft memberDraft : draft.members) {
            List<HeroTeam> heroTeams = new ArrayList<>();
            Set<String> usedPetIds = new HashSet<>();
            Set<String> usedWarFlagIds = new HashSet<>();
            for (TeamDraft<Hero> teamDraft : memberDraft.heroTeams) {
                if (teamDraft.totalPower == 0) {
                    continue;
                }
                Pet pet = teamDraft.pet;
                if (pet != null && !usedPetIds.add(pet.id())) {
                    Logger.log("Pet '" + pet.id() + "' is already used by another hero team of member '"
                            + memberDraft.id + "', dropping it from team " + (heroTeams.size() + 1));
                    pet = null;
                }
                WarFlag warFlag = teamDraft.warFlag;
                if (warFlag != null && !usedWarFlagIds.add(warFlag.id())) {
                    Logger.log("War flag '" + warFlag.id() + "' is already used by another hero team of member '"
                            + memberDraft.id + "', dropping it from team " + (heroTeams.size() + 1));
                    warFlag = null;
                }
                heroTeams.add(new HeroTeam(memberDraft.id, heroTeams.size(), teamDraft.members, pet, warFlag,
                        teamDraft.totalPower, teamDraft.lastModified));
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
