package org.c2w.gui;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.TeamScoreCalculator;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Analogous to {@link AllTeamsOverviewDialog} - same one-row-per-team table,
 * same "Fortification" assignment column/behavior, same per-fortification
 * extra columns - but those extra columns show each team's
 * {@link TeamScoreCalculator} score against that fortification (buffFitScore-
 * or generalScore-based, plus the totalPower term - see
 * {@link TeamScoreCalculator} for the formula) instead of a buff match
 * count. Unlike {@link AllTeamsOverviewDialog}, which only adds a column per
 * BUFFED fortification (match counts are meaningless without a buff to match
 * against), this dialog adds one column per BUFFED fortification of the
 * matching type, plus a single shared column standing in for every
 * buff-less fortification of that type: a buff-less fortification's score
 * always falls back to the same generalScore-based formula (see
 * {@link TeamScoreCalculator}), so it is identical no matter which buff-less
 * fortification is actually picked, and listing one column per buff-less
 * fortification would just repeat the same number (see {@link ScoreColumn}).
 * The "Fortification" assignment column/combo is unaffected by this
 * collapsing - every individual fortification, buffed or not, is still its
 * own pickable map location with its own capacity.
 *
 * <p>Most of this class - the table structure, the editable "Fortification"
 * column, the member/power rendering - is a deliberate near-duplicate of
 * {@link AllTeamsOverviewDialog}, matching that class's own "redundant copy"
 * convention (see its cell renderer Javadocs) rather than sharing code
 * across two otherwise-independent dialogs.
 */
public class AllTeamsScoreOverviewDialog extends JDialog {

    /** Dialog title - hardcoded, not localized (matches {@link AllTeamsOverviewDialog}/{@link ReportViewerDialog}). */
    private static final String BASE_TITLE = "Cow2 - Team Scores";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for "no fortification assigned". */
    private static final String KEY_NO_FORTIFICATION = "common.none";

    /** Target size of the member icons (Heroes/Titans column). */
    private static final int MEMBER_ICON_SIZE = 24;

    /** Row height matching {@link #MEMBER_ICON_SIZE} (24px icon + 6px margin). */
    private static final int ROW_HEIGHT = MEMBER_ICON_SIZE + 6;

    /** Preferred width of each per-fortification score column (see class Javadoc). */
    private static final int SCORE_COLUMN_WIDTH = 70;

    /**
     * Language file keys (see {@code resources/language/<name>/<name>.properties}) for the table
     * column headers/tab titles - the SAME keys {@link AllTeamsOverviewDialog}/
     * {@link TeamsOverviewPanel} use (redundant copy, not the resource keys).
     */
    private static final String COLUMN_KEY_POWER = "teamsOverview.power";
    private static final String COLUMN_KEY_MEMBER = "teamsOverview.member";
    private static final String COLUMN_KEY_HEROES = "teamsOverview.heroes";
    private static final String COLUMN_KEY_TITANS = "teamsOverview.titans";
    private static final String COLUMN_KEY_FORTIFICATION = "teamsOverview.fortification";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the shared "no buff" score column header - see {@link ScoreColumn}. */
    private static final String COLUMN_KEY_NO_BUFF = "teamsOverview.noBuff";

    private final AppContext appContext;

    private final FortificationMapPanel fortificationMapPanel;

    /** Every fortification of the matching type, buffed or not - used for the "Fortification" assignment combo, unaffected by the score-column collapsing (see class Javadoc). */
    private final List<Fortification> heroFortifications = sortedFortifications(FortificationType.HERO);

    private final List<Fortification> titanFortifications = sortedFortifications(FortificationType.TITAN);

    /** This dialog's own score-table columns - one per buffed fortification plus one shared "no buff" column (see {@link ScoreColumn}/class Javadoc), derived from the full lists above. */
    private final List<ScoreColumn> heroScoreColumns = buildScoreColumns(heroFortifications);

    private final List<ScoreColumn> titanScoreColumns = buildScoreColumns(titanFortifications);

    private final AllTeamsScoreTableModel<Hero> heroModel =
            new AllTeamsScoreTableModel<>(COLUMN_KEY_HEROES, heroScoreColumns, this::handleFortificationSelected);
    private final AllTeamsScoreTableModel<Titan> titanModel =
            new AllTeamsScoreTableModel<>(COLUMN_KEY_TITANS, titanScoreColumns, this::handleFortificationSelected);
    private final JTable heroTable = new JTable(heroModel);
    private final JTable titanTable = new JTable(titanModel);

    public AllTeamsScoreOverviewDialog(Frame owner, AppContext appContext,
                                       FortificationMapPanel fortificationMapPanel) {
        super(owner, BASE_TITLE, false);
        if (appContext == null) {
            throw new IllegalArgumentException("AllTeamsScoreOverviewDialog needs a guildContext");
        }
        if (fortificationMapPanel == null) {
            throw new IllegalArgumentException("AllTeamsScoreOverviewDialog needs a fortificationMapPanel");
        }
        this.appContext = appContext;
        this.fortificationMapPanel = fortificationMapPanel;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JComboBox<Fortification> heroFortificationCombo = buildFortificationCombo(heroFortifications);
        JComboBox<Fortification> titanFortificationCombo = buildFortificationCombo(titanFortifications);
        this.<Hero>configureTable(heroTable, h -> IconLoader.iconFor(h.imagePath(), MEMBER_ICON_SIZE),
                h -> LanguageService.displayName(h.id()), heroFortificationCombo);
        this.<Titan>configureTable(titanTable, t -> IconLoader.iconFor(t.imagePath(), MEMBER_ICON_SIZE),
                t -> LanguageService.displayName(t.id()), titanFortificationCombo);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(LanguageService.displayName(COLUMN_KEY_HEROES), new JScrollPane(heroTable));
        tabs.addTab(LanguageService.displayName(COLUMN_KEY_TITANS), new JScrollPane(titanTable));
        // Tab label colors match FortificationType's own colors, same as e.g.
        // the "show heroes"/"show titans" checkboxes in FortificationMapPanel.
        tabs.setForegroundAt(0, FortificationType.HERO.getColor());
        tabs.setForegroundAt(1, FortificationType.TITAN.getColor());

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(tabs, BorderLayout.CENTER);
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);

        refreshTables();

        setSize(900, 600);
        setLocationRelativeTo(owner);
    }

    private static List<Fortification> sortedFortifications(FortificationType type) {
        return FortificationRepository.findAll().stream()
                .filter(f -> f.type() == type)
                .sorted(Comparator.comparing(f -> LanguageService.displayName(f.id())))
                .toList();
    }

    /**
     * Turns {@code fortifications} (already sorted, one type) into this
     * dialog's score columns: every buffed fortification keeps its own
     * column (same order), and every buff-less fortification is folded into
     * a single shared column appended at the end - see {@link ScoreColumn}/
     * class Javadoc. Returns only the buffed columns, with no shared column
     * appended, if {@code fortifications} has no buff-less entry at all.
     */
    private static List<ScoreColumn> buildScoreColumns(List<Fortification> fortifications) {
        List<ScoreColumn> columns = new ArrayList<>();
        Fortification firstBuffLess = null;
        for (Fortification fortification : fortifications) {
            if (fortification.buff() != null) {
                columns.add(ScoreColumn.forBuffedFortification(fortification));
            } else if (firstBuffLess == null) {
                firstBuffLess = fortification;
            }
        }
        if (firstBuffLess != null) {
            columns.add(ScoreColumn.sharedNoBuff(firstBuffLess));
        }
        return List.copyOf(columns);
    }

    private static <T> void configureTable(JTable table, Function<T, Icon> iconResolver, Function<T, String> nameResolver,
                                           JComboBox<Fortification> fortificationCombo) {
        table.setRowHeight(ROW_HEIGHT);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(0).setCellRenderer(new PowerCellRenderer());
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setCellRenderer(new MembersCellRenderer<>(iconResolver, nameResolver));
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setCellRenderer(new FortificationCellRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(fortificationCombo));
        for (int col = AllTeamsScoreTableModel.FIXED_COLUMN_COUNT; col < table.getColumnModel().getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(SCORE_COLUMN_WIDTH);
            table.getColumnModel().getColumn(col).setCellRenderer(new ScoreCellRenderer());
        }

        // Column-header sorting (see TeamsOverviewPanel#configureTable for
        // the full rationale) - the per-fortification score columns are
        // plain Double values, so the default sorter already compares them
        // correctly without a custom comparator, same as column 0 (Power).
        table.setAutoCreateRowSorter(true);
        if (table.getRowSorter() instanceof TableRowSorter<?> rowSorter) {
            @SuppressWarnings("unchecked")
            TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) rowSorter;
            sorter.setComparator(2, Comparator.<List<T>, String>comparing(
                    members -> members.isEmpty() ? "" : nameResolver.apply(members.get(0)),
                    String.CASE_INSENSITIVE_ORDER));
        }
    }

    private static JComboBox<Fortification> buildFortificationCombo(List<Fortification> sortedFortifications) {
        DefaultComboBoxModel<Fortification> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        sortedFortifications.forEach(model::addElement);

        JComboBox<Fortification> combo = new JComboBox<>(model);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? LanguageService.displayName(KEY_NO_FORTIFICATION)
                        : LanguageService.displayName(((Fortification) value).id()));
                return this;
            }
        });
        return combo;
    }

    private boolean handleFortificationSelected(AllTeamsScoreTableModel.Row<?> row, Fortification fortification) {
        Lineup currentLineup = appContext.lineup();
        List<Lineup.Entry> otherEntries = new ArrayList<>();
        for (Lineup.Entry entry : currentLineup.entries()) {
            boolean sameTeam = entry.teamMemberId().equals(row.teamMemberId) && entry.teamType() == row.teamType
                    && entry.teamIndex() == row.teamIndex;
            if (!sameTeam) {
                otherEntries.add(entry);
            }
        }

        if (fortification != null) {
            long filledSlots = otherEntries.stream()
                    .filter(entry -> entry.fortificationId().equals(fortification.id()))
                    .count();
            if (filledSlots >= fortification.capacity()) {
                JOptionPane.showMessageDialog(this,
                        "Fortification \"" + LanguageService.displayName(fortification.id()) + "\" has no free slot ("
                                + filledSlots + "/" + fortification.capacity() + " already assigned).",
                        "Fortification full", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }

        List<Lineup.Entry> updatedEntries = new ArrayList<>(otherEntries);
        if (fortification != null) {
            updatedEntries.add(new Lineup.Entry(fortification.id(), row.teamMemberId, row.teamType, row.teamIndex,
                    row.totalPower, 0, 0));
        }
        Lineup updatedLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                currentLineup.algorithmName(), currentLineup.createdAt(), updatedEntries);
        appContext.setLineup(updatedLineup);
        GuiUtils.editedLineup = true;
        fortificationMapPanel.refresh(updatedLineup);
        return true;
    }

    private void refreshTables() {
        Guild currentGuild = appContext.guild();
        Lineup currentLineup = appContext.lineup();
        List<AllTeamsScoreTableModel.Row<Hero>> heroRows = new ArrayList<>();
        List<AllTeamsScoreTableModel.Row<Titan>> titanRows = new ArrayList<>();
        for (GuildMember member : currentGuild.members()) {
            String memberLabel = memberLabel(member);

            List<HeroTeam> heroTeams = member.heroTeams();
            for (int i = 0; i < heroTeams.size(); i++) {
                HeroTeam team = heroTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.HERO, i);
                double[] scores = heroScoresFor(team, heroScoreColumns);
                heroRows.add(new AllTeamsScoreTableModel.Row<>(memberLabel, team.heroes(), team.totalPower(),
                        member.id(), Lineup.TeamType.HERO, i, assigned, scores));
            }

            List<TitanTeam> titanTeams = member.titanTeams();
            for (int i = 0; i < titanTeams.size(); i++) {
                TitanTeam team = titanTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.TITAN, i);
                double[] scores = titanScoresFor(team, titanScoreColumns);
                titanRows.add(new AllTeamsScoreTableModel.Row<>(memberLabel, team.titans(), team.totalPower(),
                        member.id(), Lineup.TeamType.TITAN, i, assigned, scores));
            }
        }
        heroModel.setRows(heroRows);
        titanModel.setRows(titanRows);
    }

    /**
     * {@code team}'s {@link TeamScoreCalculator} score against every entry of
     * {@code scoreColumns}, same order - for the shared "no buff" column
     * (see {@link ScoreColumn}), this uses that column's representative
     * buff-less fortification, which yields the same result as any other
     * buff-less fortification would (see class Javadoc).
     */
    private static double[] heroScoresFor(HeroTeam team, List<ScoreColumn> scoreColumns) {
        double[] scores = new double[scoreColumns.size()];
        for (int i = 0; i < scoreColumns.size(); i++) {
            scores[i] = TeamScoreCalculator.scoreFor(team, scoreColumns.get(i).representative()).total();
        }
        return scores;
    }

    /** The TITAN-side counterpart of {@link #heroScoresFor}. */
    private static double[] titanScoresFor(TitanTeam team, List<ScoreColumn> scoreColumns) {
        double[] scores = new double[scoreColumns.size()];
        for (int i = 0; i < scoreColumns.size(); i++) {
            scores[i] = TeamScoreCalculator.scoreFor(team, scoreColumns.get(i).representative()).total();
        }
        return scores;
    }

    private static Fortification findAssignedFortification(Lineup lineup, String teamMemberId,
                                                           Lineup.TeamType teamType, int teamIndex) {
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamMemberId().equals(teamMemberId) && entry.teamType() == teamType
                    && entry.teamIndex() == teamIndex) {
                return FortificationRepository.findById(entry.fortificationId()).orElse(null);
            }
        }
        return null;
    }

    private static String memberLabel(GuildMember member) {
        String name = member.name();
        return (name == null || name.isBlank()) ? member.id() : name;
    }

    /**
     * Redundant copy of {@code AllTeamsOverviewDialog.AllTeamsTableModel} -
     * identical structure (one row per team, only column 3 "Fortification"
     * editable), but {@code scoreColumns}/{@code Row#scores} hold a
     * {@code double} {@link TeamScoreCalculator} total per {@link ScoreColumn}
     * instead of an {@code int} buff match count, and cover every BUFFED
     * fortification of the matching type plus one shared "no buff" column
     * (not only buffed ones - see class Javadoc/{@link ScoreColumn}).
     */
    static final class AllTeamsScoreTableModel<T> extends AbstractTableModel {

        /** Number of fixed columns before the per-fortification score columns start (Power, Member, Heroes/Titans, Fortification). */
        static final int FIXED_COLUMN_COUNT = 4;

        private final String membersColumnKey;

        /** This table's own score columns, in display order - see {@link AllTeamsScoreOverviewDialog#heroScoresFor}/{@link AllTeamsScoreOverviewDialog#titanScoresFor}. */
        private final List<ScoreColumn> scoreColumns;

        /**
         * Called from {@link #setValueAt} after a "Fortification" combo box
         * selection (column 3) was committed for a row - see
         * {@link AllTeamsScoreOverviewDialog#handleFortificationSelected}.
         * Returns whether the pick is accepted; only then is the row's own
         * assignedFortification field updated.
         */
        private final BiFunction<Row<T>, Fortification, Boolean> onFortificationSelected;

        private List<Row<T>> rows = new ArrayList<>();

        AllTeamsScoreTableModel(String membersColumnKey, List<ScoreColumn> scoreColumns,
                                BiFunction<Row<T>, Fortification, Boolean> onFortificationSelected) {
            this.membersColumnKey = membersColumnKey;
            this.scoreColumns = scoreColumns;
            this.onFortificationSelected = onFortificationSelected;
        }

        void setRows(List<Row<T>> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        List<Row<T>> rows() {
            return rows;
        }

        /** This table's own score columns, in display order - see {@link ScoreCellRenderer}. */
        List<ScoreColumn> scoreColumns() {
            return scoreColumns;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return FIXED_COLUMN_COUNT + scoreColumns.size();
        }

        @Override
        public String getColumnName(int column) {
            if (column >= FIXED_COLUMN_COUNT) {
                return scoreColumns.get(column - FIXED_COLUMN_COUNT).headerText();
            }
            return switch (column) {
                case 0 -> LanguageService.displayName(COLUMN_KEY_POWER);
                case 1 -> LanguageService.displayName(COLUMN_KEY_MEMBER);
                case 2 -> LanguageService.displayName(membersColumnKey);
                case 3 -> LanguageService.displayName(COLUMN_KEY_FORTIFICATION);
                default -> throw new IllegalArgumentException("Unknown column: " + column);
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column >= FIXED_COLUMN_COUNT) {
                return Double.class;
            }
            return switch (column) {
                case 0 -> Integer.class;
                case 2 -> List.class;
                case 3 -> Fortification.class;
                default -> String.class;
            };
        }

        /** Only the "Fortification" column (3) is editable - "Power" (0) and every score column are read-only (see class Javadoc). */
        @Override
        public boolean isCellEditable(int rowIndex, int column) {
            return column == 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            Row<T> row = rows.get(rowIndex);
            if (column >= FIXED_COLUMN_COUNT) {
                return row.scores[column - FIXED_COLUMN_COUNT];
            }
            return switch (column) {
                case 0 -> row.totalPower;
                case 1 -> row.memberLabel;
                case 2 -> row.members;
                case 3 -> row.assignedFortification;
                default -> throw new IllegalArgumentException("Unknown column: " + column);
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int column) {
            if (column == 3) {
                Row<T> row = rows.get(rowIndex);
                Fortification fortification = (Fortification) value;
                boolean accepted = onFortificationSelected == null || onFortificationSelected.apply(row, fortification);
                if (accepted) {
                    row.assignedFortification = fortification;
                }
                fireTableCellUpdated(rowIndex, column);
            }
        }

        /**
         * One table row = one team. totalPower is final here - "Power" is
         * not editable in this dialog (see class Javadoc), so it never
         * changes after the row is built. scores holds one entry per
         * {@link AllTeamsScoreTableModel#scoreColumns}, same order - see
         * {@link AllTeamsScoreOverviewDialog#heroScoresFor}/
         * {@link AllTeamsScoreOverviewDialog#titanScoresFor}.
         */
        static final class Row<T> {
            final String memberLabel;
            final List<T> members;
            final int totalPower;
            final String teamMemberId;
            final Lineup.TeamType teamType;
            final int teamIndex;
            Fortification assignedFortification;
            final double[] scores;

            Row(String memberLabel, List<T> members, int totalPower,
                String teamMemberId, Lineup.TeamType teamType, int teamIndex, Fortification assignedFortification,
                double[] scores) {
                this.memberLabel = memberLabel;
                this.members = members;
                this.totalPower = totalPower;
                this.teamMemberId = teamMemberId;
                this.teamType = teamType;
                this.teamIndex = teamIndex;
                this.assignedFortification = assignedFortification;
                this.scores = scores;
            }
        }
    }

    /** Redundant copy of {@code AllTeamsOverviewDialog.MembersCellRenderer}. */
    static final class MembersCellRenderer<T> implements TableCellRenderer {

        private final Function<T, Icon> iconResolver;
        private final Function<T, String> nameResolver;

        MembersCellRenderer(Function<T, Icon> iconResolver, Function<T, String> nameResolver) {
            this.iconResolver = iconResolver;
            this.nameResolver = nameResolver;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
            panel.setOpaque(true);
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

            @SuppressWarnings("unchecked")
            List<T> members = (List<T>) value;
            if (members != null) {
                for (T member : members) {
                    Icon icon = iconResolver.apply(member);
                    JLabel label = icon != null ? new JLabel(icon) : new JLabel(nameResolver.apply(member));
                    label.setOpaque(false);
                    if (icon != null) {
                        label.setToolTipText(nameResolver.apply(member));
                    }
                    panel.add(label);
                }
            }
            return panel;
        }
    }

    /** Redundant copy of {@code AllTeamsOverviewDialog.PowerCellRenderer}. */
    static final class PowerCellRenderer implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = new JLabel(GuiUtils.NUMBER_FORMAT.format((Integer) value), JLabel.RIGHT);
            label.setOpaque(true);
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return label;
        }
    }

    /** Redundant copy of {@code AllTeamsOverviewDialog.FortificationCellRenderer}. */
    static final class FortificationCellRenderer implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Fortification fortification = (Fortification) value;
            JLabel label = new JLabel(fortification == null ? LanguageService.displayName(KEY_NO_FORTIFICATION)
                    : LanguageService.displayName(fortification.id()));
            label.setOpaque(true);
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return label;
        }
    }

    /**
     * Renders one score cell, e.g. "12.5" - the SCORE-column counterpart of
     * {@code AllTeamsOverviewDialog.MatchCountCellRenderer}, same
     * light-green highlight for a row's currently assigned fortification's
     * own column (for the shared "no buff" column, see {@link ScoreColumn},
     * that means ANY buff-less assignment, not one specific fortification).
     */
    static final class ScoreCellRenderer implements TableCellRenderer {

        /** Same pastel green as {@code AllTeamsOverviewDialog.MatchCountCellRenderer.MATCH_HIGHLIGHT_BACKGROUND}. */
        private static final Color SCORE_HIGHLIGHT_BACKGROUND = new Color(224, 247, 224);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            double score = (Double) value;
            JLabel label = new JLabel(String.format(Locale.ROOT, "%.1f", score), JLabel.CENTER);
            label.setOpaque(true);
            label.setBackground(backgroundFor(table, isSelected, row, column));
            return label;
        }

        private static Color backgroundFor(JTable table, boolean isSelected, int row, int column) {
            if (isSelected) {
                return table.getSelectionBackground();
            }
            AllTeamsScoreTableModel<?> model = (AllTeamsScoreTableModel<?>) table.getModel();
            int modelRow = table.convertRowIndexToModel(row);
            int modelColumn = table.convertColumnIndexToModel(column);
            ScoreColumn scoreColumn = model.scoreColumns().get(modelColumn - AllTeamsScoreTableModel.FIXED_COLUMN_COUNT);
            Fortification assignedFortification = model.rows().get(modelRow).assignedFortification;
            return scoreColumn.matchesAssignment(assignedFortification) ? SCORE_HIGHLIGHT_BACKGROUND : table.getBackground();
        }
    }

    /**
     * One score-table column (see class Javadoc): either one specific
     * BUFFED fortification, or the single column shared by every buff-less
     * fortification of this type. A buff-less fortification's
     * {@link TeamScoreCalculator} score always uses the generalScore-based
     * branch - never that specific fortification's (non-existent) buff - so
     * it is numerically identical no matter which buff-less fortification is
     * actually assigned; {@link #representative()} is therefore only ever
     * used to drive that shared calculation, never to tell two buff-less
     * fortifications apart.
     */
    static final class ScoreColumn {

        private final Fortification representative;
        private final boolean sharedNoBuffColumn;

        private ScoreColumn(Fortification representative, boolean sharedNoBuffColumn) {
            this.representative = representative;
            this.sharedNoBuffColumn = sharedNoBuffColumn;
        }

        /** One column for exactly this (buffed) fortification. */
        static ScoreColumn forBuffedFortification(Fortification fortification) {
            return new ScoreColumn(fortification, false);
        }

        /**
         * The single shared "no buff" column - {@code anyBuffLessFortification}
         * is an arbitrary representative (any buff-less fortification of
         * this type does equally well, see class Javadoc) used only to
         * compute the (shared) score, never shown or compared by identity.
         */
        static ScoreColumn sharedNoBuff(Fortification anyBuffLessFortification) {
            return new ScoreColumn(anyBuffLessFortification, true);
        }

        /** The fortification to score teams against for this column - see class Javadoc. */
        Fortification representative() {
            return representative;
        }

        /** This column's header text, in the currently configured language. */
        String headerText() {
            return sharedNoBuffColumn ? LanguageService.displayName(COLUMN_KEY_NO_BUFF)
                    : LanguageService.displayName(representative.id());
        }

        /**
         * Whether {@code assignedFortification} (a row's current
         * "Fortification" assignment, possibly null) belongs to this column
         * - for the shared "no buff" column, that means any buff-less
         * fortification at all, not just {@link #representative()}.
         */
        boolean matchesAssignment(Fortification assignedFortification) {
            if (assignedFortification == null) {
                return false;
            }
            return sharedNoBuffColumn ? assignedFortification.buff() == null
                    : representative.equals(assignedFortification);
        }
    }
}
