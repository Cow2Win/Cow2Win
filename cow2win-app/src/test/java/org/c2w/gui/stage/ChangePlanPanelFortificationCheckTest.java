package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.ChangePlanCheckRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.ChangePlanOutline.Kind;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ChangePlanPanel}: the checkbox in front of each fortification that checks or unchecks
 * all its entries - headless, never shown (like {@code OutputStageViewTest}).
 *
 * <p>Live: m1, m2, m6 in the bastion, titan m5 in the bastion of fire; target (the open lineup,
 * unsaved): m2, m3, m4 in the bastion, m1 in the citadel, titan m7 in the bastion of fire. So the
 * bastion appears under "remove" (m1 - half of a move -, m6) and under "add" (m3, m4), the
 * citadel under "add" (m1, the other half of the move).
 */
class ChangePlanPanelFortificationCheckTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private ChangePlanPanel panel;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");

        Path guildDir = context.guildFilePath().getParent();
        LineupRepository.save(new Lineup(context.guild().id(), context.guild().name(), "", LocalDateTime.now(), List.of(
                        hero("bastion", "m1"), hero("bastion", "m2"), hero("bastion", "m6"),
                        new Lineup.Entry("bastion-of-fire", "m5", Lineup.TeamType.TITAN, 0))),
                LineupFiles.originalPathFor(guildDir));
        context.setLineup(new Lineup(context.lineup().guildId(), context.lineup().guildName(), "", LocalDateTime.now(), List.of(
                hero("bastion", "m2"), hero("bastion", "m3"), hero("bastion", "m4"), hero("citadel", "m1"),
                new Lineup.Entry("bastion-of-fire", "m7", Lineup.TeamType.TITAN, 0))));
        context.setLineupDirty(true);
        context.setFortificationType(FortificationType.HERO);
        panel = new ChangePlanPanel(context, true);
        assertTrue(panel.hasPlan());
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    private static Lineup.Entry hero(String fortificationId, String memberId) {
        return new Lineup.Entry(fortificationId, memberId, Lineup.TeamType.HERO, 0);
    }

    private String id(Kind kind, String memberId) {
        return panel.outline().items().stream()
                .filter(item -> item.kind() == kind && item.teamKey().teamMemberId().equals(memberId))
                .findFirst().orElseThrow().id();
    }

    private void assertChecked(boolean expected, String... itemIds) {
        for (String itemId : itemIds) {
            assertEquals(expected, panel.checks().isChecked(itemId), itemId);
            assertEquals(expected, panel.checkBox(itemId).isSelected(), itemId);
            assertEquals(expected, panel.checkBox(itemId).getText().contains("<s>"), itemId + " struck through");
        }
    }

    @Test
    @DisplayName("Each fortification has a checkbox with its name and \"(0/n)\", unchecked, in the entries' color")
    void initialState() {
        JCheckBox addBastion = panel.fortificationCheckBox(Kind.ADD, "bastion");
        assertEquals(ChangePlanRenderer.fortificationName("bastion"), addBastion.getText());
        assertFalse(addBastion.isSelected());
        assertEquals("(0/2)", panel.fortificationCountText(Kind.ADD, "bastion"));
        assertEquals("(0/2)", panel.fortificationCountText(Kind.REMOVE, "bastion"));
        assertEquals("(0/1)", panel.fortificationCountText(Kind.ADD, "citadel"));
        assertEquals(ChangePlanPanel.itemColor(FortificationType.HERO, false), addBastion.getForeground());
        assertEquals(LanguageService.displayName("changePlan.fortification.checkAll"), addBastion.getToolTipText());
        assertNull(panel.fortificationCheckBox(Kind.ADD, "bastion-of-fire"), "titans are not shown");
    }

    @Test
    @DisplayName("A click checks all entries of the group, saves once, notifies once; a second click unchecks them all")
    void clickChecksAndUnchecksAll() {
        AtomicInteger notified = new AtomicInteger();
        panel.addPlanListener(notified::incrementAndGet);
        int savesBefore = panel.saveCount();
        JCheckBox addBastion = panel.fortificationCheckBox(Kind.ADD, "bastion");
        String m3 = id(Kind.ADD, "m3");
        String m4 = id(Kind.ADD, "m4");

        addBastion.doClick();

        assertChecked(true, m3, m4);
        assertTrue(addBastion.isSelected());
        assertEquals("(2/2)", panel.fortificationCountText(Kind.ADD, "bastion"));
        assertEquals(ChangePlanPanel.itemColor(FortificationType.HERO, true), addBastion.getForeground());
        assertFalse(addBastion.getText().contains("<s>"), "the name is not struck through");
        assertEquals(LanguageService.displayName("changePlan.fortification.uncheckAll"), addBastion.getToolTipText());
        assertEquals(LanguageService.displayName("changePlan.checkedSummary", 5, 2), panel.summaryText());
        assertTrue(panel.applyButton().isEnabled());
        assertEquals(1, notified.get(), "plan listeners once");
        assertEquals(savesBefore + 1, panel.saveCount(), "saved once");
        assertEquals(panel.checks().checkedIds(),
                ChangePlanCheckRepository.load(context.guildFilePath().getParent()).checkedIds(), "saved to the file");

        addBastion.doClick();

        assertChecked(false, m3, m4);
        assertFalse(addBastion.isSelected());
        assertEquals("(0/2)", panel.fortificationCountText(Kind.ADD, "bastion"));
        assertFalse(panel.applyButton().isEnabled());
        assertEquals(2, notified.get());
        assertEquals(savesBefore + 2, panel.saveCount());
    }

    @Test
    @DisplayName("Partly checked: unchecked with \"(k/n)\", a click checks all")
    void partlyChecked() {
        String m1 = id(Kind.REMOVE, "m1");
        String m6 = id(Kind.REMOVE, "m6");
        panel.checkBox(m6).doClick();

        JCheckBox removeBastion = panel.fortificationCheckBox(Kind.REMOVE, "bastion");
        assertFalse(removeBastion.isSelected());
        assertEquals("(1/2)", panel.fortificationCountText(Kind.REMOVE, "bastion"));

        removeBastion.doClick();
        assertChecked(true, m1, m6);
        assertTrue(removeBastion.isSelected());
        assertEquals("(2/2)", panel.fortificationCountText(Kind.REMOVE, "bastion"));
    }

    @Test
    @DisplayName("Checking the last entry one by one checks the fortification; unchecking one unchecks it")
    void followsSingleEntries() {
        String m3 = id(Kind.ADD, "m3");
        String m4 = id(Kind.ADD, "m4");
        JCheckBox addBastion = panel.fortificationCheckBox(Kind.ADD, "bastion");

        panel.checkBox(m3).doClick();
        assertFalse(addBastion.isSelected());
        assertEquals("(1/2)", panel.fortificationCountText(Kind.ADD, "bastion"));
        panel.checkBox(m4).doClick();
        assertTrue(addBastion.isSelected());

        panel.setChecked(m3, false);
        assertFalse(addBastion.isSelected());
        assertEquals("(1/2)", panel.fortificationCountText(Kind.ADD, "bastion"));
    }

    @Test
    @DisplayName("Only that group changes: not the same fortification in the other section, not the partner of a move")
    void onlyThatGroup() {
        String removeM1 = id(Kind.REMOVE, "m1");
        String removeM6 = id(Kind.REMOVE, "m6");
        String addM1 = id(Kind.ADD, "m1");

        panel.fortificationCheckBox(Kind.ADD, "bastion").doClick();
        assertChecked(false, removeM1, removeM6);
        assertFalse(panel.fortificationCheckBox(Kind.REMOVE, "bastion").isSelected());
        assertEquals("(0/2)", panel.fortificationCountText(Kind.REMOVE, "bastion"));

        panel.fortificationCheckBox(Kind.REMOVE, "bastion").doClick();
        assertChecked(true, removeM1, removeM6);
        assertChecked(false, addM1);
        assertFalse(panel.fortificationCheckBox(Kind.ADD, "citadel").isSelected(), "the move's other half stays");

        long titanChecks = panel.outline().ofType(FortificationType.TITAN).items().stream()
                .map(ChangePlanOutline.Item::id).filter(panel.checks()::isChecked).count();
        assertEquals(0, titanChecks, "the other fortification type stays");
    }

    @Test
    @DisplayName("After a rebuild (type switch and back) the fortification checkboxes follow the saved checks")
    void afterRebuild() {
        panel.fortificationCheckBox(Kind.ADD, "bastion").doClick();
        panel.checkBox(id(Kind.REMOVE, "m6")).doClick();

        context.setFortificationType(FortificationType.TITAN);
        assertNull(panel.fortificationCheckBox(Kind.ADD, "bastion"));
        assertNotNull(panel.fortificationCheckBox(Kind.ADD, "bastion-of-fire"));
        context.setFortificationType(FortificationType.HERO);

        assertTrue(panel.fortificationCheckBox(Kind.ADD, "bastion").isSelected());
        assertEquals("(2/2)", panel.fortificationCountText(Kind.ADD, "bastion"));
        assertFalse(panel.fortificationCheckBox(Kind.REMOVE, "bastion").isSelected());
        assertEquals("(1/2)", panel.fortificationCountText(Kind.REMOVE, "bastion"));

        ChangePlanPanel restarted = new ChangePlanPanel(context, true);
        assertTrue(restarted.fortificationCheckBox(Kind.ADD, "bastion").isSelected());
        assertEquals("(1/2)", restarted.fortificationCountText(Kind.REMOVE, "bastion"));
    }
}
