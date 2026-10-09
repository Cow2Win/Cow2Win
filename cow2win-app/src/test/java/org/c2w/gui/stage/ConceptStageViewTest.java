package org.c2w.gui.stage;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
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
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        map = new FortificationMapPanel(context);
        view = new ConceptStageView(context, actions, map);
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
}
