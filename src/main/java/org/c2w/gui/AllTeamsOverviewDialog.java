package org.c2w.gui;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.util.AppContext;
import org.c2w.util.BuffCalculationService;
import org.c2w.util.LanguageService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AllTeamsOverviewDialog extends JDialog {

    /** Dialog title - hardcoded, not localized (matches {@link ReportViewerDialog}). */
    private static final String BASE_TITLE = "Cow2 - All Teams";

    /** Language file key (see resources/language/*.txt) for "no fortification assigned". */
    private static final String KEY_NO_FORTIFICATION = "common.none";

    /** Target size of the member icons (Heroes/Titans column). */
    private static final int MEMBER_ICON_SIZE = 24;

    /** Row height matching {@link #MEMBER_ICON_SIZE} (24px icon + 6px margin). */
    private static final int ROW_HEIGHT = MEMBER_ICON_SIZE + 6;

    /** Preferred width of each per-fortification match-count column (see class Javadoc). */
    private static final int MATCH_COLUMN_WIDTH = 60;

    /**
     * Language file keys (see resources/language/*.txt) for the table
     * column headers/tab titles - the SAME keys {@link TeamsOverviewPanel}
     * uses (the German/English/French text is identical, only the Java
     * constants/classes here are a redundant copy, not the resource keys).
     */
    private static final String COLUMN_KEY_POWER = "teamsOverview.power";
    private static final String COLUMN_KEY_MEMBER = "teamsOverview.member";
    private static final String COLUMN_KEY_HEROES = "teamsOverview.heroes";
    private static final String COLUMN_KEY_TITANS = "teamsOverview.titans";
    private static final String COLUMN_KEY_FORTIFICATION = "teamsOverview.fortification";

    private final AppContext appContext;

    private final FortificationMapPanel fortificationMapPanel;

    private final List<Fortification> heroFortifications = sortedFortifications(FortificationType.HERO);

    private final List<Fortification> titanFortifications = sortedFortifications(FortificationType.TITAN);

    private final List<Fortification> heroBuffFortifications = withBuffOnly(heroFortifications);

    /** {@link FortificationType#TITAN} counterpart of {@link #heroBuffFortifications}, backing {@link #titanModel}'s extra columns. */
    private final List<Fortification> titanBuffFortifications = withBuffOnly(titanFortifications);

    private final AllTeamsTableModel<Hero> heroModel =
            new AllTeamsTableModel<>(COLUMN_KEY_HEROES, heroBuffFortifications, this::handleFortificationSelected);
    private final AllTeamsTableModel<Titan> titanModel =
            new AllTeamsTableModel<>(COLUMN_KEY_TITANS, titanBuffFortifications, this::handleFortificationSelected);
    private final JTable heroTable = new JTable(heroModel);
    private final JTable titanTable = new JTable(titanModel);

    public AllTeamsOverviewDialog(Frame owner, AppContext appContext,
                                  FortificationMapPanel fortificationMapPanel) {
        super(owner, BASE_TITLE, false);
        if (appContext == null) {
            throw new IllegalArgumentException("AllTeamsOverviewDialog needs a guildContext");
        }
        if (fortificationMapPanel == null) {
            throw new IllegalArgumentException("AllTeamsOverviewDialog needs a fortificationMapPanel");
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

     private static List<Fortification> withBuffOnly(List<Fortification> fortifications) {
        return fortifications.stream()
                .filter(f -> f.buff() != null)
                .toList();
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
        for (int col = AllTeamsTableModel.FIXED_COLUMN_COUNT; col < table.getColumnModel().getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(MATCH_COLUMN_WIDTH);
            table.getColumnModel().getColumn(col).setCellRenderer(new MatchCountCellRenderer());
        }

        // Column-header sorting (see TeamsOverviewPanel#configureTable for the
        // full rationale) - the per-fortification match-count columns are
        // plain Integer values, so the default sorter already compares them
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

    private boolean handleFortificationSelected(AllTeamsTableModel.Row<?> row, Fortification fortification) {
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
        List<AllTeamsTableModel.Row<Hero>> heroRows = new ArrayList<>();
        List<AllTeamsTableModel.Row<Titan>> titanRows = new ArrayList<>();
        for (GuildMember member : currentGuild.members()) {
            String memberLabel = memberLabel(member);

            List<HeroTeam> heroTeams = member.heroTeams();
            for (int i = 0; i < heroTeams.size(); i++) {
                HeroTeam team = heroTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.HERO, i);
                int[] matchCounts = matchCountsFor(heroBuffFortifications, member.id(), Lineup.TeamType.HERO, i,
                        team.totalPower(), currentGuild, currentLineup);
                heroRows.add(new AllTeamsTableModel.Row<>(memberLabel, team.heroes(), team.totalPower(),
                        member.id(), Lineup.TeamType.HERO, i, assigned, matchCounts));
            }

            List<TitanTeam> titanTeams = member.titanTeams();
            for (int i = 0; i < titanTeams.size(); i++) {
                TitanTeam team = titanTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.TITAN, i);
                int[] matchCounts = matchCountsFor(titanBuffFortifications, member.id(), Lineup.TeamType.TITAN, i,
                        team.totalPower(), currentGuild, currentLineup);
                titanRows.add(new AllTeamsTableModel.Row<>(memberLabel, team.titans(), team.totalPower(),
                        member.id(), Lineup.TeamType.TITAN, i, assigned, matchCounts));
            }
        }
        heroModel.setRows(heroRows);
        titanModel.setRows(titanRows);
    }

    private static int[] matchCountsFor(List<Fortification> fortifications, String teamMemberId, Lineup.TeamType teamType,
                                        int teamIndex, int totalPower, Guild guild, Lineup currentLineup) {
        int[] counts = new int[fortifications.size()];
        for (int i = 0; i < fortifications.size(); i++) {
            Fortification fortification = fortifications.get(i);
            Lineup.Entry syntheticEntry = new Lineup.Entry(fortification.id(), teamMemberId, teamType, teamIndex,
                    totalPower, 0, 0);
            Lineup syntheticLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                    currentLineup.algorithmName(), currentLineup.createdAt(), List.of(syntheticEntry));
            counts[i] = BuffCalculationService.countMatchingMembersForFortification(
                    fortification.id(), syntheticLineup, guild, fortification.buff());
        }
        return counts;
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
     * Redundant copy of {@code TeamsOverviewPanel.TeamOverviewTableModel} -
     * one row per team, but ONLY column 3 ("Fortification") is editable
     * here (see {@link #isCellEditable}) - column 0 ("Power") is
     * deliberately read-only in this dialog (see class Javadoc), so there
     * is no onPowerEdited/onGuildEdited callback at all, unlike the
     * original. Extended 2026-09-06 with one extra, always-read-only
     * column per entry of {@code matchColumns} (see
     * {@link AllTeamsOverviewDialog#heroFortifications}/
     * {@link AllTeamsOverviewDialog#titanFortifications}) - see class
     * Javadoc.
     */
    static final class AllTeamsTableModel<T> extends AbstractTableModel {

        /** Number of fixed columns before the per-fortification match-count columns start (Power, Member, Heroes/Titans, Fortification). */
        static final int FIXED_COLUMN_COUNT = 4;

        private final String membersColumnKey;

        /** This table's own per-fortification match-count columns, in display order - see {@link AllTeamsOverviewDialog#matchCountsFor}. */
        private final List<Fortification> matchColumns;

        /**
         * Called from {@link #setValueAt} after a "Fortification" combo box
         * selection (column 3) was committed for a row - see
         * {@link AllTeamsOverviewDialog#handleFortificationSelected}.
         * Returns whether the pick is accepted; only then is the row's own
         * assignedFortification field updated.
         */
        private final BiFunction<Row<T>, Fortification, Boolean> onFortificationSelected;

        private List<Row<T>> rows = new ArrayList<>();

        AllTeamsTableModel(String membersColumnKey, List<Fortification> matchColumns,
                           BiFunction<Row<T>, Fortification, Boolean> onFortificationSelected) {
            this.membersColumnKey = membersColumnKey;
            this.matchColumns = matchColumns;
            this.onFortificationSelected = onFortificationSelected;
        }

        void setRows(List<Row<T>> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        List<Row<T>> rows() {
            return rows;
        }

        /** This table's own per-fortification match-count columns, in display order - see {@link MatchCountCellRenderer}. */
        List<Fortification> matchColumns() {
            return matchColumns;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return FIXED_COLUMN_COUNT + matchColumns.size();
        }

        @Override
        public String getColumnName(int column) {
            if (column >= FIXED_COLUMN_COUNT) {
                return LanguageService.displayName(matchColumns.get(column - FIXED_COLUMN_COUNT).id());
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
                return Integer.class;
            }
            return switch (column) {
                case 0 -> Integer.class;
                case 2 -> List.class;
                case 3 -> Fortification.class;
                default -> String.class;
            };
        }

        /** Only the "Fortification" column (3) is editable - "Power" (0) and every match-count column are read-only (see class Javadoc). */
        @Override
        public boolean isCellEditable(int rowIndex, int column) {
            return column == 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            Row<T> row = rows.get(rowIndex);
            if (column >= FIXED_COLUMN_COUNT) {
                return row.matchCounts[column - FIXED_COLUMN_COUNT];
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
         * One table row = one team. Unlike {@code TeamsOverviewPanel.TeamOverviewTableModel.Row},
         * totalPower is final here - "Power" is not editable in this
         * dialog (see class Javadoc), so it never changes after the row is
         * built. matchCounts (added 2026-09-06) holds one entry per
         * {@link AllTeamsTableModel#matchColumns}, same order - see
         * {@link AllTeamsOverviewDialog#matchCountsFor}.
         */
        static final class Row<T> {
            final String memberLabel;
            final List<T> members;
            final int totalPower;
            final String teamMemberId;
            final Lineup.TeamType teamType;
            final int teamIndex;
            Fortification assignedFortification;
            final int[] matchCounts;

            Row(String memberLabel, List<T> members, int totalPower,
                String teamMemberId, Lineup.TeamType teamType, int teamIndex, Fortification assignedFortification,
                int[] matchCounts) {
                this.memberLabel = memberLabel;
                this.members = members;
                this.totalPower = totalPower;
                this.teamMemberId = teamMemberId;
                this.teamType = teamType;
                this.teamIndex = teamIndex;
                this.assignedFortification = assignedFortification;
                this.matchCounts = matchCounts;
            }
        }
    }

    /** Redundant copy of {@code TeamsOverviewPanel.MembersCellRenderer}. */
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
            JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 0));
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

    /** Redundant copy of {@code TeamsOverviewPanel.PowerCellRenderer}. */
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

    /** Redundant copy of {@code TeamsOverviewPanel.FortificationCellRenderer}. */
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

    static final class MatchCountCellRenderer implements TableCellRenderer {

        /** Very light pastel green used to highlight a row's currently assigned fortification's own match-count column (see class Javadoc). */
        private static final Color MATCH_HIGHLIGHT_BACKGROUND = new Color(224, 247, 224);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = new JLabel(String.valueOf(value), JLabel.CENTER);
            label.setOpaque(true);
            label.setBackground(backgroundFor(table, isSelected, row, column));
            return label;
        }

        private static Color backgroundFor(JTable table, boolean isSelected, int row, int column) {
            if (isSelected) {
                return table.getSelectionBackground();
            }
            AllTeamsTableModel<?> model = (AllTeamsTableModel<?>) table.getModel();
            int modelRow = table.convertRowIndexToModel(row);
            int modelColumn = table.convertColumnIndexToModel(column);
            Fortification columnFortification = model.matchColumns().get(modelColumn - AllTeamsTableModel.FIXED_COLUMN_COUNT);
            Fortification assignedFortification = model.rows().get(modelRow).assignedFortification;
            return columnFortification.equals(assignedFortification) ? MATCH_HIGHLIGHT_BACKGROUND : table.getBackground();
        }
    }
}
