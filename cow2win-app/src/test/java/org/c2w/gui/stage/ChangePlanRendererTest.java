package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ChangePlanRenderer}: texts of the checklist and the plain text with [x] / [ ]. */
class ChangePlanRendererTest {

    private static final Guild GUILD = new Guild("alpha", "Alpha", List.of(
            new GuildMember("m1", "Anna", List.of(), List.of()),
            new GuildMember("m2", "Bert", List.of(), List.of())));

    private static TeamKey hero(String memberId, int index) {
        return new TeamKey(memberId, Lineup.TeamType.HERO, index);
    }

    @Test
    @DisplayName("An entry is \"member · team\"; a fortification change adds \"→ new\" / \"← old fortification\"")
    void itemText() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.MOVE, hero("m1", 0), "bastion", "citadel"),
                new ChangeStep(ChangeType.PLACE, hero("m2", 1), null, "citadel")), GUILD);
        String team = "Anna · " + GuildMember.teamLabel(0);
        String bastion = LanguageService.displayName("bastion");
        String citadel = LanguageService.displayName("citadel");

        ChangePlanOutline.Item removal = outline.items().get(0);
        assertEquals(team + " " + LanguageService.displayName("changePlan.movedTo", citadel),
                ChangePlanRenderer.itemText(removal, GUILD));
        ChangePlanOutline.Item addition = outline.item(removal.partnerId()).orElseThrow();
        assertEquals(team + " " + LanguageService.displayName("changePlan.movedFrom", bastion),
                ChangePlanRenderer.itemText(addition, GUILD));
        ChangePlanOutline.Item place = outline.items().stream().filter(i -> !i.isMovePart()).findFirst().orElseThrow();
        assertEquals("Bert · " + GuildMember.teamLabel(1), ChangePlanRenderer.itemText(place, GUILD));
    }

    @Test
    @DisplayName("Plain text: heading, summary, type → section → fortification → entries with [x] / [ ]")
    void plainText() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.REMOVE, hero("m1", 0), "bastion", null),
                new ChangeStep(ChangeType.PLACE, hero("m2", 1), null, "citadel")), GUILD);
        String checkedId = outline.items().get(0).id();

        String text = ChangePlanRenderer.plainText(outline, Set.of(checkedId), GUILD);

        String expected = LanguageService.displayName("changePlan.heading") + "\n"
                + LanguageService.displayName("changePlan.checkedSummary", 2, 1) + "\n"
                + "\n" + LanguageService.displayName("teamType.HERO") + "\n"
                + "  " + LanguageService.displayName("changePlan.sectionRemove", 1) + "\n"
                + "    " + LanguageService.displayName("bastion") + "\n"
                + "      [x] Anna · " + GuildMember.teamLabel(0) + "\n"
                + "  " + LanguageService.displayName("changePlan.sectionAdd", 1) + "\n"
                + "    " + LanguageService.displayName("citadel") + "\n"
                + "      [ ] Bert · " + GuildMember.teamLabel(1) + "\n";
        assertEquals(expected, text);
    }

    @Test
    @DisplayName("No entries: the \"no changes\" text")
    void noChanges() {
        String text = ChangePlanRenderer.plainText(ChangePlanOutline.of(List.of(), GUILD), Set.of(), GUILD);
        assertEquals(LanguageService.displayName("changePlan.heading") + "\n"
                + LanguageService.displayName("changePlan.noChanges") + "\n", text);
    }
}
