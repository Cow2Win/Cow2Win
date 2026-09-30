package org.c2w.gui.guild;

import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Turns on a {@link TeamEditorPanel}'s optional war flag and pet combo boxes
 * (in this order, between the power field and the member slots) - hero teams
 * only, titan teams have neither.
 *
 * Per Clash of Worlds rules, a member can field each pet and each war flag in
 * at most ONE of their hero teams (see {@code GuildMember}). The two
 * suppliers return the ids already used by the member's OTHER hero teams;
 * they are evaluated every time the respective dropdown opens, so the lists
 * always reflect the current state of every other team of that member - an
 * id in there is hidden from the dropdown (the team's own current selection
 * always stays selectable). Since which teams count as "other teams of the
 * same member" depends on the dialog (a fixed member in
 * {@link MemberEditorPanel}, a per-row member combo in
 * {@code FortificationEntryDialog}/{@link GuildTeamEntryDialog}), every
 * dialog supplies its own - see {@link #forOtherDrafts}. This only guides the
 * user; {@link #confirmNoConflict} is the save-time safety net.
 */
public record TeamExtras(Supplier<Set<String>> blockedPetIds, Supplier<Set<String>> blockedWarFlagIds) {

    /** Icon size of the war flag/pet combo boxes - same as the member slots' icons in every dialog. */
    public static final int ICON_SIZE = 32;

    /** Language file key for the warning shown by {@link #confirmNoConflict}. */
    private static final String KEY_CONFLICT = "teamEditor.petWarFlagConflict";

    public TeamExtras {
        blockedPetIds = blockedPetIds == null ? Set::of : blockedPetIds;
        blockedWarFlagIds = blockedWarFlagIds == null ? Set::of : blockedWarFlagIds;
    }

    /**
     * Blocks every pet/war flag used by one of the drafts {@code otherDrafts}
     * returns - i.e. the member's other hero team drafts, as far as the
     * calling dialog can tell (see class Javadoc).
     */
    public static TeamExtras forOtherDrafts(Supplier<? extends Collection<? extends TeamDraft<?>>> otherDrafts) {
        return new TeamExtras(() -> petIdsOf(otherDrafts.get()), () -> warFlagIdsOf(otherDrafts.get()));
    }

    static Set<String> petIdsOf(Collection<? extends TeamDraft<?>> drafts) {
        Set<String> ids = new HashSet<>();
        for (TeamDraft<?> draft : drafts) {
            if (draft.pet != null) {
                ids.add(draft.pet.id());
            }
        }
        return ids;
    }

    static Set<String> warFlagIdsOf(Collection<? extends TeamDraft<?>> drafts) {
        Set<String> ids = new HashSet<>();
        for (TeamDraft<?> draft : drafts) {
            if (draft.warFlag != null) {
                ids.add(draft.warFlag.id());
            }
        }
        return ids;
    }

    /**
     * Save-time check behind every dialog that edits hero teams: if
     * {@code draft} still has a member using the same pet/war flag in two
     * hero teams (see {@link GuildDraftConverter#findPetWarFlagConflict}),
     * shows a warning naming the member and the pet/war flag and returns
     * false - the caller must then abort its save, so the user can fix it
     * instead of {@link GuildDraftConverter#toGuild} silently dropping it.
     */
    public static boolean confirmNoConflict(Component parent, GuildDraft draft) {
        Optional<GuildDraftConverter.PetWarFlagConflict> conflict = GuildDraftConverter.findPetWarFlagConflict(draft);
        if (conflict.isEmpty()) {
            return true;
        }
        MemberDraft member = conflict.get().member();
        String memberName = member.name == null || member.name.isBlank() ? member.id : member.name;
        JOptionPane.showMessageDialog(parent,
                LanguageService.displayName(KEY_CONFLICT, memberName, LanguageService.displayName(conflict.get().itemId())),
                LanguageService.displayName("common.saveNotPossibleTitle"), JOptionPane.WARNING_MESSAGE);
        return false;
    }

    /** Every draft in {@code drafts} except {@code self} (by identity) - the common "other teams" of {@link MemberEditorPanel}. */
    static <T> List<TeamDraft<T>> allExcept(List<TeamDraft<T>> drafts, TeamDraft<T> self) {
        List<TeamDraft<T>> result = new ArrayList<>();
        for (TeamDraft<T> draft : drafts) {
            if (draft != self) {
                result.add(draft);
            }
        }
        return result;
    }
}
