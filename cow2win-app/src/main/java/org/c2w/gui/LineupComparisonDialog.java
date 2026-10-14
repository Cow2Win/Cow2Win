package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.LineupComparisonService;
import org.c2w.domain.LineupComparisonService.FortificationValues;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.compare.ComparableLineups;
import org.c2w.gui.compare.ComparableLineups.Choice;
import org.c2w.gui.compare.TornadoChart;
import org.c2w.gui.fort.FortificationPanel;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Compares the open lineup (left, with its unsaved changes) with any other lineup of the
 * workspace (right) - also one of another guild: power and buff per fortification of the
 * selected type, as two {@link TornadoChart}s. Each side is calculated with its own guild
 * (see {@link LineupComparisonService#compareFortifications}), since a lineup only references
 * members by id. Purely read-only, non-modal, and only one instance exists at a time (see
 * {@link #open}). The left side follows the open lineup and guild (see {@link #contextListener}).
 */
public class LineupComparisonDialog extends JDialog {

    private static final String KEY_TITLE = "lineupComparison.title";
    private static final String KEY_CURRENT = "lineupComparison.current";
    private static final String KEY_COMPARE_WITH = "lineupComparison.compareWith";
    private static final String KEY_OTHER_GUILD_HINT = "lineupComparison.otherGuildHint";
    private static final String KEY_UNSAVED = "lineupComparison.unsaved";
    private static final String KEY_CHART_POWER = "lineupComparison.chartPower";
    private static final String KEY_CHART_BUFF = "lineupComparison.chartBuff";
    private static final String KEY_NO_BUFF = "lineupComparison.noBuff";
    private static final String KEY_TOTAL = "lineupComparison.total";
    private static final String KEY_NO_OTHER_LINEUP = "lineupComparison.noOtherLineup";
    private static final String KEY_LOAD_GUILD_ERROR = "lineupComparison.loadGuildError";
    private static final String KEY_ROW_TOOLTIP = "lineupComparison.rowTooltip";
    private static final String KEY_LOAD_LINEUP_ERROR = "common.loadLineupError";
    private static final String KEY_LOAD_LINEUP_ERROR_TITLE = "common.loadLineupErrorTitle";
    private static final Map<FortificationType, String> TYPE_KEYS = Map.of(
            FortificationType.HERO, "fortificationMap.showHeroes",
            FortificationType.TITAN, "fortificationMap.showTitans");

    private static final String SEPARATOR = " · ";
    private static final int WIDTH = 900;
    private static final int HEIGHT = 720;

    /** The open dialog, or null - see {@link #open}. */
    private static LineupComparisonDialog instance;

    private final AppContext appContext;
    private final Map<FortificationType, JRadioButton> typeButtons = new EnumMap<>(FortificationType.class);
    private final JLabel currentLabel = new JLabel(" ");
    private final JComboBox<Choice> choiceCombo = new JComboBox<>();
    private final JLabel hintLabel = new JLabel(" ");
    private final TornadoChart powerChart = new TornadoChart(LanguageService.displayName(KEY_CHART_POWER),
            FortificationPanel::compactPower, LineupComparisonDialog::signedCompactPower);
    private final TornadoChart buffChart = new TornadoChart(LanguageService.displayName(KEY_CHART_BUFF),
            FortificationPanel::formatPercent, FortificationPanel::formatPercent);

    /** Guilds of other folders, loaded once per folder - see {@link #guildOf}. */
    private final Map<String, Guild> guildCache = new HashMap<>();

    private FortificationType type;
    /** Folder of the open guild when the choices were last built - see {@link #onGuildChanged}. */
    private String currentFolder;
    /** The selected choice and its loaded lineup - null if nothing is selected or it could not be loaded. */
    private Choice selectedChoice;
    private Lineup rightLineup;
    /** The lineup file that failed to load last, so a rebuild does not repeat the same error message. */
    private Path lastFailedPath;
    /** True while {@link #rebuildChoices} fills the combo box - its selection events are ignored then. */
    private boolean rebuilding;

    private final AppContext.Listener contextListener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            onGuildChanged();
        }

        @Override
        public void lineupChanged() {
            rebuildChoices();
            refreshLeft();
        }

        @Override
        public void dirtyStateChanged() {
            refreshLeft();
        }
    };

    /** Brings the open dialog to front, or creates and shows it if none is open. */
    public static LineupComparisonDialog open(Frame owner, AppContext appContext) {
        if (instance == null) {
            instance = new LineupComparisonDialog(owner, appContext);
        }
        instance.setVisible(true);
        instance.toFront();
        return instance;
    }

    /** The open dialog, if any. */
    public static LineupComparisonDialog openInstance() {
        return instance;
    }

    private LineupComparisonDialog(Frame owner, AppContext appContext) {
        super(owner, LanguageService.displayName(KEY_TITLE), false);
        if (appContext == null) {
            throw new IllegalArgumentException("LineupComparisonDialog needs an appContext");
        }
        this.appContext = appContext;
        this.type = appContext.fortificationType();
        this.currentFolder = currentGuildFolder();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        powerChart.setTotalLabel(LanguageService.displayName(KEY_TOTAL));
        buffChart.setDisabledText(LanguageService.displayName(KEY_NO_BUFF));
        choiceCombo.setRenderer(new ChoiceRenderer());
        choiceCombo.addActionListener(e -> {
            if (!rebuilding) {
                selectChoice((Choice) choiceCombo.getSelectedItem());
            }
        });
        hintLabel.setForeground(dimmedColor());

        JPanel content = new JPanel(new BorderLayout());
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildCharts(), BorderLayout.CENTER);
        setContentPane(content);

        rebuildChoices();
        refreshLeft();
        appContext.addListener(contextListener);

        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        appContext.removeListener(contextListener);
        if (instance == this) {
            instance = null;
        }
        super.dispose();
    }

    // --- layout ---

    private JComponent buildHeader() {
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        ButtonGroup group = new ButtonGroup();
        for (FortificationType fortificationType : FortificationType.values()) {
            JRadioButton button = new JRadioButton(LanguageService.displayName(TYPE_KEYS.get(fortificationType)));
            button.setSelected(fortificationType == type);
            button.addActionListener(e -> selectType(fortificationType));
            group.add(button);
            typePanel.add(button);
            typeButtons.put(fortificationType, button);
        }

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel currentCaption = new JLabel(LanguageService.displayName(KEY_CURRENT));
        currentCaption.setForeground(dimmedColor());
        currentLabel.setFont(currentLabel.getFont().deriveFont(Font.BOLD));
        left.add(currentCaption);
        left.add(Box.createVerticalStrut(4));
        left.add(currentLabel);

        JPanel comboRow = new JPanel(new BorderLayout(8, 0));
        comboRow.add(new JLabel(LanguageService.displayName(KEY_COMPARE_WITH)), BorderLayout.WEST);
        comboRow.add(choiceCombo, BorderLayout.CENTER);
        comboRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(comboRow);
        right.add(Box.createVerticalStrut(4));
        right.add(hintLabel);

        JPanel sides = new JPanel(new GridLayout(1, 2, 24, 0));
        sides.add(left);
        sides.add(right);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, dimmedColor()),
                BorderFactory.createEmptyBorder(8, 12, 10, 12)));
        header.add(typePanel, BorderLayout.NORTH);
        header.add(sides, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildCharts() {
        JPanel charts = new JPanel();
        charts.setLayout(new BoxLayout(charts, BoxLayout.Y_AXIS));
        charts.add(powerChart);
        charts.add(Box.createVerticalStrut(8));
        charts.add(buffChart);
        charts.add(Box.createVerticalGlue());
        JScrollPane scrollPane = new JScrollPane(charts);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    // --- events ---

    private void selectType(FortificationType fortificationType) {
        type = fortificationType;
        refreshCharts();
    }

    private void onGuildChanged() {
        String folder = currentGuildFolder();
        if (!Objects.equals(folder, currentFolder)) {
            // The guild that was open may have been edited and saved, the newly opened one is used in memory.
            guildCache.remove(currentFolder);
            guildCache.remove(folder);
            currentFolder = folder;
        }
        rebuildChoices();
        refreshLeft();
    }

    /**
     * Lists the comparable lineups again and keeps the selected one if it is still there,
     * otherwise selects the default one (see {@link ComparableLineups#reselect}).
     */
    private void rebuildChoices() {
        currentFolder = currentGuildFolder();
        GuildService guildService = new GuildService(appContext, RecentFiles.NONE);
        List<Choice> choices = ComparableLineups.list(Config.getWorkspaceDir(), guildService.listGuildFolderNames(),
                currentFolder, appContext.lineupFilePath());
        Choice selection = ComparableLineups.reselect(choices, selectedChoice, currentFolder);

        rebuilding = true;
        try {
            choiceCombo.removeAllItems();
            choices.forEach(choiceCombo::addItem);
            choiceCombo.setSelectedItem(selection);
            choiceCombo.setEnabled(!choices.isEmpty());
        } finally {
            rebuilding = false;
        }
        selectChoice(selection);
    }

    /** Loads the lineup of {@code choice} (null: nothing to compare with) and redraws everything. */
    private void selectChoice(Choice choice) {
        selectedChoice = choice;
        rightLineup = null;
        if (choice != null) {
            rightLineup = loadLineup(choice);
            if (rightLineup != null && guildOf(choice) == null) {
                rightLineup = null;
            }
            if (rightLineup != null) {
                lastFailedPath = null;
            }
        }
        updateHint();
        refreshCharts();
    }

    private Lineup loadLineup(Choice choice) {
        try {
            Lineup lineup = LineupRepository.load(choice.lineupPath());
            return lineup;
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not load the lineup to compare with: " + choice.lineupPath(), e);
            showLoadError(choice.lineupPath(), LanguageService.displayName(KEY_LOAD_LINEUP_ERROR), e);
            return null;
        }
    }

    /**
     * The guild {@code choice} belongs to: the open guild (in memory, with its unsaved changes)
     * for the current folder, otherwise the guild file of its folder, loaded once. Null if it
     * cannot be loaded (an error message is shown).
     */
    private Guild guildOf(Choice choice) {
        if (choice.guildFolder().equals(currentFolder)) {
            return appContext.guild();
        }
        Guild cached = guildCache.get(choice.guildFolder());
        if (cached != null) {
            return cached;
        }
        Path guildFile = choice.lineupPath().resolveSibling(GuildService.GUILD_FILE_NAME);
        try {
            Guild guild = GuildRepository.load(guildFile, appContext.catalog());
            guildCache.put(choice.guildFolder(), guild);
            return guild;
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not load the guild to compare with: " + guildFile, e);
            showLoadError(choice.lineupPath(), LanguageService.displayName(KEY_LOAD_GUILD_ERROR, choice.guildName()), e);
            return null;
        }
    }

    private void showLoadError(Path lineupPath, String message, Exception e) {
        if (lineupPath.equals(lastFailedPath)) {
            return;
        }
        lastFailedPath = lineupPath;
        JOptionPane.showMessageDialog(this, message + "\n" + e.getMessage(),
                LanguageService.displayName(KEY_LOAD_LINEUP_ERROR_TITLE), JOptionPane.ERROR_MESSAGE);
    }

    // --- refresh ---

    /** Header of the left side and both charts - after the open lineup, guild or dirty state changed. */
    private void refreshLeft() {
        String text = currentGuildName() + SEPARATOR + currentLineupName();
        boolean dirty = appContext.isLineupDirty();
        currentLabel.setText(dirty ? text + " *" : text);
        currentLabel.setToolTipText(dirty ? LanguageService.displayName(KEY_UNSAVED) : null);
        refreshCharts();
    }

    private void updateHint() {
        if (choiceCombo.getItemCount() == 0) {
            hintLabel.setText(LanguageService.displayName(KEY_NO_OTHER_LINEUP));
        } else if (selectedChoice != null && !selectedChoice.guildFolder().equals(currentFolder)) {
            hintLabel.setText(LanguageService.displayName(KEY_OTHER_GUILD_HINT));
        } else {
            hintLabel.setText(" ");
        }
    }

    private void refreshCharts() {
        Lineup left = appContext.lineup() != null ? appContext.lineup() : LineupRepository.createEmptyLineup();
        Guild leftGuild = appContext.guild() != null ? appContext.guild() : new Guild("-", "", List.of(), null);
        Guild rightGuild = selectedChoice != null && rightLineup != null ? guildOf(selectedChoice) : null;
        boolean rightAvailable = rightGuild != null;

        List<FortificationValues> values = LineupComparisonService.compareFortifications(
                left, leftGuild, rightAvailable ? rightLineup : null, rightGuild, type);

        String leftName = LanguageService.displayName(KEY_CURRENT);
        String rightName = selectedChoice == null ? "" : selectedChoice.guildName() + SEPARATOR + selectedChoice.lineupDisplayName();
        List<TornadoChart.Row> powerRows = new ArrayList<>();
        List<TornadoChart.Row> buffRows = new ArrayList<>();
        for (FortificationValues value : values) {
            String name = fortificationName(value.fortificationId());
            powerRows.add(new TornadoChart.Row(name, value.powerLeft(), value.powerRight(),
                    tooltip(name, leftName, rightName, value, value.powerLeft(), value.powerRight(),
                            FortificationPanel::compactPower, LineupComparisonDialog::signedCompactPower, rightAvailable),
                    true));
            String buffTooltip = value.hasBuff()
                    ? tooltip(name, leftName, rightName, value, value.buffPercentLeft(), value.buffPercentRight(),
                    FortificationPanel::formatPercent, FortificationPanel::formatPercent, rightAvailable)
                    : null;
            buffRows.add(new TornadoChart.Row(name, value.buffPercentLeft(), value.buffPercentRight(),
                    buffTooltip, value.hasBuff()));
        }
        Color leftColor = FortificationTypeStyle.color(type);
        powerChart.setLeftColor(leftColor);
        buffChart.setLeftColor(leftColor);
        powerChart.setRows(powerRows, rightAvailable);
        buffChart.setRows(buffRows, rightAvailable);
    }

    private static String tooltip(String name, String leftName, String rightName, FortificationValues value,
                                  int left, int right, IntFunction<String> format, IntFunction<String> diffFormat,
                                  boolean rightAvailable) {
        StringBuilder html = new StringBuilder("<html><b>").append(escape(name)).append("</b><br>")
                .append(escape(LanguageService.displayName(KEY_ROW_TOOLTIP, leftName, format.apply(left),
                        value.filledLeft(), value.capacity())));
        if (rightAvailable) {
            html.append("<br>").append(escape(LanguageService.displayName(KEY_ROW_TOOLTIP, rightName,
                    format.apply(right), value.filledRight(), value.capacity())));
            html.append("<br>Δ ").append(escape(diffFormat.apply(right - left)));
        }
        return html.append("</html>").toString();
    }

    // --- lookups ---

    private String currentGuildFolder() {
        Path guildFile = appContext.guildFilePath();
        Path guildDir = guildFile == null ? null : guildFile.getParent();
        return guildDir == null || guildDir.getFileName() == null ? null : guildDir.getFileName().toString();
    }

    private String currentGuildName() {
        Guild guild = appContext.guild();
        if (guild != null && guild.name() != null && !guild.name().isBlank()) {
            return guild.name();
        }
        return currentFolder == null ? "" : currentFolder;
    }

    private String currentLineupName() {
        Path lineupFile = appContext.lineupFilePath();
        return lineupFile == null || lineupFile.getFileName() == null ? ""
                : LineupFiles.displayName(lineupFile.getFileName().toString());
    }

    private static String fortificationName(String fortificationId) {
        return FortificationRepository.findById(fortificationId)
                .map(f -> LanguageService.displayName(f.id()))
                .orElse(fortificationId);
    }

    /** "+1,05 Mio", "-230.000" or "0" - compact like the fortification map, with sign. */
    static String signedCompactPower(int diff) {
        String formatted = FortificationPanel.compactPower(diff);
        return diff > 0 ? "+" + formatted : formatted;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Muted text color: the label color 40 % of the way to the background - readable on a dark and a light theme. */
    private static Color dimmedColor() {
        return mix(uiColor("Label.foreground", Color.DARK_GRAY), uiColor("Panel.background", Color.WHITE), 0.4);
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private static Color mix(Color from, Color to, double share) {
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * share),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * share),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * share));
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    // --- for tests ---

    /** The selectable lineups, in combo box order. */
    List<Choice> choices() {
        List<Choice> result = new ArrayList<>();
        for (int i = 0; i < choiceCombo.getItemCount(); i++) {
            result.add(choiceCombo.getItemAt(i));
        }
        return result;
    }

    Choice selectedChoice() {
        return selectedChoice;
    }

    void selectChoiceForTest(Choice choice) {
        choiceCombo.setSelectedItem(choice);
    }

    void selectTypeForTest(FortificationType fortificationType) {
        typeButtons.get(fortificationType).doClick();
    }

    String currentLabelText() {
        return currentLabel.getText();
    }

    String hintText() {
        return hintLabel.getText();
    }

    TornadoChart powerChart() {
        return powerChart;
    }

    TornadoChart buffChart() {
        return buffChart;
    }

    /** "Guild · Lineup", the guild name dimmed. */
    private static final class ChoiceRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Choice choice) {
                Color muted = mix(getForeground(), getBackground(), 0.4);
                setText("<html><font color='" + hex(muted) + "'>" + escape(choice.guildName()) + SEPARATOR
                        + "</font>" + escape(choice.lineupDisplayName()) + "</html>");
            } else if (value == null) {
                setText(" ");
            }
            return this;
        }
    }
}
