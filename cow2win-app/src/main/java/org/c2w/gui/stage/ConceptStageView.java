package org.c2w.gui.stage;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Lineup;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.gui.AlgorithmSelection;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.fort.FortificationAssignmentDialog;
import org.c2w.gui.fort.FortificationInfoPanel;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.fort.FortificationPanel;
import org.c2w.gui.fort.FortificationValueMode;
import org.c2w.gui.fort.HeroLineupSummaryPanel;
import org.c2w.gui.fort.TeamAvatars;
import org.c2w.gui.fort.TitanLineupSummaryPanel;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.c2w.gui.stage.InfoSections.*;

/**
 * The stage view "strategic concept": the fortification map as work area, on the left the
 * concept actions plus the "algorithm" combo box directly above "Run assignment" (the
 * algorithm it runs for the selected fortification type, see {@link AlgorithmSelection})
 * and the "fortification values" combo box at the end, and on the right the overview of the selected
 * fortification type, the comparison with the "Original" lineup and the details of the
 * fortification selected on the map, including its assigned teams. A double-click on a
 * fortification opens the {@link FortificationAssignmentDialog} to assign, move, swap and
 * remove its teams by hand.
 */
public class ConceptStageView extends StageView {

    private static final String KEY_OVERVIEW = "stageInfo.overview";
    private static final String KEY_COMPARISON = "stageInfo.comparison";
    private static final String KEY_IS_ORIGINAL = "stageInfo.comparison.isOriginal";
    private static final String KEY_NO_ORIGINAL = "stageInfo.comparison.noOriginal";
    private static final String KEY_TOTAL_POWER = "stageInfo.comparison.totalPower";
    private static final String KEY_TYPE_POWER = "stageInfo.comparison.typePower";
    private static final String KEY_CHANGES = "stageInfo.comparison.changes";
    private static final String KEY_FORTIFICATION = "stageInfo.fortification";
    private static final String KEY_FORTIFICATION_NONE = "stageInfo.fortification.none";
    private static final String KEY_NO_TEAMS = "stageInfo.fortification.noTeams";
    private static final String KEY_DOUBLE_CLICK_HINT = "stageInfo.fortification.doubleClickHint";
    private static final int TEAM_AVATAR_SIZE = 20;
    private static final int TEAM_NAME_WIDTH = 90;
    private static final String KEY_VALUE_MODE = "fortificationMap.valueMode";
    private static final String KEY_ALGORITHM = "conceptStage.algorithm";
    private static final String KEY_HEROES = "fortificationMap.showHeroes";
    private static final String KEY_TITANS = "fortificationMap.showTitans";

    private final AppContext appContext;
    private final MainActions actions;
    private final AlgorithmSelection algorithmSelection;
    private final FortificationMapPanel map;

    private final JPanel overviewSection = sectionBody();
    private final JPanel comparisonSection = sectionBody();
    private final JPanel fortificationSection = sectionBody();
    private JComboBox<FortificationValueMode> valueModeBox;
    private JComboBox<LineupAlgorithm> algorithmBox;

    /** True while {@link #fillAlgorithmBox} replaces the entries - its selection events are no user choice. */
    private boolean fillingAlgorithmBox;

    /** The fortification selected on the map, shown in {@link #fortificationSection}. */
    private Optional<Fortification> selectedFortification = Optional.empty();

    public ConceptStageView(AppContext appContext, MainActions actions, AlgorithmSelection algorithmSelection,
                            FortificationMapPanel map) {
        super(actions);
        if (appContext == null || algorithmSelection == null || map == null) {
            throw new IllegalArgumentException(
                    "ConceptStageView needs the AppContext, the algorithm selection and the fortification map");
        }
        this.appContext = appContext;
        this.actions = actions;
        this.algorithmSelection = algorithmSelection;
        this.map = map;
        build();

        refreshOverview();
        refreshComparison();
        showFortification(map.selectedFortification());
        map.addSelectionListener(this::showFortification);
        map.addOpenListener(this::openAssignmentDialog);
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                refreshAll();
            }

            @Override
            public void lineupChanged() {
                refreshAll();
            }

            @Override
            public void dirtyStateChanged() {
                refreshComparison();
            }

            @Override
            public void fortificationTypeChanged() {
                refreshOverview();
                refreshComparison();
                fillAlgorithmBox();
            }
        });
    }

    @Override
    public Stage stage() {
        return Stage.CONCEPT;
    }

    /** The fortification map, unchanged, in its transparent scroll pane. */
    @Override
    protected JComponent createWorkArea() {
        JScrollPane scrollPane = new JScrollPane(map);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        return scrollPane;
    }

    @Override
    protected JComponent createInfoPanel() {
        JPanel panel = InfoSections.infoPanel();
        addSection(panel, KEY_OVERVIEW, overviewSection);
        addSection(panel, KEY_COMPARISON, comparisonSection);
        addSection(panel, KEY_FORTIFICATION, fortificationSection);
        return panel;
    }

    /**
     * The "algorithm" combo box directly above "Run assignment" (see {@link #createAlgorithmBox}),
     * and the "fortification values" combo box below the actions: switches the map between power,
     * the change since loading/saving and the change against live. Always starts with power.
     */
    @Override
    protected void addActionListExtras(StageActionList actionList) {
        Action runAlgorithm = actions.get(ActionId.RUN_ALGORITHM);
        actionList.addExtraBefore(runAlgorithm, labeledPanel(KEY_ALGORITHM, createAlgorithmBox(runAlgorithm)));

        valueModeBox = new JComboBox<>(FortificationValueMode.values());
        valueModeBox.setSelectedItem(FortificationValueMode.POWER);
        map.setValueMode(FortificationValueMode.POWER);
        valueModeBox.setOpaque(false);
        valueModeBox.addActionListener(e -> map.setValueMode((FortificationValueMode) valueModeBox.getSelectedItem()));
        actionList.addExtra(labeledPanel(KEY_VALUE_MODE, valueModeBox));
    }

    /** A muted heading above {@code comboBox}, no higher than needed. */
    private static JPanel labeledPanel(String headingKey, JComboBox<?> comboBox) {
        JPanel panel = new JPanel(new BorderLayout(0, 4)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setOpaque(false);
        panel.add(mutedLabel(LanguageService.displayName(headingKey)), BorderLayout.NORTH);
        panel.add(comboBox, BorderLayout.CENTER);
        return panel;
    }

    // --- algorithm ---

    /**
     * The "algorithm" combo box: the assigning algorithms of the selected fortification type
     * ("Manual" is no choice here), showing that type's choice in {@link #algorithmSelection};
     * a choice goes there, so "Run assignment" (menu and action list) uses it. The tooltips
     * show the algorithms' descriptions. Disabled while {@code runAlgorithm} is, i.e. while
     * an algorithm is running.
     */
    private JComboBox<LineupAlgorithm> createAlgorithmBox(Action runAlgorithm) {
        algorithmBox = new JComboBox<>();
        algorithmBox.setOpaque(false);
        algorithmBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LineupAlgorithm algorithm) {
                    setText(algorithm.localizedName());
                    setToolTipText(descriptionTooltip(algorithm));
                }
                return this;
            }
        });
        algorithmBox.addActionListener(e -> {
            if (!fillingAlgorithmBox && algorithmBox.getSelectedItem() instanceof LineupAlgorithm algorithm) {
                algorithmSelection.set(algorithm);
                algorithmBox.setToolTipText(descriptionTooltip(algorithm));
            }
        });
        fillAlgorithmBox();
        algorithmBox.setEnabled(runAlgorithm.isEnabled());
        runAlgorithm.addPropertyChangeListener(e -> {
            if ("enabled".equals(e.getPropertyName())) {
                algorithmBox.setEnabled(runAlgorithm.isEnabled());
            }
        });
        return algorithmBox;
    }

    /** Puts the choices and the chosen algorithm of the selected fortification type into the combo box. */
    private void fillAlgorithmBox() {
        if (algorithmBox == null) {
            return;
        }
        Lineup.TeamType teamType = AlgorithmSelection.teamTypeOf(appContext.fortificationType());
        LineupAlgorithm selected = algorithmSelection.get(teamType);
        fillingAlgorithmBox = true;
        try {
            algorithmBox.setModel(new DefaultComboBoxModel<>(
                    algorithmSelection.choices(teamType).toArray(LineupAlgorithm[]::new)));
            algorithmBox.setSelectedItem(selected);
        } finally {
            fillingAlgorithmBox = false;
        }
        algorithmBox.setToolTipText(descriptionTooltip(selected));
    }

    /** The description of {@code algorithm}, wrapped - the descriptions are long. */
    private static String descriptionTooltip(LineupAlgorithm algorithm) {
        return "<html><div style='width:300px'>" + escape(AlgorithmDescriptions.forAlgorithm(algorithm))
                + "</div></html>";
    }

    private void refreshAll() {
        refreshOverview();
        refreshComparison();
        showFortification(selectedFortification);
    }

    // --- overview ---

    /** The lineup summary of the selected fortification type (moved here from the map). */
    private void refreshOverview() {
        overviewSection.removeAll();
        JComponent summary = appContext.fortificationType() == FortificationType.HERO
                ? new HeroLineupSummaryPanel(appContext.lineup(), appContext.guild())
                : new TitanLineupSummaryPanel(appContext.lineup(), appContext.guild());
        makeTransparent(summary);
        addLine(overviewSection, summary);
        relayout(overviewSection);
    }

    // --- comparison with the Original lineup ---

    private void refreshComparison() {
        comparisonSection.removeAll();
        ConceptInfoModel.Comparison comparison = ConceptInfoModel.compareWithOriginal(appContext.guildFilePath(),
                appContext.lineupFilePath(), appContext.lineup(), appContext.guild(), appContext.fortificationType());
        switch (comparison.state()) {
            case IS_ORIGINAL -> addLine(comparisonSection, mutedLabel(LanguageService.displayName(KEY_IS_ORIGINAL)));
            case NO_ORIGINAL -> addLine(comparisonSection, mutedLabel(LanguageService.displayName(KEY_NO_ORIGINAL)));
            case COMPARED -> {
                addLine(comparisonSection, new JLabel(LanguageService.displayName(KEY_TOTAL_POWER)));
                addLine(comparisonSection, valueLine(number(comparison.totalPowerBefore()) + " " + arrow() + " "
                        + number(comparison.totalPowerAfter()), comparison.totalPowerDiff()));
                addLine(comparisonSection, Box.createVerticalStrut(4));
                addLine(comparisonSection, valueLine(LanguageService.displayName(KEY_TYPE_POWER, typeName()),
                        comparison.typePowerDiff()));
                addLine(comparisonSection, Box.createVerticalStrut(4));
                addLine(comparisonSection, mutedLabel(LanguageService.displayName(KEY_CHANGES,
                        comparison.movedCount(), comparison.addedCount(), comparison.removedCount())));
            }
        }
        relayout(comparisonSection);
    }

    private String typeName() {
        return LanguageService.displayName(appContext.fortificationType() == FortificationType.HERO ? KEY_HEROES : KEY_TITANS);
    }

    // --- selected fortification ---

    /** Shows the details of {@code fortification}, or the hint to click one if empty. Package-visible for tests. */
    void showFortification(Optional<Fortification> fortification) {
        selectedFortification = fortification;
        fortificationSection.removeAll();
        if (fortification.isEmpty()) {
            addLine(fortificationSection, mutedLabel(LanguageService.displayName(KEY_FORTIFICATION_NONE)));
        } else {
            Fortification fort = fortification.get();
            addLine(fortificationSection, titleLabel(LanguageService.displayName(fort.id())));
            ConceptInfoModel.FortificationFacts facts = ConceptInfoModel.factsOf(fort, appContext.lineup(), appContext.guild());
            String factsText = facts.filledSlots() + "/" + facts.capacity() + "  ·  " + number(facts.totalPower())
                    + (facts.buffPercent() == null ? "" : "  ·  " + facts.buffPercent() + " %");
            addLine(fortificationSection, mutedLabel(factsText));
            addLine(fortificationSection, Box.createVerticalStrut(8));
            addAssignedTeams(fort);
            addLine(fortificationSection, Box.createVerticalStrut(4));
            addLine(fortificationSection, mutedLabel(LanguageService.displayName(KEY_DOUBLE_CLICK_HINT)));
            addLine(fortificationSection, Box.createVerticalStrut(8));
            FortificationInfoPanel infoPanel = new FortificationInfoPanel(fort,
                    appContext.catalog().heroes().findAll(), appContext.catalog().titans().findAll(), true);
            makeTransparent(infoPanel);
            addLine(fortificationSection, infoPanel);
        }
        relayout(fortificationSection);
    }

    /**
     * One line per team assigned to {@code fort}, strongest first: member name, avatars and
     * compact power - or a muted "no teams assigned yet".
     */
    private void addAssignedTeams(Fortification fort) {
        List<ConceptInfoModel.AssignedTeam> teams = ConceptInfoModel.teamsOf(fort, appContext.lineup(), appContext.guild());
        if (teams.isEmpty()) {
            addLine(fortificationSection, mutedLabel(LanguageService.displayName(KEY_NO_TEAMS)));
            return;
        }
        for (ConceptInfoModel.AssignedTeam team : teams) {
            JPanel line = new JPanel(new BorderLayout(6, 0)) {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
            };
            line.setOpaque(false);
            JLabel name = new JLabel(team.memberName());
            name.setPreferredSize(new Dimension(TEAM_NAME_WIDTH, name.getPreferredSize().height));
            line.add(name, BorderLayout.WEST);
            line.add(TeamAvatars.of(team.entry(), appContext.guild(), TEAM_AVATAR_SIZE), BorderLayout.CENTER);
            JLabel power = new JLabel(FortificationPanel.compactPower(team.power()), JLabel.RIGHT);
            power.setForeground(MUTED_COLOR);
            line.add(power, BorderLayout.EAST);
            addLine(fortificationSection, line);
        }
    }

    /** Opens the dialog "fortification assignment" for {@code fortification} (double-click on the map). */
    private void openAssignmentDialog(Fortification fortification) {
        FortificationAssignmentDialog.open(SwingUtilities.getWindowAncestor(this), appContext, fortification);
    }

    /** The texts shown in the "selected fortification" section - for tests. */
    List<String> fortificationSectionTexts() {
        List<String> texts = new ArrayList<>();
        collectTexts(fortificationSection, texts);
        return texts;
    }

    /** The "fortification values" combo box in the action list - for tests. */
    JComboBox<FortificationValueMode> valueModeBox() {
        return valueModeBox;
    }

    /** The "algorithm" combo box in the action list - for tests. */
    JComboBox<LineupAlgorithm> algorithmBox() {
        return algorithmBox;
    }

}
