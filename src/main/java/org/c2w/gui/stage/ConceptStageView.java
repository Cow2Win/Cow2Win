package org.c2w.gui.stage;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationInfoPanel;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.fort.HeroLineupSummaryPanel;
import org.c2w.gui.fort.TitanLineupSummaryPanel;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The stage view "strategic concept": the fortification map as work area, the concept actions
 * plus the "changes" toggle on the left, and on the right the overview of the selected
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
    private static final String KEY_SHOW_CHANGES = "fortificationMap.showChanges";
    private static final String KEY_HEROES = "fortificationMap.showHeroes";
    private static final String KEY_TITANS = "fortificationMap.showTitans";

    private static final Color HEADING_COLOR = new Color(255, 255, 255, 150);
    private static final Color MUTED_COLOR = new Color(255, 255, 255, 130);

    private final AppContext appContext;
    private final FortificationMapPanel map;

    private final JPanel overviewSection = sectionBody();
    private final JPanel comparisonSection = sectionBody();
    private final JPanel fortificationSection = sectionBody();
    private JCheckBox changesToggle;

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
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        addSection(panel, KEY_OVERVIEW, overviewSection);
        addSection(panel, KEY_COMPARISON, comparisonSection);
        addSection(panel, KEY_FORTIFICATION, fortificationSection);
        return panel;
    }

    /** The "changes" toggle below the actions: switches the map between total power and the change since loading/saving. */
    @Override
    protected void addActionListExtras(StageActionList actionList) {
        changesToggle = new JCheckBox(LanguageService.displayName(KEY_SHOW_CHANGES), map.isShowChanges());
        changesToggle.setOpaque(false);
        changesToggle.addActionListener(e -> map.setShowChanges(changesToggle.isSelected()));
        actionList.addExtra(changesToggle);
    }

    private static void addSection(JPanel panel, String headingKey, JPanel body) {
        JLabel heading = new JLabel(LanguageService.displayName(headingKey).toUpperCase(JournalTexts.locale()));
        heading.setForeground(HEADING_COLOR);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() - 1f));
        heading.setBorder(BorderFactory.createEmptyBorder(panel.getComponentCount() == 0 ? 0 : 14, 0, 6, 0));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(body);
    }

    private static JPanel sectionBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        return body;
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
        revalidateSection(overviewSection);
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
                addLine(comparisonSection, valueLine(format(comparison.totalPowerBefore()) + " → "
                        + format(comparison.totalPowerAfter()), comparison.totalPowerDiff()));
                addLine(comparisonSection, Box.createVerticalStrut(4));
                addLine(comparisonSection, valueLine(LanguageService.displayName(KEY_TYPE_POWER, typeName()),
                        comparison.typePowerDiff()));
                addLine(comparisonSection, Box.createVerticalStrut(4));
                addLine(comparisonSection, mutedLabel(LanguageService.displayName(KEY_CHANGES,
                        comparison.movedCount(), comparison.addedCount(), comparison.removedCount())));
            }
        }
        revalidateSection(comparisonSection);
    }

    private String typeName() {
        return LanguageService.displayName(appContext.fortificationType() == FortificationType.HERO ? KEY_HEROES : KEY_TITANS);
    }

    /** {@code text} followed by the colored difference: green for a gain, red for a loss. */
    private static JComponent valueLine(String text, int diff) {
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.setOpaque(false);
        line.add(new JLabel(text + "   "));
        JLabel diffLabel = new JLabel((diff > 0 ? "+" : "") + format(diff));
        diffLabel.setForeground(diff > 0 ? IconLoader.GREEN : diff < 0 ? IconLoader.RED : MUTED_COLOR);
        line.add(diffLabel);
        return line;
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
            JLabel name = new JLabel(LanguageService.displayName(fort.id()));
            name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 1f));
            addLine(fortificationSection, name);
            ConceptInfoModel.FortificationFacts facts = ConceptInfoModel.factsOf(fort, appContext.lineup(), appContext.guild());
            String factsText = facts.filledSlots() + "/" + facts.capacity() + "  ·  " + format(facts.totalPower())
                    + (facts.buffPercent() == null ? "" : "  ·  " + facts.buffPercent() + " %");
            addLine(fortificationSection, mutedLabel(factsText));
            addLine(fortificationSection, Box.createVerticalStrut(8));
            FortificationInfoPanel infoPanel = new FortificationInfoPanel(fort,
                    appContext.catalog().heroes().findAll(), appContext.catalog().titans().findAll(), true);
            makeTransparent(infoPanel);
            addLine(fortificationSection, infoPanel);
        }
        revalidateSection(fortificationSection);
    }

    /** The texts shown in the "selected fortification" section - for tests. */
    List<String> fortificationSectionTexts() {
        List<String> texts = new ArrayList<>();
        collectTexts(fortificationSection, texts);
        return texts;
    }

    /** The "changes" toggle in the action list - for tests. */
    JCheckBox changesToggle() {
        return changesToggle;
    }

    // --- helpers ---

    private static void collectTexts(Container container, List<String> texts) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                texts.add(label.getText());
            }
            if (child instanceof Container nested) {
                collectTexts(nested, texts);
            }
        }
    }

    private static void addLine(JPanel section, Component line) {
        if (line instanceof JComponent component) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        section.add(line);
    }

    /** A muted label that wraps its text within the narrow info panel. */
    private static JLabel mutedLabel(String text) {
        JLabel label = new JLabel("<html><body style='width:" + (INFO_PANEL_WIDTH - 50) + "px'>"
                + escape(text) + "</body></html>");
        label.setForeground(MUTED_COLOR);
        return label;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String format(long value) {
        return NumberFormat.getIntegerInstance(JournalTexts.locale()).format(value);
    }

    private static void revalidateSection(JPanel section) {
        section.revalidate();
        section.repaint();
    }
}
