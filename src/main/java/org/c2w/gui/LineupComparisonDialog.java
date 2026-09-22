package org.c2w.gui;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.hero.HeroValueOverviewDialog;
import org.c2w.gui.titan.TitanValueOverviewDialog;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.LineupComparisonService;
import org.c2w.util.LineupComparisonService.FortificationDiff;
import org.c2w.util.LineupComparisonService.LineupComparison;
import org.c2w.util.LineupComparisonService.TeamDiff;
import org.c2w.util.Logger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Compares two {@link Lineup}s of the currently open guild - either two
 * saved ".lineup" files, or the currently open lineup against a candidate a
 * {@link LineupAlgorithm} produces on the fly. Purely read-only/preview: the
 * algorithm candidate is never saved and never applied to {@link AppContext}
 * from here - see {@link #onCompare()}. Added 2026-09-13 per the user's
 * request (see cow2win-verbesserungsvorschlaege.md, section 3,
 * "Lineup-Vergleich"). The actual diff computation lives in
 * {@link LineupComparisonService}; this class is purely the Swing wiring
 * around it, following the same non-modal, disposable-on-close pattern as
 * {@link HeroValueOverviewDialog}/{@link TitanValueOverviewDialog}.
 */
public class LineupComparisonDialog extends JDialog {

    /** Dialog title - hardcoded, not localized (matches {@link HeroValueOverviewDialog}/{@link TitanValueOverviewDialog}/{@link ReportViewerDialog}). */
    private static final String BASE_TITLE = "Cow2 - Lineup Comparison";

    private static final String KEY_MODE_SAVED_LINEUPS = "lineupComparison.modeSavedLineups";
    private static final String KEY_MODE_ALGORITHM = "lineupComparison.modeCurrentVsAlgorithm";
    private static final String KEY_LINEUP_BEFORE = "lineupComparison.lineupBefore";
    private static final String KEY_LINEUP_AFTER = "lineupComparison.lineupAfter";
    private static final String KEY_ALGORITHM_LABEL = "teamsOverview.algorithm";
    private static final String KEY_COMPARE = "lineupComparison.compare";
    private static final String KEY_ONLY_CHANGES = "lineupComparison.onlyChanges";
    private static final String KEY_TAB_TEAMS = "lineupComparison.tabTeams";
    private static final String KEY_TAB_FORTIFICATIONS = "lineupComparison.tabFortifications";
    private static final String KEY_SUMMARY_TOTAL_POWER = "lineupComparison.summaryTotalPower";
    private static final String KEY_SUMMARY_HERO_POWER = "lineupSummary.heroPower";
    private static final String KEY_SUMMARY_TITAN_POWER = "lineupSummary.titanPower";
    private static final String KEY_SUMMARY_CHANGES = "lineupComparison.summaryChanges";
    private static final String KEY_NONE = "common.none";

    /**
     * Glob pattern for lineup files - same as {@code ToolbarPanel}'s own
     * private constant, deliberately duplicated here rather than exposing
     * it (see class Javadoc / {@link HeroValueOverviewDialog}/{@link TitanValueOverviewDialog}'s own
     * "redundant copy" convention). {@link LineupRepository#findAll()} is
     * NOT used for this - it always returns an empty map (its backing
     * loader is a stub), so listing a guild's lineup files has to go
     * through the file system directly, exactly like {@code ToolbarPanel}
     * already does for its own lineup combo box.
     */
    private static final String LINEUP_FILE_GLOB = "*.lineup";
    private static final String LINEUP_FILE_SUFFIX = ".lineup";

    private final AppContext appContext;
    private final Guild guild;

    private final JRadioButton savedLineupsRadio = new JRadioButton(LanguageService.displayName(KEY_MODE_SAVED_LINEUPS));
    private final JRadioButton algorithmRadio = new JRadioButton(LanguageService.displayName(KEY_MODE_ALGORITHM));

    private final JComboBox<String> lineupBeforeCombo = new JComboBox<>();
    private final JComboBox<String> lineupAfterCombo = new JComboBox<>();
    private final JComboBox<LineupAlgorithm> algorithmCombo = new JComboBox<>();

    private final JPanel savedLineupsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel algorithmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

    private final JCheckBox onlyChangesCheckbox = new JCheckBox(LanguageService.displayName(KEY_ONLY_CHANGES), true);

    private final TeamDiffTableModel teamModel = new TeamDiffTableModel();
    private final FortificationDiffTableModel fortificationModel = new FortificationDiffTableModel();
    private final JTable teamTable = new JTable(teamModel);
    private final JTable fortificationTable = new JTable(fortificationModel);

    private final JLabel summaryLabel = new JLabel(" ");

    /**
     * Result of the last {@link #onCompare()} run - null before the first
     * comparison. {@link #onlyChangesCheckbox} re-filters this into the
     * tables (see {@link #refreshTables()}) without recomputing it.
     */
    private LineupComparison currentResult;

    public LineupComparisonDialog(Frame owner, AppContext appContext) {
        super(owner, BASE_TITLE, false);
        if (appContext == null) {
            throw new IllegalArgumentException("LineupComparisonDialog needs an appContext");
        }
        this.appContext = appContext;
        this.guild = appContext.guild();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(savedLineupsRadio);
        modeGroup.add(algorithmRadio);
        algorithmRadio.setSelected(true);
        savedLineupsRadio.addActionListener(e -> updateModeVisibility());
        algorithmRadio.addActionListener(e -> updateModeVisibility());

        populateLineupCombos();
        populateAlgorithmCombo();

        savedLineupsPanel.add(new JLabel(LanguageService.displayName(KEY_LINEUP_BEFORE)));
        savedLineupsPanel.add(lineupBeforeCombo);
        savedLineupsPanel.add(new JLabel(LanguageService.displayName(KEY_LINEUP_AFTER)));
        savedLineupsPanel.add(lineupAfterCombo);

        algorithmPanel.add(new JLabel(LanguageService.displayName(KEY_ALGORITHM_LABEL)));
        algorithmPanel.add(algorithmCombo);

        JButton compareButton = new JButton(LanguageService.displayName(KEY_COMPARE));
        compareButton.addActionListener(e -> onCompare());

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        modePanel.add(savedLineupsRadio);
        modePanel.add(algorithmRadio);

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        savedLineupsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        algorithmPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlPanel.add(modePanel);
        controlPanel.add(savedLineupsPanel);
        controlPanel.add(algorithmPanel);

        JPanel topPanel = new JPanel(new BorderLayout(8, 4));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        topPanel.add(controlPanel, BorderLayout.CENTER);
        topPanel.add(compareButton, BorderLayout.EAST);

        configureTable(teamTable);
        configureTable(fortificationTable);
        teamTable.getColumnModel().getColumn(0).setCellRenderer(new StatusCellRenderer());
        teamTable.getColumnModel().getColumn(6).setCellRenderer(new DiffCellRenderer());
        teamTable.getColumnModel().getColumn(7).setCellRenderer(new DiffCellRenderer());
        fortificationTable.getColumnModel().getColumn(3).setCellRenderer(new DiffCellRenderer());
        fortificationTable.getColumnModel().getColumn(5).setCellRenderer(new DiffCellRenderer());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(LanguageService.displayName(KEY_TAB_TEAMS), new JScrollPane(teamTable));
        tabs.addTab(LanguageService.displayName(KEY_TAB_FORTIFICATIONS), new JScrollPane(fortificationTable));

        onlyChangesCheckbox.addActionListener(e -> refreshTables());

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        bottomPanel.add(summaryLabel, BorderLayout.CENTER);
        bottomPanel.add(onlyChangesCheckbox, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        updateModeVisibility();
        setSize(950, 560);
        setLocationRelativeTo(owner);
    }

    private void updateModeVisibility() {
        boolean savedMode = savedLineupsRadio.isSelected();
        savedLineupsPanel.setVisible(savedMode);
        algorithmPanel.setVisible(!savedMode);
    }

    private void populateAlgorithmCombo() {
        algorithmCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LineupAlgorithm algorithm) {
                    setText(algorithm.displayName());
                }
                return this;
            }
        });
        for (LineupAlgorithm algorithm : LineupAlgorithms.ALL) {
            algorithmCombo.addItem(algorithm);
        }
    }

    private void populateLineupCombos() {
        DefaultListCellRenderer suffixStrippingRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String fileName) {
                    setText(stripLineupSuffix(fileName));
                }
                return this;
            }
        };
        lineupBeforeCombo.setRenderer(suffixStrippingRenderer);
        lineupAfterCombo.setRenderer(suffixStrippingRenderer);

        List<String> fileNames = listLineupFileNames();
        for (String fileName : fileNames) {
            lineupBeforeCombo.addItem(fileName);
            lineupAfterCombo.addItem(fileName);
        }

        // Preselect the currently open lineup file as "before" when it is one of this guild's own lineup files - the most common starting point for a two-saved-lineups comparison.
        String currentFileName = appContext.lineupFilePath() == null ? null : appContext.lineupFilePath().getFileName().toString();
        if (currentFileName != null && fileNames.contains(currentFileName)) {
            lineupBeforeCombo.setSelectedItem(currentFileName);
        }
    }

    private static String stripLineupSuffix(String fileName) {
        return fileName.endsWith(LINEUP_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - LINEUP_FILE_SUFFIX.length())
                : fileName;
    }

    /** Every ".lineup" file directly inside the current guild's folder, sorted alphabetically - see class Javadoc on {@link #LINEUP_FILE_GLOB}. */
    private List<String> listLineupFileNames() {
        List<String> result = new ArrayList<>();
        Path guildDir = appContext.guildFilePath() == null ? null : appContext.guildFilePath().getParent();
        if (guildDir == null || !Files.isDirectory(guildDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(guildDir, LINEUP_FILE_GLOB)) {
            for (Path path : stream) {
                result.add(path.getFileName().toString());
            }
        } catch (IOException e) {
            Logger.logException("Could not list lineup files in " + guildDir, e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private void configureTable(JTable table) {
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
    }

    private void onCompare() {
        Lineup before;
        Lineup after;

        if (savedLineupsRadio.isSelected()) {
            String beforeFileName = (String) lineupBeforeCombo.getSelectedItem();
            String afterFileName = (String) lineupAfterCombo.getSelectedItem();
            if (beforeFileName == null || afterFileName == null) {
                JOptionPane.showMessageDialog(this, "Please select two lineups to compare.",
                        "Compare lineups", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Path guildDir = appContext.guildFilePath().getParent();
            try {
                before = LineupRepository.load(guildDir.resolve(beforeFileName));
                after = LineupRepository.load(guildDir.resolve(afterFileName));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Could not load lineup:\n" + e.getMessage(),
                        "Error while loading lineup", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!before.guildId().equals(after.guildId())) {
                JOptionPane.showMessageDialog(this,
                        "These two lineups belong to different guilds and cannot be meaningfully compared.",
                        "Compare lineups", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else {
            LineupAlgorithm algorithm = (LineupAlgorithm) algorithmCombo.getSelectedItem();
            if (algorithm == null) {
                JOptionPane.showMessageDialog(this, "No algorithm available.",
                        "Compare lineups", JOptionPane.WARNING_MESSAGE);
                return;
            }
            before = appContext.lineup();
            after = algorithm.run(before, guild);
        }

        currentResult = LineupComparisonService.compare(before, after, guild);
        refreshTables();
    }

    private void refreshTables() {
        if (currentResult == null) {
            teamModel.setRows(List.of());
            fortificationModel.setRows(List.of());
            summaryLabel.setText(" ");
            return;
        }
        boolean onlyChanges = onlyChangesCheckbox.isSelected();

        List<TeamDiff> teamRows = onlyChanges
                ? currentResult.teamDiffs().stream().filter(d -> d.status() != TeamDiff.Status.UNCHANGED).toList()
                : currentResult.teamDiffs();
        teamModel.setRows(teamRows);

        List<FortificationDiff> fortificationRows = onlyChanges
                ? currentResult.fortificationDiffs().stream().filter(d -> !d.isUnchanged()).toList()
                : currentResult.fortificationDiffs();
        fortificationModel.setRows(fortificationRows);

        summaryLabel.setText(buildSummaryText(currentResult.summary()));
    }

    private String buildSummaryText(LineupComparisonService.Summary summary) {
        String totalPower = powerRangeText(summary.totalPowerBefore(), summary.totalPowerAfter());
        String heroPower = powerRangeText(summary.heroPowerBefore(), summary.heroPowerAfter());
        String titanPower = powerRangeText(summary.titanPowerBefore(), summary.titanPowerAfter());
        String changes = summary.addedCount() + " +, " + summary.removedCount() + " -, " + summary.movedCount() + " ~";
        return "<html>"
                + LanguageService.displayName(KEY_SUMMARY_TOTAL_POWER) + ": " + totalPower
                + "&nbsp;&nbsp; (" + LanguageService.displayName(KEY_SUMMARY_HERO_POWER) + " " + heroPower
                + ", " + LanguageService.displayName(KEY_SUMMARY_TITAN_POWER) + " " + titanPower + ")"
                + "&nbsp;&nbsp; " + LanguageService.displayName(KEY_SUMMARY_CHANGES) + ": " + changes
                + "</html>";
    }

    private static String powerRangeText(int before, int after) {
        return GuiUtils.NUMBER_FORMAT.format(before) + "→" + GuiUtils.NUMBER_FORMAT.format(after)
                + " (" + signed(after - before) + ")";
    }

    private static String signed(int diff) {
        String formatted = GuiUtils.NUMBER_FORMAT.format(diff);
        return diff > 0 ? "+" + formatted : formatted;
    }

    // --- shared lookups ---

    private String fortificationName(String fortificationId) {
        return fortificationId == null
                ? LanguageService.displayName(KEY_NONE)
                : FortificationRepository.findById(fortificationId)
                        .map(f -> LanguageService.displayName(f.id()))
                        .orElse(fortificationId);
    }

    private String memberName(String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .map(GuildMember::name)
                .orElse(memberId);
    }

    private static String statusText(TeamDiff.Status status) {
        return switch (status) {
            case ADDED -> LanguageService.displayName("lineupComparison.statusAdded");
            case REMOVED -> LanguageService.displayName("lineupComparison.statusRemoved");
            case MOVED -> LanguageService.displayName("lineupComparison.statusMoved");
            case UPDATED -> LanguageService.displayName("lineupComparison.statusUpdated");
            case UNCHANGED -> LanguageService.displayName("lineupComparison.statusUnchanged");
        };
    }

    private static Color statusColor(TeamDiff.Status status) {
        return switch (status) {
            case ADDED -> IconLoader.GREEN;
            case REMOVED -> IconLoader.RED;
            case MOVED, UPDATED -> IconLoader.BLUE;
            case UNCHANGED -> Color.GRAY;
        };
    }

    // --- cell renderers ---

    /** Colors the "Status" column's text per {@link TeamDiff.Status} (see {@link #statusColor}) - looks up the row's status via the table's {@link TeamDiffTableModel}, translated through the row sorter (see {@link #configureTable}). */
    private static final class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                        boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected && table.getModel() instanceof TeamDiffTableModel model) {
                int modelRow = table.convertRowIndexToModel(row);
                setForeground(statusColor(model.statusAt(modelRow)));
            }
            return component;
        }
    }

    /**
     * Right-aligned, signed, colored rendering for a numeric diff column
     * (green for a gain, red for a loss, black for no change) - same
     * gain/loss color convention {@code FortificationPanel} already uses for
     * its own power/buff-percent diffs. Handles both {@link Integer} (power)
     * and {@link Double} (weighted score) cell values.
     */
    private static final class DiffCellRenderer extends DefaultTableCellRenderer {
        DiffCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                        boolean hasFocus, int row, int column) {
            double diff = value instanceof Number number ? number.doubleValue() : 0;
            String text = value instanceof Double
                    ? String.format(Locale.GERMANY, "%.2f", diff)
                    : GuiUtils.NUMBER_FORMAT.format(Math.round(diff));
            if (diff > 0 && !text.startsWith("+")) {
                text = "+" + text;
            }
            Component component = super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setForeground(diff > 0 ? IconLoader.GREEN : diff < 0 ? IconLoader.RED : Color.BLACK);
            }
            return component;
        }
    }

    // --- table models ---

    /** One row per team that is assigned in EITHER lineup (see {@link LineupComparisonService.TeamDiff}). */
    private final class TeamDiffTableModel extends AbstractTableModel {
        private final String[] columnKeys = {
                "lineupComparison.columnStatus", "teamsOverview.member", "lineupComparison.columnTeam",
                "lineupComparison.columnFortificationBefore", "lineupComparison.columnFortificationAfter",
                "lineupComparison.columnPowerRange", "lineupComparison.columnPowerDiff"
        };
        private List<TeamDiff> rows = List.of();

        void setRows(List<TeamDiff> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columnKeys.length;
        }

        @Override
        public String getColumnName(int column) {
            return LanguageService.displayName(columnKeys[column]);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 6) {
                return Integer.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TeamDiff diff = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> statusText(diff.status());
                case 1 -> memberName(diff.teamKey().teamMemberId());
                case 2 -> GuildMember.teamLabel(diff.teamKey().teamIndex()) + " (" + diff.teamKey().teamType() + ")";
                case 3 -> fortificationName(diff.fortificationIdBefore());
                case 4 -> fortificationName(diff.fortificationIdAfter());
                case 5 -> GuiUtils.NUMBER_FORMAT.format(diff.powerBefore()) + " → "
                        + GuiUtils.NUMBER_FORMAT.format(diff.powerAfter());
                case 6 -> diff.powerDiff();
                default -> "";
            };
        }

        TeamDiff.Status statusAt(int rowIndex) {
            return rows.get(rowIndex).status();
        }
    }

    /** One row per fortification assigned in EITHER lineup (see {@link LineupComparisonService.FortificationDiff}). */
    private final class FortificationDiffTableModel extends AbstractTableModel {
        private final String[] columnKeys = {
                "teamsOverview.fortification", "lineupComparison.columnSlots",
                "lineupComparison.columnPowerRange", "lineupComparison.columnPowerDiff",
                "lineupComparison.columnBuffPercent", "lineupComparison.columnBuffPercentDiff"
        };
        private List<FortificationDiff> rows = List.of();

        void setRows(List<FortificationDiff> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columnKeys.length;
        }

        @Override
        public String getColumnName(int column) {
            return LanguageService.displayName(columnKeys[column]);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3 || columnIndex == 5) {
                return Integer.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FortificationDiff diff = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> fortificationName(diff.fortificationId());
                case 1 -> diff.filledSlotsBefore() + " → " + diff.filledSlotsAfter();
                case 2 -> GuiUtils.NUMBER_FORMAT.format(diff.powerBefore()) + " → "
                        + GuiUtils.NUMBER_FORMAT.format(diff.powerAfter());
                case 3 -> diff.powerDiff();
                case 4 -> diff.buffPercentBefore() + "% → " + diff.buffPercentAfter() + "%";
                case 5 -> diff.buffPercentDiff();
                default -> "";
            };
        }
    }
}
