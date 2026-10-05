package org.c2w.gui.stage;

import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.MainMenuBar;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link OutputStageView}: action list, "copy plan" and the plan following the open lineup - headless, never shown. */
class OutputStageViewTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private MainActions actions;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("The action list is the output menu plus \"copy plan\", which is disabled without a plan")
    void actionListAndCopyWithoutPlan() {
        OutputStageView view = new OutputStageView(context, actions);

        List<StageActionList.ActionRow> rows = view.actionList().rows();
        assertEquals(MainMenuBar.menuFor(Stage.OUTPUT).allActionIds(),
                rows.stream().map(row -> ((AppAction) row.action()).id()).toList());
        StageActionList.ActionRow last = (StageActionList.ActionRow)
                view.actionList().getComponents()[view.actionList().getComponentCount() - 1];
        assertSame(view.copyPlanAction(), last.action());
        assertEquals(LanguageService.displayName("changePlan.copyPlan"), last.getText());

        assertFalse(view.planPanel().hasPlan(), "no Original yet");
        assertEquals(ChangePlanModel.State.NO_ORIGINAL, view.planPanel().result().state());
        assertFalse(view.copyPlanAction().isEnabled());
        assertFalse(last.isEnabled());
    }

    @Test
    @DisplayName("With an Original, the open lineup's plan is there at once and follows unsaved changes; \"copy plan\" is enabled")
    void planFollowsTheOpenLineup() throws Exception {
        Path guildDir = context.guildFilePath().getParent();
        LineupRepository.save(new Lineup(context.guild().id(), context.guild().name(), "", LocalDateTime.now(), List.of()),
                LineupFiles.originalPathFor(guildDir));
        OutputStageView view = new OutputStageView(context, actions);

        assertTrue(view.planPanel().isCurrentTarget());
        assertTrue(view.planPanel().hasPlan());
        assertEquals(List.of(), view.planPanel().result().steps());
        assertTrue(view.copyPlanAction().isEnabled());

        // An unsaved change of the open lineup: one team placed.
        context.setLineup(new Lineup(context.lineup().guildId(), context.lineup().guildName(), "", LocalDateTime.now(),
                List.of(new Lineup.Entry("bastion", "someone", Lineup.TeamType.HERO, 0))));
        context.setLineupDirty(true);

        assertEquals(1, view.planPanel().result().steps().size());
        assertTrue(view.infoTexts().stream().anyMatch(text -> text.contains(
                LanguageService.displayName("stageInfo.plan.steps", 1, 0, 0, 1))), view.infoTexts().toString());
    }

    @Test
    @DisplayName("The Original itself open: the hint instead of a plan")
    void originalOpen() throws Exception {
        Path guildDir = context.guildFilePath().getParent();
        Path originalPath = LineupFiles.originalPathFor(guildDir);
        LineupRepository.save(new Lineup(context.guild().id(), context.guild().name(), "", LocalDateTime.now(), List.of()),
                originalPath);
        context.set(LineupRepository.load(originalPath), originalPath);

        OutputStageView view = new OutputStageView(context, actions);

        assertEquals(ChangePlanModel.State.ORIGINAL_IS_OPEN, view.planPanel().result().state());
        assertTrue(view.planPanel().messageText().contains(LanguageService.displayName("changePlan.originalIsOpen")));
        assertFalse(view.copyPlanAction().isEnabled());
    }
}
