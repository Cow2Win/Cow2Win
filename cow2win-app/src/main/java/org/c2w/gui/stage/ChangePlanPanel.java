package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.ChangePlanCheckRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.ChangePlanChecks;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.data.repository.LineupFiles;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildLog;
import org.c2w.service.LiveApplyService;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
 * that target: grouped by removals and additions, fortifications - one checkbox per team
 * ({@link ChangePlanOutline}), the entries of a fortification side by side in columns (as many as
 * fit the width, read row by row, see {@link ColumnGridLayout}), named by the member only. The plan
 * is computed for both fortification types, but only the {@linkplain AppContext#fortificationType()
 * selected type} is shown, summarized, copied ({@link #copyPlan()}) and applied; switching the type
 * only rebuilds the checklist. The checks (of both types) are saved in the guild folder
 * ({@link ChangePlanCheckRepository}); {@link #applyToLive()} takes the checked entries of the
 * selected type into the live lineup - the button for it ("apply heroes / titans to live") sits
 * next to "generate" and runs the handler set by {@link #setApplyToLiveHandler}. Hints and errors appear right above the plan (details in the log),
 * never as a dialog. The logic lives in {@link ChangePlanModel}, the texts in {@link ChangePlanRenderer}.
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
    private static final String KEY_APPLY_DISABLED_TOOLTIP = "changePlan.applyToLive.disabledTooltip";

    /** Indentation of a checklist level in pixels. */
    private static final int INDENT = 18;
    /** Space between two columns of entries in pixels. */
    private static final int COLUMN_GAP = 16;
    /** Client property of an entry's checkbox: its plain text. */
    private static final String ITEM_TEXT = "c2w.changePlan.itemText";
    /** Client property of an entry's checkbox: the {@link FortificationType} it belongs to. */
    private static final String ITEM_TYPE = "c2w.changePlan.itemType";

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
    /** "Apply heroes / titans to live" - the main action of the stage, see {@link #updateApplyButton()}. */
    private final JButton applyButton = new JButton();
    /** Runs on {@link #applyButton} - the action "apply to live" (see {@link #setApplyToLiveHandler}). */
    private Runnable applyToLiveHandler = () -> { };

    private final JLabel messageLabel = new JLabel();
    /** The checklist of the plan: headings and one checkbox per entry. */
    private final JPanel checklist = new JPanel();
    /** The scroll pane's viewport - its width decides how many columns of entries fit. */
    private JViewport checklistViewport;
    /** The panels holding the entries of one fortification each, in columns. */
    private final List<JPanel> itemGrids = new ArrayList<>();
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
    /** The guild folder the shown plan was computed for, null without a plan. */
    private Path planGuildDir;
    /** The target of the shown plan as shown to the user - named in the guild log on "apply to live". */
    private String targetDisplayName = "";

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
        JPanel buttonHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonHolder.setOpaque(false);
        buttonHolder.add(generateButton);
        stylePrimary(applyButton);
        applyButton.addActionListener(e -> applyToLiveHandler.run());
        buttonHolder.add(applyButton);
        topPanel.add(buttonHolder, BorderLayout.EAST);

        messageLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        messageLabel.setVisible(false);

        checklist.setOpaque(false);
        checklist.setLayout(new BoxLayout(checklist, BoxLayout.Y_AXIS));
        checklist.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
        JPanel checklistHolder = new WidthTrackingPanel();
        checklistHolder.add(checklist, BorderLayout.NORTH);
        JScrollPane outputScrollPane = new JScrollPane(checklistHolder);
        outputScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        checklistViewport = outputScrollPane.getViewport();
        // A wider or narrower window: as many columns of entries as fit now.
        checklistViewport.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateColumns();
            }
        });
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
        updateApplyButton();
        // The checklist shows the selected fortification type only - switching it rebuilds the
        // checklist, the plan is not computed again.
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void fortificationTypeChanged() {
                showSelectedType();
            }
        });
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
            targetDisplayName = appContext.lineupFilePath() == null ? ""
                    : LineupFiles.displayName(appContext.lineupFilePath().getFileName().toString());
        } else if (savedLineupRadio.isSelected()) {
            target = new ChangePlanModel.SavedLineup((String) targetLineupCombo.getSelectedItem());
            targetLabel = String.valueOf(targetLineupCombo.getSelectedItem());
            targetDisplayName = LineupFiles.displayName((String) targetLineupCombo.getSelectedItem());
        } else {
            LineupAlgorithm heroAlgorithm = (LineupAlgorithm) heroAlgorithmCombo.getSelectedItem();
            LineupAlgorithm titanAlgorithm = (LineupAlgorithm) titanAlgorithmCombo.getSelectedItem();
            target = new ChangePlanModel.Algorithms(heroAlgorithm, titanAlgorithm);
            targetLabel = "algorithm";
            targetDisplayName = (heroAlgorithm == null ? "" : heroAlgorithm.localizedName()) + " / "
                    + (titanAlgorithm == null ? "" : titanAlgorithm.localizedName());
        }
        show(ChangePlanModel.plan(appContext.guildFilePath(), appContext.guild(), target), targetLabel);
    }

    private void show(ChangePlanModel.Result newResult, String targetLabel) {
        result = newResult;
        checklist.removeAll();
        checkBoxes.clear();
        itemGrids.clear();
        if (newResult.isOk()) {
            messageLabel.setVisible(false);
            outline = ChangePlanOutline.of(newResult.steps(), appContext.guild());
            planGuildDir = guildDir();
            loadChecks(targetLabel);
            buildChecklist();
        } else {
            showMessage(newResult);
            outline = null;
            planGuildDir = null;
        }
        updateApplyButton();
        revalidate();
        repaint();
        List.copyOf(planListeners).forEach(Runnable::run);
    }

    /** Shows the plan of the newly selected fortification type - built again, not computed again. */
    private void showSelectedType() {
        if (outline != null) {
            checklist.removeAll();
            checkBoxes.clear();
            itemGrids.clear();
            buildChecklist();
        }
        updateApplyButton();
        revalidate();
        repaint();
        List.copyOf(planListeners).forEach(Runnable::run);
    }

    /** The fortification type shown, copied and applied - the one selected in the context bar. */
    public FortificationType fortificationType() {
        return appContext.fortificationType();
    }

    /** The checklist of the shown plan restricted to the {@linkplain #fortificationType() selected type}, null without a plan. */
    public ChangePlanOutline shownOutline() {
        return outline == null ? null : outline.ofType(fortificationType());
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

    /**
     * Heading and summary, then - for the selected fortification type only - type → section →
     * fortification → its entries side by side in columns. Without changes for the selected
     * type: a text saying so, plus the number of entries of the other type if it has some.
     */
    private void buildChecklist() {
        Guild guild = appContext.guild();
        FortificationType selected = fortificationType();
        ChangePlanOutline shown = outline.ofType(selected);
        addRow(styled(new JLabel(ChangePlanRenderer.heading()), Font.BOLD, 4f), 0, 2);
        if (outline.isEmpty()) {
            addRow(styled(new JLabel(ChangePlanRenderer.noChanges()), Font.BOLD, 0f), 0, 4);
            return;
        }
        if (shown.isEmpty()) {
            addRow(styled(new JLabel(ChangePlanRenderer.noChangesForType(selected)), Font.BOLD, 0f), 0, 4);
            FortificationType other = ChangePlanRenderer.otherType(selected);
            int otherEntries = outline.ofType(other).items().size();
            if (otherEntries > 0) {
                addRow(colored(new JLabel(ChangePlanRenderer.otherTypeEntries(other, otherEntries)), AppColors.TEXT_MUTED),
                        0, 4);
            }
            return;
        }
        summaryLabel.setBorder(null);
        addRow(summaryLabel, 0, 4);
        summaryLabel.setForeground(AppColors.TEXT_MUTED);
        updateSummary();
        for (ChangePlanOutline.TypeGroup type : shown.types()) {
            Color typeColor = itemColor(type.type(), false);
            addRow(colored(styled(new JLabel(ChangePlanRenderer.typeHeading(type.type())), Font.BOLD, 3f), typeColor), 0, 12);
            for (ChangePlanOutline.Section section : type.sections()) {
                addRow(colored(styled(new JLabel(ChangePlanRenderer.sectionHeading(section)), Font.BOLD, 1f), typeColor),
                        INDENT, 8);
                for (ChangePlanOutline.FortificationGroup group : section.fortifications()) {
                    addRow(colored(styled(new JLabel(ChangePlanRenderer.fortificationName(group.fortificationId())),
                            Font.BOLD, 0f), typeColor), 2 * INDENT, 4);
                    JPanel grid = new JPanel(new ColumnGridLayout(COLUMN_GAP));
                    grid.setOpaque(false);
                    for (ChangePlanOutline.Item item : group.items()) {
                        JCheckBox checkBox = new JCheckBox();
                        checkBox.setOpaque(false);
                        checkBox.putClientProperty(ITEM_TEXT, ChangePlanRenderer.itemText(item, guild));
                        checkBox.putClientProperty(ITEM_TYPE, type.type());
                        checkBox.setSelected(checks.isChecked(item.id()));
                        checkBox.addActionListener(e -> setChecked(item.id(), checkBox.isSelected()));
                        styleCheckBox(checkBox);
                        checkBoxes.put(item.id(), checkBox);
                        grid.add(checkBox);
                    }
                    itemGrids.add(grid);
                    addRow(grid, 3 * INDENT, 0);
                }
            }
        }
        // One column width for the whole checklist, so the columns of all fortifications line up.
        int cellWidth = checkBoxes.values().stream().mapToInt(box -> box.getPreferredSize().width).max().orElse(0);
        for (JPanel grid : itemGrids) {
            ((ColumnGridLayout) grid.getLayout()).setCellWidth(cellWidth);
        }
        updateColumns();
    }

    /** Sets the number of columns of every fortification's entries to what fits the viewport's width now (package-visible for tests). */
    void updateColumns() {
        if (itemGrids.isEmpty()) {
            return;
        }
        int available = checklistViewport == null ? 0 : checklistViewport.getWidth();
        Insets checklistInsets = checklist.getInsets();
        available -= checklistInsets.left + checklistInsets.right + 3 * INDENT;
        boolean changed = false;
        for (JPanel grid : itemGrids) {
            ColumnGridLayout layout = (ColumnGridLayout) grid.getLayout();
            int columns = ColumnGridLayout.columns(available, layout.cellWidth(), layout.gap());
            if (columns != layout.columns()) {
                layout.setColumns(columns);
                grid.revalidate();
                changed = true;
            }
        }
        if (changed) {
            checklist.revalidate();
            checklist.repaint();
        }
    }

    /** The texts of the checklist's labels (headings, summary, hints) - for tests. */
    List<String> checklistTexts() {
        List<String> texts = new ArrayList<>();
        for (Component component : checklist.getComponents()) {
            if (component instanceof JLabel label) {
                texts.add(label.getText());
            }
        }
        return texts;
    }

    /** The number of columns the entries are shown in now - for tests. */
    int columnCount() {
        return itemGrids.isEmpty() ? 0 : ((ColumnGridLayout) itemGrids.get(0).getLayout()).columns();
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

    private static JLabel colored(JLabel label, Color color) {
        label.setForeground(color);
        return label;
    }

    /** A checked entry is muted and struck through; its color comes from {@link #itemColor}. */
    private static void styleCheckBox(JCheckBox checkBox) {
        String text = ((String) checkBox.getClientProperty(ITEM_TEXT))
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        FortificationType type = (FortificationType) checkBox.getClientProperty(ITEM_TYPE);
        checkBox.setText(checkBox.isSelected() ? "<html><s>" + text + "</s></html>" : "<html>" + text + "</html>");
        checkBox.setForeground(itemColor(type, checkBox.isSelected()));
    }

    /**
     * The color of a checklist entry (and, unchecked, of its headings): titans in their
     * {@link FortificationTypeStyle#color titan color}, heroes in the normal label color; a
     * checked entry in the muted variant of that ({@link AppColors#TEXT_MUTED} for heroes).
     * Heroes deliberately do not use {@code FortificationTypeStyle}'s white, so they keep
     * whatever text color the look and feel provides.
     */
    static Color itemColor(FortificationType type, boolean checked) {
        if (type == FortificationType.TITAN) {
            Color titan = FortificationTypeStyle.color(type);
            return checked ? AppColors.tint(titan, AppColors.TEXT_MUTED.getAlpha()) : titan;
        }
        return checked ? AppColors.TEXT_MUTED : UIManager.getColor("Label.foreground");
    }

    /** "{n} entries, {k} checked" of the selected fortification type. */
    private void updateSummary() {
        ChangePlanOutline shown = shownOutline();
        summaryLabel.setText(ChangePlanRenderer.checkedSummary(shown.items().size(), checkedCount(shown)));
    }

    /** How many entries of {@code part} are checked. */
    private int checkedCount(ChangePlanOutline part) {
        return (int) part.items().stream().filter(item -> checks.isChecked(item.id())).count();
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
        updateApplyButton();
        List.copyOf(planListeners).forEach(Runnable::run);
    }

    /** The checklist of the shown plan, null without a plan. */
    public ChangePlanOutline outline() {
        return outline;
    }

    /** The checks of the shown plan (of both fortification types). */
    public ChangePlanChecks checks() {
        return checks;
    }

    /**
     * "Apply to live": takes the checked entries of the {@linkplain #fortificationType() selected
     * fortification type} into the live lineup (see {@link LiveApplyService}), archiving the old one
     * first, notes the type as applied ({@link ChangePlanChecks#withApplied}) and shows the plan
     * again. Checked entries of the other type stay checked and are not applied. Writes nothing unless the result is
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
        LiveApplyService.Result applied = LiveApplyService.apply(LineupRepository.load(liveFile), shownOutline(),
                checks.checkedIds(), now);
        if (applied.outcome() != LiveApplyService.Outcome.APPLIED) {
            return applied;
        }
        LiveApplyService.archive(guildDir, now);
        LiveApplyService.saveLive(guildDir, applied.live());
        GuildLog.event(guildDir, "guildLog.appliedToLive", applied.applied().size(), targetDisplayName);
        checks = checks.without(applied.appliedIds()).withApplied(fortificationType());
        saveChecks();
        if (LineupFiles.isOriginal(appContext.lineupFilePath())) {
            // Should not happen in the output stage - but an open live lineup must show the new state.
            appContext.set(applied.live(), appContext.lineupFilePath());
        }
        generate();
        return applied;
    }

    /** True if "apply to live" has something to do: an entry of the selected fortification type is checked. */
    public boolean canApplyToLive() {
        ChangePlanOutline shown = hasPlan() ? shownOutline() : null;
        return shown != null && checkedCount(shown) > 0;
    }

    /** {@code handler} runs when the button "apply heroes / titans to live" is pressed - the action "apply to live". */
    public void setApplyToLiveHandler(Runnable handler) {
        applyToLiveHandler = handler == null ? () -> { } : handler;
    }

    /**
     * The fortification type that is "not live yet" for the shown plan (see
     * {@link ChangePlanChecks#missingLiveType}), null if none - or if the plan was computed for
     * another guild than the open one.
     */
    public FortificationType missingLiveType() {
        if (outline == null || planGuildDir == null || !planGuildDir.equals(guildDir())) {
            return null;
        }
        return ChangePlanChecks.missingLiveType(outline, checks.appliedTypes());
    }

    /**
     * Label ("apply heroes / titans to live"), enabled state (an entry of the selected type is
     * checked, see {@link #canApplyToLive()}) and tooltip of the apply button.
     */
    private void updateApplyButton() {
        applyButton.setText(ChangePlanRenderer.applyToLive(fortificationType()));
        boolean enabled = canApplyToLive();
        applyButton.setEnabled(enabled);
        applyButton.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        applyButton.setToolTipText(enabled ? null : LanguageService.displayName(KEY_APPLY_DISABLED_TOOLTIP));
    }

    /** The button "apply heroes / titans to live" - for tests. */
    JButton applyButton() {
        return applyButton;
    }

    /**
     * The look of the stage's main action: filled in {@link AppColors#ACCENT} with bold white
     * text; disabled on {@link AppColors#SURFACE} with {@link AppColors#TEXT_MUTED} text. Drawn by
     * itself (basic button UI), since the look and feel would paint its own background.
     */
    private static void stylePrimary(JButton button) {
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean enabled = b.isEnabled();
                    boolean hover = enabled && b.getModel().isRollover();
                    boolean pressed = enabled && b.getModel().isPressed();
                    g2.setColor(!enabled ? AppColors.SURFACE
                            : pressed ? AppColors.mix(AppColors.ACCENT, Color.BLACK, 0.15)
                            : hover ? AppColors.mix(AppColors.ACCENT, Color.WHITE, 0.15) : AppColors.ACCENT);
                    g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
                    g2.setColor(enabled ? AppColors.ACCENT : AppColors.LINE);
                    g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
                } finally {
                    g2.dispose();
                }
                super.paint(g, c);
            }

            @Override
            protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(b.getFont());
                    g2.setColor(b.isEnabled() ? Color.WHITE : AppColors.TEXT_MUTED);
                    g2.drawString(text, textRect.x, textRect.y + g2.getFontMetrics().getAscent());
                } finally {
                    g2.dispose();
                }
            }
        });
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setForeground(Color.WHITE);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
    }

    /**
     * The panel inside the checklist's scroll pane: always as wide as the viewport (so there is
     * never a horizontal scroll bar and the entries wrap into columns instead), scrolling only
     * vertically.
     */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
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
        messageLabel.setForeground(error ? AppColors.ERROR_TEXT : AppColors.TEXT_MUTED);
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

    /** The shown plan of the selected fortification type as plain text with "[x]" / "[ ]", "" without a plan. */
    public String plainTextPlan() {
        return outline == null ? ""
                : ChangePlanRenderer.plainText(outline, checks.checkedIds(), appContext.guild(), fortificationType());
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
