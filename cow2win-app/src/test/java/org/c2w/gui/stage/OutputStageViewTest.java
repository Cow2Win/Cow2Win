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

    /** Live: m1 and m2 in the bastion; the open lineup (unsaved): m2 in the bastion, m3 in the citadel. */
    private Path liveAndTarget() throws Exception {
        Path guildDir = context.guildFilePath().getParent();
        LineupRepository.save(new Lineup(context.guild().id(), context.guild().name(), "", LocalDateTime.now(), List.of(
                        new Lineup.Entry("bastion", "m1", Lineup.TeamType.HERO, 0),
                        new Lineup.Entry("bastion", "m2", Lineup.TeamType.HERO, 0))),
                LineupFiles.originalPathFor(guildDir));
        context.setLineup(new Lineup(context.lineup().guildId(), context.lineup().guildName(), "", LocalDateTime.now(), List.of(
                new Lineup.Entry("bastion", "m2", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("citadel", "m3", Lineup.TeamType.HERO, 0))));
        context.setLineupDirty(true);
        return guildDir;
    }

    private static String idOf(ChangePlanPanel panel, String memberId) {
        return panel.outline().items().stream().filter(item -> item.teamKey().teamMemberId().equals(memberId))
                .findFirst().orElseThrow().id();
    }

    @Test
    @DisplayName("A check only changes the checks; they are saved and still there in a new view and after a rebuild")
    void checks() throws Exception {
        Path guildDir = liveAndTarget();
        String liveBefore = java.nio.file.Files.readString(LineupFiles.originalPathFor(guildDir));
        OutputStageView view = new OutputStageView(context, actions);
        ChangePlanPanel panel = view.planPanel();
        assertEquals(2, panel.outline().items().size());
        assertTrue(actions.get(ActionId.APPLY_TO_LIVE).isEnabled());

        String removeM1 = idOf(panel, "m1");
        panel.checkBox(removeM1).doClick();

        assertTrue(panel.checks().isChecked(removeM1));
        assertEquals(LanguageService.displayName("changePlan.checkedSummary", 2, 1), panel.summaryText());
        assertTrue(view.infoTexts().stream().anyMatch(text -> text.contains(LanguageService.displayName("stageInfo.plan.checked", 1, 2))),
                view.infoTexts().toString());
        assertEquals(liveBefore, java.nio.file.Files.readString(LineupFiles.originalPathFor(guildDir)), "live unchanged");
        assertTrue(context.isLineupDirty(), "the open lineup is untouched");
        assertTrue(panel.plainTextPlan().contains("[x]") && panel.plainTextPlan().contains("[ ]"), panel.plainTextPlan());

        OutputStageView restarted = new OutputStageView(context, actions);
        assertTrue(restarted.planPanel().checkBox(removeM1).isSelected());

        // Another unsaved change: the plan is rebuilt, the check of the still existing entry stays.
        context.setLineup(new Lineup(context.lineup().guildId(), context.lineup().guildName(), "", LocalDateTime.now(), List.of(
                new Lineup.Entry("bastion", "m2", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("citadel", "m3", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("citadel", "m4", Lineup.TeamType.HERO, 0))));
        assertEquals(3, panel.outline().items().size());
        assertTrue(panel.checks().isChecked(removeM1));
    }

    @Test
    @DisplayName("Apply to live: only checked entries go into live, the old live is archived, the rest stays in the plan")
    void applyToLive() throws Exception {
        Path guildDir = liveAndTarget();
        OutputStageView view = new OutputStageView(context, actions);
        ChangePlanPanel panel = view.planPanel();

        org.c2w.service.LiveApplyService.Result nothing = panel.applyToLive();
        assertEquals(org.c2w.service.LiveApplyService.Outcome.NOTHING_CHECKED, nothing.outcome());
        assertEquals(LanguageService.displayName("applyToLive.nothingChecked"), OutputStageView.applyMessage(nothing));
        assertFalse(java.nio.file.Files.exists(guildDir.resolve("live-history")));

        panel.setChecked(idOf(panel, "m3"), true);
        org.c2w.service.LiveApplyService.Result applied = panel.applyToLive();

        assertEquals(org.c2w.service.LiveApplyService.Outcome.APPLIED, applied.outcome());
        assertEquals(LanguageService.displayName("applyToLive.done", 1), OutputStageView.applyMessage(applied));
        Lineup live = LineupRepository.load(LineupFiles.originalPathFor(guildDir));
        assertTrue(live.entries().contains(new Lineup.Entry("citadel", "m3", Lineup.TeamType.HERO, 0)));
        assertTrue(live.entries().contains(new Lineup.Entry("bastion", "m1", Lineup.TeamType.HERO, 0)), "not checked");
        try (var history = java.nio.file.Files.list(guildDir.resolve("live-history"))) {
            assertEquals(1, history.count());
        }
        assertEquals(1, panel.outline().items().size(), "only the removal of m1 is left");
        assertEquals(java.util.Set.of(), panel.checks().checkedIds());
        assertTrue(context.isLineupDirty(), "the target lineup stays open and unsaved");
    }
}
