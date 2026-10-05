package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ChangePlanRenderer}: the same structure as the former dialog's plan, as HTML and plain text. */
class ChangePlanRendererTest {

    private static final Guild GUILD = new Guild("alpha", "Alpha", List.of(
            new GuildMember("anna", "Anna", List.of(), List.of()),
            new GuildMember("bert", "Bert", List.of(), List.of())));

    private static ChangeStep step(ChangeType type, String memberId, String from, String to) {
        return new ChangeStep(type, new TeamKey(memberId, Lineup.TeamType.HERO, 0), from, to);
    }

    @Test
    @DisplayName("Heading, summary and the three sections remove / move / place, numbered, in HTML and plain text")
    void structure() {
        List<ChangeStep> steps = List.of(
                step(ChangeType.REMOVE, "anna", "bastion", null),
                step(ChangeType.MOVE, "bert", "bastion", "shooting-range"),
                step(ChangeType.MOVE, "anna", "shooting-range", "bastion"),
                step(ChangeType.PLACE, "bert", null, "bastion"));

        ChangePlanRenderer.RenderedPlan plan = ChangePlanRenderer.render(steps, GUILD);

        String heading = LanguageService.displayName("changePlan.heading");
        String summary = LanguageService.displayName("changePlan.summary", 4, 1L, 2L, 1L);
        String remove = LanguageService.displayName("changePlan.sectionRemove") + " (1)";
        String move = LanguageService.displayName("changePlan.sectionMove") + " (2)";
        String place = LanguageService.displayName("changePlan.sectionPlace") + " (1)";
        String firstMove = ChangePlanRenderer.sentenceFor(steps.get(1), GUILD);
        String secondMove = ChangePlanRenderer.sentenceFor(steps.get(2), GUILD);

        assertEquals(heading + "\n" + summary + "\n"
                + "\n" + remove + "\n1. " + ChangePlanRenderer.sentenceFor(steps.get(0), GUILD) + "\n"
                + "\n" + move + "\n1. " + firstMove + "\n2. " + secondMove + "\n"
                + "\n" + place + "\n1. " + ChangePlanRenderer.sentenceFor(steps.get(3), GUILD) + "\n", plan.plainText());
        assertTrue(firstMove.contains("Bert"), "member name, not id: " + firstMove);

        String html = plan.html();
        assertTrue(html.startsWith("<html>") && html.endsWith("</html>"));
        assertTrue(html.contains("<h2>" + heading + "</h2>"));
        assertTrue(html.contains("<p><b>" + summary + "</b></p>"));
        assertTrue(html.indexOf("<h3>" + remove) < html.indexOf("<h3>" + move));
        assertTrue(html.indexOf("<h3>" + move) < html.indexOf("<h3>" + place));
        assertEquals(3, html.split("<ol>", -1).length - 1, "one numbered list per section");
    }

    @Test
    @DisplayName("No steps: the \"no changes\" text instead of summary and sections")
    void noChanges() {
        ChangePlanRenderer.RenderedPlan plan = ChangePlanRenderer.render(List.of(), GUILD);
        String noChanges = LanguageService.displayName("changePlan.noChanges");
        assertEquals(LanguageService.displayName("changePlan.heading") + "\n" + noChanges + "\n", plan.plainText());
        assertTrue(plan.html().contains(noChanges));
        assertFalse(plan.html().contains("<ol>"));
    }
}
