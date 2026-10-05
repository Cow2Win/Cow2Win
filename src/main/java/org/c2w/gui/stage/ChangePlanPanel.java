package org.c2w.gui.stage;

import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * The change plan ("Umstell-Anleitung"): choose a target - the lineup open in the context bar
 * (optional, see the constructor), another saved lineup or a candidate of two algorithms -
 * generate, and read the steps from the guild's Original lineup to that target. Hints and
 * errors appear right above the plan (details in the log), never as a dialog. The logic lives
 * in {@link ChangePlanModel}, the text in {@link ChangePlanRenderer}.
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
    private final JEditorPane outputPane = new JEditorPane();

    private final List<Runnable> planListeners = new ArrayList<>();

    /** The result shown, null before the first generation. */
    private ChangePlanModel.Result result;
    /** Plain-text form of the shown plan, "" without a plan. */
    private String plainTextPlan = "";

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

        outputPane.setEditable(false);
        outputPane.setContentType("text/html");
        // Plan text in the look and feel's label color on the transparent (dark) background.
        outputPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        outputPane.setOpaque(false);
        Color foreground = UIManager.getColor("Label.foreground");
        if (foreground != null) {
            outputPane.setForeground(foreground);
        }
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            outputPane.setFont(font);
        }
        JScrollPane outputScrollPane = new JScrollPane(outputPane);
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
        if (isCurrentTarget()) {
            target = new ChangePlanModel.CurrentLineup(appContext.lineup(), appContext.lineupFilePath());
        } else if (savedLineupRadio.isSelected()) {
            target = new ChangePlanModel.SavedLineup((String) targetLineupCombo.getSelectedItem());
        } else {
            target = new ChangePlanModel.Algorithms((LineupAlgorithm) heroAlgorithmCombo.getSelectedItem(),
                    (LineupAlgorithm) titanAlgorithmCombo.getSelectedItem());
        }
        show(ChangePlanModel.plan(appContext.guildFilePath(), appContext.guild(), target));
    }

    private void show(ChangePlanModel.Result newResult) {
        result = newResult;
        if (newResult.isOk()) {
            ChangePlanRenderer.RenderedPlan rendered = ChangePlanRenderer.render(newResult.steps(), appContext.guild());
            messageLabel.setVisible(false);
            outputPane.setText(rendered.html());
            outputPane.setCaretPosition(0);
            plainTextPlan = rendered.plainText();
        } else {
            showMessage(newResult);
            outputPane.setText("");
            plainTextPlan = "";
        }
        revalidate();
        repaint();
        List.copyOf(planListeners).forEach(Runnable::run);
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

    /** The shown plan as plain text, "" without a plan. */
    public String plainTextPlan() {
        return plainTextPlan;
    }

    /** Copies the shown plan as plain text to the clipboard - nothing without a plan. */
    public void copyPlan() {
        if (hasPlan()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plainTextPlan), null);
        }
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
                if (value instanceof String fileName && fileName.endsWith(LineupFiles.SUFFIX)) {
                    setText(fileName.substring(0, fileName.length() - LineupFiles.SUFFIX.length()));
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
