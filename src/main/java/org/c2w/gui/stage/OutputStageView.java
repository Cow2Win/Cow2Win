package org.c2w.gui.stage;

import org.c2w.data.repository.LineupFiles;
import org.c2w.gui.ContextBar;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.c2w.gui.stage.InfoSections.*;

/**
 * The stage view "output": the change plan from the guild's Original lineup (the one in the
 * game) to a target as work area - by default the lineup open in the context bar, as it stands
 * in memory, recomputed by itself whenever it changes. The action list is the output menu plus
 * "copy plan"; the info panel shows the steps per kind, the affected players, the effect on the
 * total power and how old the Original is.
 */
public class OutputStageView extends StageView {

    private static final String KEY_COPY_PLAN = "changePlan.copyPlan";
    private static final String KEY_PLAN = "stageInfo.plan";
    private static final String KEY_PLAN_NONE = "stageInfo.plan.none";
    private static final String KEY_PLAN_STEPS = "stageInfo.plan.steps";
    private static final String KEY_PLAYERS = "stageInfo.players";
    private static final String KEY_PLAYERS_ENTRY = "stageInfo.players.entry";
    private static final String KEY_EFFECT = "stageInfo.effect";
    private static final String KEY_TOTAL_POWER = "stageInfo.comparison.totalPower";
    private static final String KEY_ORIGINAL = "stageInfo.original";

    private final AppContext appContext;
    private final ChangePlanPanel planPanel;

    private final JPanel planSection = sectionBody();
    private final JPanel playersSection = sectionBody();
    private final JPanel effectSection = sectionBody();
    private final JPanel originalSection = sectionBody();

    /** "Copy plan" in the action list - enabled only while a plan is shown. */
    private final Action copyPlanAction;

    public OutputStageView(AppContext appContext, MainActions actions) {
        super(actions);
        if (appContext == null) {
            throw new IllegalArgumentException("OutputStageView needs the AppContext");
        }
        this.appContext = appContext;
        this.planPanel = new ChangePlanPanel(appContext, true);
        this.copyPlanAction = new AbstractAction(LanguageService.displayName(KEY_COPY_PLAN)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                planPanel.copyPlan();
            }
        };
        copyPlanAction.putValue(Action.SHORT_DESCRIPTION, LanguageService.displayName(KEY_COPY_PLAN));
        build();

        planPanel.addPlanListener(this::refreshInfo);
        refreshInfo();
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                planPanel.refreshTargets();
                planPanel.refreshIfCurrentTarget();
                refreshInfo();
            }

            @Override
            public void lineupChanged() {
                planPanel.refreshTargets();
                planPanel.refreshIfCurrentTarget();
                // Saving the Original (team assignment) changes its date.
                refreshInfo();
            }

            @Override
            public void dirtyStateChanged() {
                planPanel.refreshIfCurrentTarget();
            }
        });
    }

    @Override
    public Stage stage() {
        return Stage.OUTPUT;
    }

    /** Shown again: the lineups may have changed in the meantime (e.g. in the concept view). */
    @Override
    public void onShown() {
        planPanel.refreshTargets();
        planPanel.refreshIfCurrentTarget();
        refreshInfo();
    }

    @Override
    protected JComponent createWorkArea() {
        JPanel area = translucentPanel(new BorderLayout());
        area.add(planPanel, BorderLayout.CENTER);
        return area;
    }

    /** "Copy plan" below the output menu's actions (the former "copy" button of the plan). */
    @Override
    protected void addActionListExtras(StageActionList actionList) {
        actionList.addExtraAction(copyPlanAction);
    }

    @Override
    protected JComponent createInfoPanel() {
        JPanel panel = InfoSections.infoPanel();
        addSection(panel, KEY_PLAN, planSection);
        addSection(panel, KEY_PLAYERS, playersSection);
        addSection(panel, KEY_EFFECT, effectSection);
        addSection(panel, KEY_ORIGINAL, originalSection);
        return panel;
    }

    /** Fills the info panel from the plan shown, and enables "copy plan" while there is one. */
    private void refreshInfo() {
        OutputInfoModel.OutputInfo info = OutputInfoModel.of(planPanel.result(), appContext.guild());
        copyPlanAction.setEnabled(planPanel.hasPlan());

        planSection.removeAll();
        playersSection.removeAll();
        effectSection.removeAll();
        if (!info.hasPlan()) {
            addLine(planSection, mutedLabel(LanguageService.displayName(KEY_PLAN_NONE)));
        } else {
            addLine(planSection, mutedLabel(LanguageService.displayName(KEY_PLAN_STEPS,
                    info.totalSteps(), info.removeSteps(), info.moveSteps(), info.placeSteps())));
            addLine(playersSection, new JLabel(String.valueOf(info.players().size())));
            for (OutputInfoModel.PlayerSteps player : info.players()) {
                addLine(playersSection, mutedLabel(LanguageService.displayName(KEY_PLAYERS_ENTRY,
                        player.name(), player.steps())));
            }
            addLine(effectSection, new JLabel(LanguageService.displayName(KEY_TOTAL_POWER)));
            addLine(effectSection, valueLine(number(info.totalPowerBefore()) + " " + arrow() + " " + number(info.totalPowerAfter()),
                    info.totalPowerDiff()));
        }

        originalSection.removeAll();
        Path guildFilePath = appContext.guildFilePath();
        Path originalFile = guildFilePath == null || guildFilePath.getParent() == null ? null
                : LineupFiles.originalPathFor(guildFilePath.getParent());
        addLine(originalSection, mutedLabel(ContextBar.originalStatusText(originalFile, JournalTexts.locale())));

        for (JPanel section : List.of(planSection, playersSection, effectSection, originalSection)) {
            relayout(section);
        }
    }

    // --- for tests ---

    ChangePlanPanel planPanel() {
        return planPanel;
    }

    Action copyPlanAction() {
        return copyPlanAction;
    }

    List<String> infoTexts() {
        List<String> texts = new ArrayList<>();
        for (JPanel section : List.of(planSection, playersSection, effectSection, originalSection)) {
            collectTexts(section, texts);
        }
        return texts;
    }
}
