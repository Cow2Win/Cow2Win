package org.c2w.gui.stage;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.fort.FortificationInfoPanel;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.fort.FortificationValueMode;
import org.c2w.gui.fort.HeroLineupSummaryPanel;
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
 * The stage view "strategic concept": the fortification map as work area, the concept actions
 * plus the "fortification values" combo box on the left, and on the right the overview of the selected
 * fortification type, the comparison with the "Original" lineup and the details of the
 * fortification selected on the map.
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
    private static final String KEY_VALUE_MODE = "fortificationMap.valueMode";
    private static final String KEY_HEROES = "fortificationMap.showHeroes";
    private static final String KEY_TITANS = "fortificationMap.showTitans";

    private final AppContext appContext;
    private final FortificationMapPanel map;

    private final JPanel overviewSection = sectionBody();
    private final JPanel comparisonSection = sectionBody();
    private final JPanel fortificationSection = sectionBody();
    private JComboBox<FortificationValueMode> valueModeBox;

    /** The fortification selected on the map, shown in {@link #fortificationSection}. */
    private Optional<Fortification> selectedFortification = Optional.empty();

    public ConceptStageView(AppContext appContext, MainActions actions, FortificationMapPanel map) {
        super(actions);
        if (appContext == null || map == null) {
            throw new IllegalArgumentException("ConceptStageView needs the AppContext and the fortification map");
        }
        this.appContext = appContext;
        this.map = map;
        build();

        refreshOverview();
        refreshComparison();
        showFortification(map.selectedFortification());
        map.addSelectionListener(this::showFortification);
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
     * The "fortification values" combo box below the actions: switches the map between power,
     * the change since loading/saving and the change against live. Always starts with power.
     */
    @Override
    protected void addActionListExtras(StageActionList actionList) {
        valueModeBox = new JComboBox<>(FortificationValueMode.values());
        valueModeBox.setSelectedItem(FortificationValueMode.POWER);
        map.setValueMode(FortificationValueMode.POWER);
        valueModeBox.setOpaque(false);
        valueModeBox.addActionListener(e -> map.setValueMode((FortificationValueMode) valueModeBox.getSelectedItem()));

        JPanel panel = new JPanel(new BorderLayout(0, 4)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setOpaque(false);
        panel.add(mutedLabel(LanguageService.displayName(KEY_VALUE_MODE)), BorderLayout.NORTH);
        panel.add(valueModeBox, BorderLayout.CENTER);
        actionList.addExtra(panel);
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
            FortificationInfoPanel infoPanel = new FortificationInfoPanel(fort,
                    appContext.catalog().heroes().findAll(), appContext.catalog().titans().findAll(), true);
            makeTransparent(infoPanel);
            addLine(fortificationSection, infoPanel);
        }
        relayout(fortificationSection);
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

}
