package org.c2w.gui.stage;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Role;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.model.Lineup;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.AlgorithmSelection;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.cowscore.CowScoreTestSupport;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.fort.FortificationValueMode;
import org.c2w.gui.fort.HeroLineupSummaryPanel;
import org.c2w.gui.fort.TitanLineupSummaryPanel;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ConceptStageView}: info panel and "fortification values" combo box - headless, never shown. */
class ConceptStageViewTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private FortificationMapPanel map;
    private ConceptStageView view;
    private MainActions actions;
    private AlgorithmSelection selection;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        map = new FortificationMapPanel(context);
        this.actions = actions;
        selection = new AlgorithmSelection();
        view = new ConceptStageView(context, actions, selection, map);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Selecting a fortification shows its name; clearing the selection shows the hint again")
    void selectedFortificationSection() {
        String hint = LanguageService.displayName("stageInfo.fortification.none");
        assertTrue(view.fortificationSectionTexts().stream().anyMatch(text -> text.contains(hint)));

        Fortification fort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO).findFirst().orElseThrow();
        view.showFortification(Optional.of(fort));
        assertTrue(view.fortificationSectionTexts().contains(LanguageService.displayName(fort.id())),
                view.fortificationSectionTexts().toString());
        assertTrue(view.fortificationSectionTexts().stream().noneMatch(text -> text.contains(hint)));

        view.showFortification(Optional.empty());
        assertTrue(view.fortificationSectionTexts().stream().anyMatch(text -> text.contains(hint)));
    }

    @Test
    @DisplayName("The selected fortification lists its assigned teams, \"no teams\" otherwise, and the double-click hint")
    void selectedFortificationTeams() {
        Guild current = context.guild();
        context.setGuild(new Guild(current.id(), current.name(), List.of(
                new GuildMember("m1", "Zora", List.of(new HeroTeam("m1", 0, List.of(new Hero("h1", List.of(Role.MAGE))), 1000)),
                        List.of()))));
        Fortification fort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO).findFirst().orElseThrow();
        String noTeams = LanguageService.displayName("stageInfo.fortification.noTeams");
        String hint = LanguageService.displayName("stageInfo.fortification.doubleClickHint");

        view.showFortification(Optional.of(fort));
        assertTrue(view.fortificationSectionTexts().stream().anyMatch(text -> text.contains(noTeams)));
        assertTrue(view.fortificationSectionTexts().stream().anyMatch(text -> text.contains(hint)));

        new LineupService(context).assignTeam("m1", Lineup.TeamType.HERO, 0, fort);
        assertTrue(view.fortificationSectionTexts().contains("Zora"), view.fortificationSectionTexts().toString());
        assertTrue(view.fortificationSectionTexts().stream().noneMatch(text -> text.contains(noTeams)));
    }

    @Test
    @DisplayName("The \"fortification values\" combo box starts with power and sets the map's value mode")
    void valueModeBox() {
        assertEquals(FortificationValueMode.POWER, view.valueModeBox().getSelectedItem());
        assertEquals(FortificationValueMode.POWER, map.valueMode());

        view.valueModeBox().setSelectedItem(FortificationValueMode.CHANGES);
        assertEquals(FortificationValueMode.CHANGES, map.valueMode());
        view.valueModeBox().setSelectedItem(FortificationValueMode.LIVE_COMPARISON);
        assertEquals(FortificationValueMode.LIVE_COMPARISON, map.valueMode());
        view.valueModeBox().setSelectedItem(FortificationValueMode.POWER);
        assertEquals(FortificationValueMode.POWER, map.valueMode());
    }

    @Test
    @DisplayName("The overview shows the lineup summary of the selected fortification type")
    void overviewFollowsFortificationType() {
        assertEquals(1, CowScoreTestSupport.findAll(view, HeroLineupSummaryPanel.class).size());
        assertEquals(0, CowScoreTestSupport.findAll(view, TitanLineupSummaryPanel.class).size());

        context.setFortificationType(FortificationType.TITAN);
        assertEquals(0, CowScoreTestSupport.findAll(view, HeroLineupSummaryPanel.class).size());
        assertEquals(1, CowScoreTestSupport.findAll(view, TitanLineupSummaryPanel.class).size());
    }

    @Test
    @DisplayName("The action list is the concept menu plus the \"fortification values\" combo box at the end")
    void actionList() {
        StageActionList list = view.actionList();
        assertEquals(org.c2w.gui.MainMenuBar.menuFor(org.c2w.gui.action.Stage.CONCEPT).allActionIds(),
                list.rows().stream().map(row -> ((AppAction) row.action()).id()).toList());
        java.awt.Component[] components = list.getComponents();
        assertSame(view.valueModeBox().getParent(), components[components.length - 1]);
    }

    @Test
    @DisplayName("The \"algorithm\" combo box sits directly above \"Run assignment\" and offers no \"Manual\"")
    void algorithmBoxAboveRunAssignment() {
        context.setFortificationType(FortificationType.HERO);
        java.util.List<java.awt.Component> components = java.util.List.of(view.actionList().getComponents());
        int index = components.indexOf(view.algorithmBox().getParent().getParent());
        assertTrue(index >= 0);
        assertSame(actions.get(ActionId.RUN_ALGORITHM),
                ((StageActionList.ActionRow) components.get(index + 1)).action());

        assertEquals(LineupAlgorithms.forAssignment(Lineup.TeamType.HERO), items(view.algorithmBox()));
        assertSame(selection.get(Lineup.TeamType.HERO), view.algorithmBox().getSelectedItem());
        assertNull(view.algorithmBox().getToolTipText(), "no tooltip at the combo box");
    }

    @Test
    @DisplayName("The combo box follows the fortification type and keeps the choice per type in the selection")
    void algorithmBoxPerType() {
        context.setFortificationType(FortificationType.HERO);
        LineupAlgorithm cowScoreHero = LineupAlgorithms.forAssignment(Lineup.TeamType.HERO).get(1);
        view.algorithmBox().setSelectedItem(cowScoreHero);
        assertSame(cowScoreHero, selection.get(Lineup.TeamType.HERO));
        LineupAlgorithm titanBefore = selection.get(Lineup.TeamType.TITAN);

        context.setFortificationType(FortificationType.TITAN);
        assertEquals(LineupAlgorithms.forAssignment(Lineup.TeamType.TITAN), items(view.algorithmBox()));
        assertSame(titanBefore, view.algorithmBox().getSelectedItem());
        assertSame(cowScoreHero, selection.get(Lineup.TeamType.HERO), "switching the type chooses nothing");

        context.setFortificationType(FortificationType.HERO);
        assertSame(cowScoreHero, view.algorithmBox().getSelectedItem());
    }

    @Test
    @DisplayName("Below the combo box stands the summary of the chosen algorithm and follows the choice")
    void algorithmSummaryFollowsChoice() {
        context.setFortificationType(FortificationType.HERO);
        for (LineupAlgorithm algorithm : LineupAlgorithms.forAssignment(Lineup.TeamType.HERO)) {
            view.algorithmBox().setSelectedItem(algorithm);
            assertEquals(AlgorithmDescriptions.summaryFor(algorithm), view.algorithmSummary().summary());
            assertFalse(view.algorithmSummary().summary().isBlank());
        }
        java.awt.Container panel = view.algorithmBox().getParent().getParent();
        assertSame(panel, view.algorithmSummary().getParent());

        context.setFortificationType(FortificationType.TITAN);
        assertEquals(AlgorithmDescriptions.summaryFor(selection.get(Lineup.TeamType.TITAN)),
                view.algorithmSummary().summary());
    }

    @Test
    @DisplayName("The summary keeps its height when another algorithm is chosen, at least two lines")
    void algorithmSummaryHeightIsStable() {
        context.setFortificationType(FortificationType.HERO);
        java.util.Set<Integer> heights = new java.util.HashSet<>();
        for (LineupAlgorithm algorithm : LineupAlgorithms.forAssignment(Lineup.TeamType.HERO)) {
            view.algorithmBox().setSelectedItem(algorithm);
            heights.add(view.algorithmSummary().getPreferredSize().height);
        }
        assertEquals(1, heights.size(), heights.toString());
        javax.swing.JLabel oneLine = new javax.swing.JLabel("x");
        assertTrue(heights.iterator().next() >= 2 * oneLine.getFontMetrics(oneLine.getFont()).getHeight());
    }

    @Test
    @DisplayName("The \"i\" button sits right of the combo box, always enabled, with a short tooltip")
    void algorithmInfoButton() {
        assertNotNull(view.algorithmInfoButton());
        assertSame(view.algorithmBox().getParent(), view.algorithmInfoButton().getParent());
        String text = LanguageService.displayName("conceptStage.algorithmInfo");
        assertEquals(text, view.algorithmInfoButton().getToolTipText());
        assertEquals(text, view.algorithmInfoButton().getAccessibleContext().getAccessibleName());

        actions.get(ActionId.RUN_ALGORITHM).setEnabled(false);
        assertTrue(view.algorithmInfoButton().isEnabled());
        actions.get(ActionId.RUN_ALGORITHM).setEnabled(true);
    }

    @Test
    @DisplayName("The explanation follows the chosen algorithm and the fortification type")
    void algorithmInfoFollowsChoice() {
        context.setFortificationType(FortificationType.HERO);
        for (LineupAlgorithm algorithm : LineupAlgorithms.forAssignment(Lineup.TeamType.HERO)) {
            view.algorithmBox().setSelectedItem(algorithm);
            assertSame(algorithm, view.algorithmInfo().algorithm());
            assertTrue(view.algorithmInfo().texts().contains(AlgorithmDescriptions.forAlgorithm(algorithm)));
        }
        context.setFortificationType(FortificationType.TITAN);
        assertSame(selection.get(Lineup.TeamType.TITAN), view.algorithmInfo().algorithm());
    }

    @Test
    @DisplayName("The combo box is disabled while \"Run assignment\" is")
    void algorithmBoxFollowsRunAssignment() {
        actions.get(ActionId.RUN_ALGORITHM).setEnabled(false);
        assertFalse(view.algorithmBox().isEnabled());
        actions.get(ActionId.RUN_ALGORITHM).setEnabled(true);
        assertTrue(view.algorithmBox().isEnabled());
    }

    private static java.util.List<LineupAlgorithm> items(javax.swing.JComboBox<LineupAlgorithm> box) {
        java.util.List<LineupAlgorithm> items = new java.util.ArrayList<>();
        for (int i = 0; i < box.getItemCount(); i++) {
            items.add(box.getItemAt(i));
        }
        return items;
    }
}
