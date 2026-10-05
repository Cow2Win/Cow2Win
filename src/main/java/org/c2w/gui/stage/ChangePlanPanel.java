package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.ChangePlanCheckRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.ChangePlanChecks;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.data.repository.LineupFiles;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.LiveApplyService;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The change plan ("Umstell-Anleitung"): choose a target - the lineup open in the context bar
 * (optional, see the constructor), another saved lineup or a candidate of two algorithms -
 * generate, and work through the checklist from the guild's live lineup (file "Original") to
 * that target: grouped by fortification type, removals and additions, fortifications - one
 * checkbox per team ({@link ChangePlanOutline}). The checks are saved in the guild folder
 * ({@link ChangePlanCheckRepository}); {@link #applyToLive()} takes the checked entries into the
 * live lineup. Hints and errors appear right above the plan (details in the log), never as a
 * dialog. The logic lives in {@link ChangePlanModel}, the texts in {@link ChangePlanRenderer}.
 *
 * <p>Transparent, with the look and feel's label color for the plan text, so it fits the dark
 * stage views.
 */
public class ChangePlanPanel extends JPanel {

    private static final String KEY_TARGET_CURRENT = "changePlan.targetCurrent";
    private static final String KEY_TARGET_SAVED = "changePlan.targetSavedLineup";
    private static final String KEY_TARGET_ALGORITHM = "changePlan.targetAlgorithm";
    private static final String KEY_HERO_ALGORITHM_LABEL = "teamsOverview.heroAlgorithm";
    private static final String KEY_TITAN_ALGORITHM_LABEL = "teamsOverview.titanAlgorithm";
    private static final String KEY_ALGORITHM_LABEL = "teamsOverview.algorithm";
    private static final String KEY_GENERATE = "changePlan.generate";
    private static final String KEY_NO_ORIGINAL = "changePlan.noOriginal";
    private static final String KEY_SELECT_TARGET = "changePlan.selectTarget";
    private static final String KEY_DIFFERENT_GUILD = "changePlan.differentGuild";
    private static final String KEY_ORIGINAL_IS_OPEN = "changePlan.originalIsOpen";

    /** Indentation of a checklist level in pixels. */
    private static final int INDENT = 18;
    /** Client property of an entry's checkbox: its plain text. */
    private static final String ITEM_TEXT = "c2w.changePlan.itemText";

    private final AppContext appContext;

    /** Null if the open lineup is not offered as target (see the constructor). */
    private final JRadioButton currentRadio;
    private final JRadioButton savedLineupRadio = new JRadioButton(LanguageService.displayName(KEY_TARGET_SAVED));
    private final JRadioButton algorithmRadio = new JRadioButton(LanguageService.displayName(KEY_TARGET_ALGORITHM));
    private final JComboBox<String> targetLineupCombo = new JComboBox<>();
    /** One algorithm per fortification type - see {@link LineupAlgorithms#runBoth}. */
    private final JComboBox<LineupAlgorithm> heroAlgorithmCombo = new JComboBox<>();
    private final JComboBox<LineupAlgorithm> titanAlgorithmCombo = new JComboBox<>();
    private final JPanel savedLineupPanel = transparentFlow();
    private final JPanel algorithmPanel = transparentFlow();
    private final JButton generateButton = new JButton(LanguageService.displayName(KEY_GENERATE));

    private final JLabel messageLabel = new JLabel();
    /** The checklist of the plan: headings and one checkbox per entry. */
    private final JPanel checklist = new JPanel();
    private final JLabel summaryLabel = new JLabel();
    /** Per entry id its checkbox - for updating the look and for tests. */
    private final Map<String, JCheckBox> checkBoxes = new LinkedHashMap<>();

    private final List<Runnable> planListeners = new ArrayList<>();

    /** The result shown, null before the first generation. */
    private ChangePlanModel.Result result;
    /** The checklist of the shown plan, null without a plan. */
    private ChangePlanOutline outline;
    /** The checks of the shown plan (see {@link ChangePlanChecks}), saved in the guild folder after every change. */
    private ChangePlanChecks checks = ChangePlanChecks.EMPTY;

    /**
     * @param offerCurrentLineup true to offer "current lineup" (the one open in the context bar,
     *                           as in memory) as target and preselect it
     */
    public ChangePlanPanel(AppContext appContext, boolean offerCurrentLineup) {
        super(new BorderLayout());
        if (appContext == null) {
            throw new IllegalArgumentException("ChangePlanPanel needs an appContext");
        }
        this.appContext = appContext;
        setOpaque(false);

        ButtonGroup modeGroup = new ButtonGroup();
        JPanel modePanel = transparentFlow();
        currentRadio = offerCurrentLineup ? new JRadioButton(LanguageService.displayName(KEY_TARGET_CURRENT)) : null;
        for (JRadioButton radio : radios()) {
            radio.setOpaque(false);
            modeGroup.add(radio);
            modePanel.add(radio);
            radio.addActionListener(e -> onModeChanged());
        }
        (currentRadio != null ? currentRadio : savedLineupRadio).setSelected(true);

        populateTargetCombo();
        populateAlgorithmCombos();
        savedLineupPanel.add(targetLineupCombo);
        algorithmPanel.add(new JLabel(LanguageService.displayName(KEY_ALGORITHM_LABEL)));
        algorithmPanel.add(new JLabel(LanguageService.displayName(KEY_HERO_ALGORITHM_LABEL)));
        algorithmPanel.add(heroAlgorithmCombo);
        algorithmPanel.add(new JLabel(LanguageService.displayName(KEY_TITAN_ALGORITHM_LABEL)));
        algorithmPanel.add(titanAlgorithmCombo);

        JPanel controlPanel = new JPanel();
        controlPanel.setOpaque(false);
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        for (JPanel row : List.of(modePanel, savedLineupPanel, algorithmPanel)) {
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            controlPanel.add(row);
        }
        generateButton.addActionListener(e -> generate());

        JPanel topPanel = new JPanel(new BorderLayout(8, 4));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        topPanel.add(controlPanel, BorderLayout.CENTER);
        JPanel buttonHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonHolder.setOpaque(false);
        buttonHolder.add(generateButton);
        topPanel.add(buttonHolder, BorderLayout.EAST);

        messageLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        messageLabel.setVisible(false);

        checklist.setOpaque(false);
        checklist.setLayout(new BoxLayout(checklist, BoxLayout.Y_AXIS));
        checklist.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
        JPanel checklistHolder = new JPanel(new BorderLayout());
        checklistHolder.setOpaque(false);
        checklistHolder.add(checklist, BorderLayout.NORTH);
        JScrollPane outputScrollPane = new JScrollPane(checklistHolder);
        outputScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        outputScrollPane.setOpaque(false);
        outputScrollPane.getViewport().setOpaque(false);
        outputScrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel outputArea = new JPanel(new BorderLayout());
        outputArea.setOpaque(false);
        outputArea.add(messageLabel, BorderLayout.NORTH);
        outputArea.add(outputScrollPane, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(outputArea, BorderLayout.CENTER);
        onModeChanged();
    }

    private List<JRadioButton> radios() {
        return currentRadio == null ? List.of(savedLineupRadio, algorithmRadio)
                : List.of(currentRadio, savedLineupRadio, algorithmRadio);
    }

    private static JPanel transparentFlow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        return panel;
    }

    /** Shows the controls of the chosen target; the open lineup's plan is computed right away. */
    private void onModeChanged() {
        savedLineupPanel.setVisible(savedLineupRadio.isSelected());
        algorithmPanel.setVisible(algorithmRadio.isSelected());
        if (isCurrentTarget()) {
            generate();
        }
    }

    /** True while the target is the lineup open in the context bar - then the plan follows it automatically. */
    public boolean isCurrentTarget() {
        return currentRadio != null && currentRadio.isSelected();
    }

    /** Recomputes the plan if the target is the open lineup (see {@link #isCurrentTarget()}); other targets wait for "generate". */
    public void refreshIfCurrentTarget() {
        if (isCurrentTarget()) {
            generate();
        }
    }

    /** Lists the guild's saved lineups again (e.g. after a guild switch or a new lineup). */
    public void refreshTargets() {
        String selected = (String) targetLineupCombo.getSelectedItem();
        targetLineupCombo.removeAllItems();
        populateTargetCombo();
        if (selected != null) {
            targetLineupCombo.setSelectedItem(selected);
        }
    }

    /** Computes and shows the plan for the chosen target. */
    public void generate() {
        ChangePlanModel.Target target;
        String targetLabel;
        if (isCurrentTarget()) {
            target = new ChangePlanModel.CurrentLineup(appContext.lineup(), appContext.lineupFilePath());
            targetLabel = "current:" + (appContext.lineupFilePath() == null ? "" : appContext.lineupFilePath().getFileName());
        } else if (savedLineupRadio.isSelected()) {
            target = new ChangePlanModel.SavedLineup((String) targetLineupCombo.getSelectedItem());
            targetLabel = String.valueOf(targetLineupCombo.getSelectedItem());
        } else {
            target = new ChangePlanModel.Algorithms((LineupAlgorithm) heroAlgorithmCombo.getSelectedItem(),
                    (LineupAlgorithm) titanAlgorithmCombo.getSelectedItem());
            targetLabel = "algorithm";
        }
        show(ChangePlanModel.plan(appContext.guildFilePath(), appContext.guild(), target), targetLabel);
    }

    private void show(ChangePlanModel.Result newResult, String targetLabel) {
        result = newResult;
        checklist.removeAll();
        checkBoxes.clear();
        if (newResult.isOk()) {
            messageLabel.setVisible(false);
            outline = ChangePlanOutline.of(newResult.steps(), appContext.guild());
            loadChecks(targetLabel);
            buildChecklist();
        } else {
            showMessage(newResult);
            outline = null;
        }
        revalidate();
        repaint();
        List.copyOf(planListeners).forEach(Runnable::run);
    }

    // --- checklist ---

    /**
     * The checks of the guild for the new plan: those of entries that still exist for the same
     * target, none for another target - saved again if that changed anything.
     */
    private void loadChecks(String targetLabel) {
        Path guildDir = guildDir();
        if (guildDir == null) {
            checks = ChangePlanChecks.EMPTY;
            return;
        }
        ChangePlanChecks saved = ChangePlanCheckRepository.load(guildDir);
        checks = saved.forPlan(targetLabel, outline.items().stream().map(ChangePlanOutline.Item::id).toList());
        if (!checks.equals(saved)) {
            saveChecks();
        }
    }

    private void saveChecks() {
        Path guildDir = guildDir();
        if (guildDir == null) {
            return;
        }
        try {
            ChangePlanCheckRepository.save(guildDir, checks);
        } catch (IOException e) {
            Logger.logException("Could not save the change plan checks in " + guildDir, e);
        }
    }

    private Path guildDir() {
        Path guildFilePath = appContext.guildFilePath();
        return guildFilePath == null ? null : guildFilePath.getParent();
    }

    /** Heading and summary, then type → section → fortification → one checkbox per entry. */
    private void buildChecklist() {
        Guild guild = appContext.guild();
        addRow(styled(new JLabel(ChangePlanRenderer.heading()), Font.BOLD, 4f), 0, 2);
        if (outline.isEmpty()) {
            addRow(styled(new JLabel(ChangePlanRenderer.noChanges()), Font.BOLD, 0f), 0, 4);
            return;
        }
        summaryLabel.setBorder(null);
        addRow(summaryLabel, 0, 4);
        summaryLabel.setForeground(InfoSections.MUTED_COLOR);
        updateSummary();
        for (ChangePlanOutline.TypeGroup type : outline.types()) {
            addRow(styled(new JLabel(ChangePlanRenderer.typeHeading(type.type())), Font.BOLD, 3f), 0, 12);
            for (ChangePlanOutline.Section section : type.sections()) {
                addRow(styled(new JLabel(ChangePlanRenderer.sectionHeading(section)), Font.BOLD, 1f), INDENT, 8);
                for (ChangePlanOutline.FortificationGroup group : section.fortifications()) {
                    addRow(styled(new JLabel(ChangePlanRenderer.fortificationName(group.fortificationId())), Font.BOLD, 0f),
                            2 * INDENT, 4);
                    for (ChangePlanOutline.Item item : group.items()) {
                        JCheckBox checkBox = new JCheckBox();
                        checkBox.setOpaque(false);
                        checkBox.putClientProperty(ITEM_TEXT, ChangePlanRenderer.itemText(item, guild));
                        checkBox.setSelected(checks.isChecked(item.id()));
                        checkBox.addActionListener(e -> setChecked(item.id(), checkBox.isSelected()));
                        styleCheckBox(checkBox);
                        checkBoxes.put(item.id(), checkBox);
                        addRow(checkBox, 3 * INDENT, 0);
                    }
                }
            }
        }
    }

    private void addRow(JComponent component, int indent, int top) {
        component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(top, indent, 0, 0),
                component.getBorder()));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklist.add(component);
    }

    /** {@code label} in {@code style}, {@code larger} points larger - "->" instead of "→" if the font lacks the arrow. */
    private static JLabel styled(JLabel label, int style, float larger) {
        label.setFont(label.getFont().deriveFont(style, label.getFont().getSize2D() + larger));
        if (!label.getFont().canDisplay('→')) {
            label.setText(label.getText().replace("→", "->"));
        }
        return label;
    }

    /** A checked entry is muted and struck through. */
    private static void styleCheckBox(JCheckBox checkBox) {
        String text = ((String) checkBox.getClientProperty(ITEM_TEXT))
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (checkBox.isSelected()) {
            checkBox.setText("<html><s>" + text + "</s></html>");
            checkBox.setForeground(InfoSections.MUTED_COLOR);
        } else {
            checkBox.setText("<html>" + text + "</html>");
            checkBox.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    private void updateSummary() {
        summaryLabel.setText(ChangePlanRenderer.checkedSummary(outline.items().size(), checks.checkedIds().size()));
    }

    /** Checks or unchecks one entry - only that, nothing else changes - and saves the checks right away. */
    public void setChecked(String itemId, boolean checked) {
        if (outline == null || outline.item(itemId).isEmpty()) {
            return;
        }
        checks = checks.with(itemId, checked);
        saveChecks();
        JCheckBox checkBox = checkBoxes.get(itemId);
        if (checkBox != null) {
            checkBox.setSelected(checked);
            styleCheckBox(checkBox);
        }
        updateSummary();
        List.copyOf(planListeners).forEach(Runnable::run);
    }

    /** The checklist of the shown plan, null without a plan. */
    public ChangePlanOutline outline() {
        return outline;
    }

    /** The checks of the shown plan. */
    public ChangePlanChecks checks() {
        return checks;
    }

    /**
     * "Apply to live": takes the checked entries into the live lineup (see {@link LiveApplyService}),
     * archiving the old one first, and shows the plan again. Writes nothing unless the result is
     * {@link LiveApplyService.Outcome#APPLIED}.
     *
     * @return the result; null without a plan
     * @throws IOException if the live lineup could not be read, archived or saved - it is then unchanged
     */
    public LiveApplyService.Result applyToLive() throws IOException {
        Path guildDir = guildDir();
        if (!hasPlan() || outline == null || guildDir == null) {
            return null;
        }
        Path liveFile = LineupFiles.originalPathFor(guildDir);
        LocalDateTime now = LocalDateTime.now();
        LiveApplyService.Result applied = LiveApplyService.apply(LineupRepository.load(liveFile), outline,
                checks.checkedIds(), now);
        if (applied.outcome() != LiveApplyService.Outcome.APPLIED) {
            return applied;
        }
        LiveApplyService.archive(guildDir, now);
        LiveApplyService.saveLive(guildDir, applied.live());
        checks = checks.without(applied.appliedIds());
        saveChecks();
        if (LineupFiles.isOriginal(appContext.lineupFilePath())) {
            // Should not happen in the output stage - but an open live lineup must show the new state.
            appContext.set(applied.live(), appContext.lineupFilePath());
        }
        generate();
        return applied;
    }

    /** A hint (muted) or an error (red, with the exception's message) above the empty plan. */
    private void showMessage(ChangePlanModel.Result failed) {
        String key = switch (failed.state()) {
            case NO_ORIGINAL -> KEY_NO_ORIGINAL;
            case DIFFERENT_GUILD -> KEY_DIFFERENT_GUILD;
            case NO_TARGET -> KEY_SELECT_TARGET;
            case ORIGINAL_IS_OPEN -> KEY_ORIGINAL_IS_OPEN;
            case LOAD_ERROR -> failed.messageKey();
            case OK -> throw new IllegalStateException("no message for a plan");
        };
        String text = LanguageService.displayName(key)
                + (failed.state() == ChangePlanModel.State.LOAD_ERROR && failed.detail() != null ? " " + failed.detail() : "");
        boolean error = failed.state() == ChangePlanModel.State.LOAD_ERROR;
        messageLabel.setText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>");
        messageLabel.setForeground(error ? IconLoader.RED : InfoSections.MUTED_COLOR);
        messageLabel.setVisible(true);
    }

    /** The result shown, null before the first generation. */
    public ChangePlanModel.Result result() {
        return result;
    }

    /** True if a plan is shown (then {@link #copyPlan()} has something to copy). */
    public boolean hasPlan() {
        return result != null && result.isOk();
    }

    /** The shown plan as plain text with "[x]" / "[ ]", "" without a plan. */
    public String plainTextPlan() {
        return outline == null ? "" : ChangePlanRenderer.plainText(outline, checks.checkedIds(), appContext.guild());
    }

    /** Copies the shown plan as plain text to the clipboard - nothing without a plan. */
    public void copyPlan() {
        if (hasPlan()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plainTextPlan()), null);
        }
    }

    /** The checkbox of the entry {@code itemId}, null if none is shown - for tests. */
    JCheckBox checkBox(String itemId) {
        return checkBoxes.get(itemId);
    }

    /** The summary "{n} entries, {k} checked" - for tests. */
    String summaryText() {
        return summaryLabel.getText();
    }

    /** {@code listener} runs after every (re)generation of the plan. */
    public void addPlanListener(Runnable listener) {
        planListeners.add(listener);
    }

    /** The text of the hint or error shown instead of a plan, null if none is shown - for tests. */
    String messageText() {
        return messageLabel.isVisible() ? messageLabel.getText() : null;
    }

    /**
     * Fills both algorithm combo boxes with their fortification type's algorithms and preselects
     * the defaults configured in the Settings dialog.
     */
    private void populateAlgorithmCombos() {
        populateAlgorithmCombo(heroAlgorithmCombo, Lineup.TeamType.HERO, Config.getDefaultHeroAlgorithm());
        populateAlgorithmCombo(titanAlgorithmCombo, Lineup.TeamType.TITAN, Config.getDefaultTitanAlgorithm());
    }

    private static void populateAlgorithmCombo(JComboBox<LineupAlgorithm> combo, Lineup.TeamType teamType,
                                               String configuredAlgorithm) {
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LineupAlgorithm algorithm) {
                    setText(algorithm.localizedName());
                }
                return this;
            }
        });
        for (LineupAlgorithm algorithm : LineupAlgorithms.forType(teamType)) {
            combo.addItem(algorithm);
        }
        combo.setSelectedItem(LineupAlgorithms.findOrDefault(teamType, configuredAlgorithm));
    }

    /** The guild's saved lineups except the Original, suffix-stripped for display; the open one preselected. */
    private void populateTargetCombo() {
        targetLineupCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String fileName) {
                    setText(LineupFiles.displayName(fileName));
                }
                return this;
            }
        });
        List<String> fileNames = ChangePlanModel.targetLineupFileNames(appContext.guildFilePath());
        fileNames.forEach(targetLineupCombo::addItem);

        String currentFileName = appContext.lineupFilePath() == null
                ? null : appContext.lineupFilePath().getFileName().toString();
        if (currentFileName != null && fileNames.contains(currentFileName)) {
            targetLineupCombo.setSelectedItem(currentFileName);
        }
    }
}
