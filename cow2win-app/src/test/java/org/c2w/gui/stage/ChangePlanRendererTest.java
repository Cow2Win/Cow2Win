package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
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

    private static TeamKey titan(String memberId, int index) {
        return new TeamKey(memberId, Lineup.TeamType.TITAN, index);
    }

    @Test
    @DisplayName("An entry is only the member's name - no team, no other fortification, also for a fortification change")
    void itemText() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.MOVE, hero("m1", 0), "bastion", "citadel"),
                new ChangeStep(ChangeType.PLACE, hero("m2", 1), null, "citadel"),
                new ChangeStep(ChangeType.PLACE, hero("m1", 1), null, "citadel")), GUILD);

        ChangePlanOutline.Item removal = outline.items().stream().filter(i -> i.isMovePart()).findFirst().orElseThrow();
        assertEquals("Anna", ChangePlanRenderer.itemText(removal, GUILD));
        ChangePlanOutline.Item addition = outline.item(removal.partnerId()).orElseThrow();
        assertEquals("Anna", ChangePlanRenderer.itemText(addition, GUILD));
        assertEquals(List.of("Anna", "Anna", "Bert"), outline.items().stream()
                .filter(i -> i.kind() == ChangePlanOutline.Kind.ADD)
                .map(i -> ChangePlanRenderer.itemText(i, GUILD)).toList(), "two teams of Anna in the citadel");
    }

    @Test
    @DisplayName("Plain text of one type: heading, summary, type → section → fortification → names with [x] / [ ]")
    void plainText() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.REMOVE, hero("m1", 0), "bastion", null),
                new ChangeStep(ChangeType.PLACE, hero("m2", 1), null, "citadel"),
                new ChangeStep(ChangeType.PLACE, titan("m1", 0), null, "bastion-of-fire")), GUILD);
        String checkedId = outline.items().get(0).id();

        String text = ChangePlanRenderer.plainText(outline, Set.of(checkedId), GUILD, FortificationType.HERO);

        String expected = LanguageService.displayName("changePlan.heading") + "\n"
                + LanguageService.displayName("changePlan.checkedSummary", 2, 1) + "\n"
                + "\n" + LanguageService.displayName("teamType.HERO") + "\n"
                + "  " + LanguageService.displayName("changePlan.sectionRemove", 1) + "\n"
                + "    " + LanguageService.displayName("bastion") + "\n"
                + "      [x] Anna\n"
                + "  " + LanguageService.displayName("changePlan.sectionAdd", 1) + "\n"
                + "    " + LanguageService.displayName("citadel") + "\n"
                + "      [ ] Bert\n";
        assertEquals(expected, text);

        String titans = ChangePlanRenderer.plainText(outline, Set.of(checkedId), GUILD, FortificationType.TITAN);
        assertTrue(titans.contains(LanguageService.displayName("teamType.TITAN")), titans);
        assertTrue(titans.contains(LanguageService.displayName("changePlan.checkedSummary", 1, 0)), titans);
        assertFalse(titans.contains(LanguageService.displayName("teamType.HERO")), titans);
        assertFalse(titans.contains("Bert"), titans);
    }

    @Test
    @DisplayName("No entries of the chosen type: \"no changes for …\" plus the other type's entries")
    void noChangesForType() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.PLACE, hero("m2", 1), null, "citadel")), GUILD);

        String text = ChangePlanRenderer.plainText(outline, Set.of(), GUILD, FortificationType.TITAN);

        assertEquals(LanguageService.displayName("changePlan.heading") + "\n"
                + LanguageService.displayName("changePlan.noChangesForType", LanguageService.displayName("teamType.TITAN")) + "\n"
                + LanguageService.displayName("changePlan.otherTypeEntries", LanguageService.displayName("teamType.HERO"), 1) + "\n",
                text);
    }

    @Test
    @DisplayName("No entries: the \"no changes\" text")
    void noChanges() {
        String text = ChangePlanRenderer.plainText(ChangePlanOutline.of(List.of(), GUILD), Set.of(), GUILD, FortificationType.HERO);
        assertEquals(LanguageService.displayName("changePlan.heading") + "\n"
                + LanguageService.displayName("changePlan.noChanges") + "\n", text);
    }

    @Test
    @DisplayName("The button text names the type")
    void applyToLiveText() {
        assertEquals(LanguageService.displayName("changePlan.applyToLive.HERO"), ChangePlanRenderer.applyToLive(FortificationType.HERO));
        assertEquals(LanguageService.displayName("changePlan.applyToLive.TITAN"), ChangePlanRenderer.applyToLive(FortificationType.TITAN));
    }
}
