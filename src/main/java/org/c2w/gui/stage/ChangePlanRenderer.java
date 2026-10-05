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
 * fortification types and sections, fortification names, the text of an entry ("member · team",
 * for a fortification change with "→ new" / "← old fortification") and the whole plan as
 * indented plain text with "[x]" / "[ ]" for the clipboard.
 */
public final class ChangePlanRenderer {

    private static final String KEY_HEADING = "changePlan.heading";
    private static final String KEY_NO_CHANGES = "changePlan.noChanges";
    private static final String KEY_CHECKED_SUMMARY = "changePlan.checkedSummary";
    private static final String KEY_SECTION_REMOVE = "changePlan.sectionRemove";
    private static final String KEY_SECTION_ADD = "changePlan.sectionAdd";
    private static final String KEY_MOVED_TO = "changePlan.movedTo";
    private static final String KEY_MOVED_FROM = "changePlan.movedFrom";

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

    /** "{n} entries, {k} checked". */
    static String checkedSummary(int entries, int checked) {
        return LanguageService.displayName(KEY_CHECKED_SUMMARY, entries, checked);
    }

    /** "Heroes" / "Titans". */
    static String typeHeading(FortificationType type) {
        return LanguageService.displayName("teamType." + type.name());
    }

    /** "Remove ({n})" / "Add ({n})". */
    static String sectionHeading(Section section) {
        return LanguageService.displayName(section.kind() == ChangePlanOutline.Kind.REMOVE ? KEY_SECTION_REMOVE : KEY_SECTION_ADD,
                section.itemCount());
    }

    /** One entry, e.g. "Anna · Team 1" or, for a fortification change, "Anna · Team 1 → Bastion". */
    static String itemText(Item item, Guild guild) {
        String text = memberName(item.teamKey().teamMemberId(), guild) + " · "
                + GuildMember.teamLabel(item.teamKey().teamIndex());
        if (item.isMovePart()) {
            String other = fortificationName(item.otherFortificationId());
            text += " " + LanguageService.displayName(
                    item.kind() == ChangePlanOutline.Kind.REMOVE ? KEY_MOVED_TO : KEY_MOVED_FROM, other);
        }
        return text;
    }

    /** The display name of a fortification, its id if unknown. */
    static String fortificationName(String fortificationId) {
        return fortificationId == null
                ? LanguageService.displayName("common.none")
                : FortificationRepository.findById(fortificationId)
                        .map(f -> LanguageService.displayName(f.id()))
                        .orElse(fortificationId);
    }

    /** The whole plan as indented plain text, "[x]" before checked and "[ ]" before open entries. */
    static String plainText(ChangePlanOutline outline, Set<String> checkedIds, Guild guild) {
        StringBuilder text = new StringBuilder(heading()).append("\n");
        if (outline.isEmpty()) {
            return text.append(noChanges()).append("\n").toString();
        }
        int checked = (int) outline.items().stream().filter(item -> checkedIds.contains(item.id())).count();
        text.append(checkedSummary(outline.items().size(), checked)).append("\n");
        for (TypeGroup type : outline.types()) {
            text.append("\n").append(typeHeading(type.type())).append("\n");
            for (Section section : type.sections()) {
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
                .map(GuildMember::name)
                .orElse(memberId);
    }
}
