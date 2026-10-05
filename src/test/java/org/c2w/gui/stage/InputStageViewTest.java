package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.MainMenuBar;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.GuiUtils;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link InputStageView}: action list, member table and member section - headless, never shown. */
class InputStageViewTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private InputStageView view;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        Guild testGuild = MemberOverviewModelTest.testGuild();
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), testGuild.members(),
                context.guild().gameGuildId()));
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        view = new InputStageView(context, actions);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("The action list holds the entries of the input menu, in order, and nothing else")
    void actionList() {
        assertEquals(MainMenuBar.menuFor(Stage.INPUT).allActionIds(),
                view.actionList().rows().stream().map(row -> ((AppAction) row.action()).id()).toList());
    }

    @Test
    @DisplayName("The table lists every member, sorted by total power descending")
    void table() {
        assertEquals(3, view.table().getRowCount());
        assertEquals("Anna", view.table().getValueAt(0, MemberTableModel.COLUMN_MEMBER));
        assertEquals(4_000_000, view.table().getValueAt(0, MemberTableModel.COLUMN_TOTAL_POWER));
        assertEquals("Bert", view.table().getValueAt(1, MemberTableModel.COLUMN_MEMBER));
    }

    @Test
    @DisplayName("No selection shows the hint; selecting a member shows its name and its teams")
    void memberSection() {
        String hint = LanguageService.displayName("stageInfo.member.none");
        assertTrue(view.memberSectionTexts().stream().anyMatch(text -> text.contains(hint)));

        view.selectMember("anna");
        List<String> texts = view.memberSectionTexts();
        assertTrue(texts.contains("Anna"), texts.toString());
        assertTrue(texts.stream().anyMatch(text -> text.startsWith(LanguageService.displayName("stageInfo.member.team", 2))),
                texts.toString());
        assertTrue(texts.stream().noneMatch(text -> text.contains(hint)));

        view.selectMember("carl");
        assertTrue(view.memberSectionTexts().stream().anyMatch(text -> text.contains(
                LanguageService.displayName("stageInfo.member.noTeams", LanguageService.displayName("fortificationMap.showHeroes")))));
    }

    @Test
    @DisplayName("Switching the fortification type shows the titan teams in table and info panel, keeping the selection")
    void fortificationTypeSwitch() {
        view.selectMember("anna");
        context.setFortificationType(FortificationType.TITAN);

        assertEquals("Anna", view.table().getValueAt(0, MemberTableModel.COLUMN_MEMBER));
        assertEquals(500_000, view.table().getValueAt(0, MemberTableModel.COLUMN_TOTAL_POWER));
        assertEquals(1, view.table().getValueAt(0, MemberTableModel.COLUMN_TEAMS));
        List<String> texts = view.memberSectionTexts();
        assertTrue(texts.contains("Anna"), texts.toString());
        assertTrue(texts.stream().anyMatch(text -> text.contains(GuiUtils.NUMBER_FORMAT.format(500_000))), texts.toString());
        assertTrue(texts.stream().noneMatch(text -> text.startsWith(LanguageService.displayName("stageInfo.member.team", 2))),
                "only one titan team: " + texts);
    }
}
