package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.ChangePlanOutline.FortificationGroup;
import org.c2w.domain.ChangePlanOutline.Item;
import org.c2w.domain.ChangePlanOutline.Section;
import org.c2w.domain.ChangePlanOutline.TypeGroup;
import org.c2w.i18n.LanguageService;

import java.util.Set;

/**
 * GUI-free texts of the change plan checklist ({@link ChangePlanOutline}): headings of
 * fortification types and sections, fortification names, the text of an entry (only the member's
 * name) and the plan of one fortification type as indented plain text with "[x]" / "[ ]" for the clipboard.
 */
public final class ChangePlanRenderer {

    private static final String KEY_HEADING = "changePlan.heading";
    private static final String KEY_NO_CHANGES = "changePlan.noChanges";
    private static final String KEY_NO_CHANGES_FOR_TYPE = "changePlan.noChangesForType";
    private static final String KEY_OTHER_TYPE_ENTRIES = "changePlan.otherTypeEntries";
    private static final String KEY_CHECKED_SUMMARY = "changePlan.checkedSummary";
    private static final String KEY_SECTION_REMOVE = "changePlan.sectionRemove";
    private static final String KEY_SECTION_ADD = "changePlan.sectionAdd";
    private static final String KEY_APPLY_TO_LIVE = "changePlan.applyToLive.";

    private static final String INDENT = "  ";

    private ChangePlanRenderer() {
    }

    /** The heading of the plan, "Change plan: Live → target". */
    static String heading() {
        return LanguageService.displayName(KEY_HEADING);
    }

    /** "No changes needed …". */
    static String noChanges() {
        return LanguageService.displayName(KEY_NO_CHANGES);
    }

    /** "No changes needed for heroes." - the selected type has none, the other one may have some. */
    static String noChangesForType(FortificationType type) {
        return LanguageService.displayName(KEY_NO_CHANGES_FOR_TYPE, typeHeading(type));
    }

    /** "Titans: {n} entries" - the hint on the other type's entries. */
    static String otherTypeEntries(FortificationType type, int entries) {
        return LanguageService.displayName(KEY_OTHER_TYPE_ENTRIES, typeHeading(type), entries);
    }

    /** "Apply heroes to live" / "Apply titans to live" - the label of the button. */
    static String applyToLive(FortificationType type) {
        return LanguageService.displayName(KEY_APPLY_TO_LIVE + type.name());
    }

    /** "{n} entries, {k} checked". */
    static String checkedSummary(int entries, int checked) {
        return LanguageService.displayName(KEY_CHECKED_SUMMARY, entries, checked);
    }

    /** "Heroes" / "Titans". */
    static String typeHeading(FortificationType type) {
        return LanguageService.displayName("teamType." + type.name());
    }

    /** The other fortification type. */
    static FortificationType otherType(FortificationType type) {
        return type == FortificationType.HERO ? FortificationType.TITAN : FortificationType.HERO;
    }

    /** "Remove ({n})" / "Add ({n})". */
    static String sectionHeading(Section section) {
        return LanguageService.displayName(section.kind() == ChangePlanOutline.Kind.REMOVE ? KEY_SECTION_REMOVE : KEY_SECTION_ADD,
                section.itemCount());
    }

    /** One entry: only the member's name, e.g. "Anna" - no team, no other fortification. */
    static String itemText(Item item, Guild guild) {
        return memberName(item.teamKey().teamMemberId(), guild);
    }

    /** The display name of a fortification, its id if unknown. */
    static String fortificationName(String fortificationId) {
        return fortificationId == null
                ? LanguageService.displayName("common.none")
                : FortificationRepository.findById(fortificationId)
                        .map(f -> LanguageService.displayName(f.id()))
                        .orElse(fortificationId);
    }

    /**
     * The plan of fortification type {@code type} as indented plain text, "[x]" before checked
     * and "[ ]" before open entries. Without entries of {@code type}: "no changes for {type}",
     * plus how many entries the other type has, if any.
     */
    static String plainText(ChangePlanOutline outline, Set<String> checkedIds, Guild guild, FortificationType type) {
        StringBuilder text = new StringBuilder(heading()).append("\n");
        if (outline.isEmpty()) {
            return text.append(noChanges()).append("\n").toString();
        }
        ChangePlanOutline shown = outline.ofType(type);
        if (shown.isEmpty()) {
            text.append(noChangesForType(type)).append("\n");
            int otherEntries = outline.ofType(otherType(type)).items().size();
            if (otherEntries > 0) {
                text.append(otherTypeEntries(otherType(type), otherEntries)).append("\n");
            }
            return text.toString();
        }
        int checked = (int) shown.items().stream().filter(item -> checkedIds.contains(item.id())).count();
        text.append(checkedSummary(shown.items().size(), checked)).append("\n");
        for (TypeGroup typeGroup : shown.types()) {
            text.append("\n").append(typeHeading(typeGroup.type())).append("\n");
            for (Section section : typeGroup.sections()) {
                text.append(INDENT).append(sectionHeading(section)).append("\n");
                for (FortificationGroup group : section.fortifications()) {
                    text.append(INDENT.repeat(2)).append(fortificationName(group.fortificationId())).append("\n");
                    for (Item item : group.items()) {
                        text.append(INDENT.repeat(3)).append(checkedIds.contains(item.id()) ? "[x] " : "[ ] ")
                                .append(itemText(item, guild)).append("\n");
                    }
                }
            }
        }
        return text.toString();
    }

    /** The member's name in {@code guild}, or the id if the guild has no such member. */
    static String memberName(String memberId, Guild guild) {
        if (guild == null || guild.members() == null) {
            return memberId;
        }
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .map(GuildMember::displayName)
                .orElse(memberId);
    }
}
