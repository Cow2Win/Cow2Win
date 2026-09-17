package org.c2w.gui;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.GuildRepository;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TeamsOverviewPanel extends JPanel {

    /**
     * Language file key (see {@code resources/language/<name>/<name>.properties}) for "no
     * fortification assigned" - used both in the Fortification combo and in
     * the cell display, resolved via {@link LanguageService#displayName}
     * just like the column headers (see {@link #COLUMN_KEY_POWER} and
     * friends) and the hero/titan/fortification names themselves.
     */
    private static final String KEY_NO_FORTIFICATION = "common.none";

    /** Target size of the member icons (Heroes/Titans column). */
    private static final int MEMBER_ICON_SIZE = 24;

    /** Row height matching {@link #MEMBER_ICON_SIZE} (24px icon + 6px margin). */
    private static final int ROW_HEIGHT = MEMBER_ICON_SIZE + 6;


    private static final String COLUMN_KEY_POWER = "teamsOverview.power";
    private static final String COLUMN_KEY_MEMBER = "teamsOverview.member";
    private static final String COLUMN_KEY_HEROES = "teamsOverview.heroes";
    private static final String COLUMN_KEY_TITANS = "teamsOverview.titans";
    private static final String COLUMN_KEY_FORTIFICATION = "teamsOverview.fortification";

    private final AppContext appContext;
    private final FortificationMapPanel fortificationMapPanel;

    private final TeamOverviewTableModel<Hero> heroModel =
            new TeamOverviewTableModel<>(COLUMN_KEY_HEROES, this::handleFortificationSelected, this::handlePowerEdited,
                    this::markGuildEdited);
    private final TeamOverviewTableModel<Titan> titanModel =
            new TeamOverviewTableModel<>(COLUMN_KEY_TITANS, this::handleFortificationSelected, this::handlePowerEdited,
                    this::markGuildEdited);
    private final JTable heroTable = new JTable(heroModel);
    private final JTable titanTable = new JTable(titanModel);

    public TeamsOverviewPanel(AppContext appContext, FortificationMapPanel fortificationMapPanel) {
        super(new BorderLayout(8, 8));
        if (appContext == null) {
            throw new IllegalArgumentException("TeamsOverviewPanel needs appContext");
        }

        if (fortificationMapPanel == null) {
            throw new IllegalArgumentException("TeamsOverviewPanel needs a fortificationMapPanel");
        }
        this.appContext = appContext;

        this.fortificationMapPanel = fortificationMapPanel;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        java.util.List<Fortification> fortificationCatalog = FortificationRepository.findAll();
        JComboBox<Fortification> heroFortificationCombo = buildFortificationCombo(fortificationCatalog, FortificationType.HERO);
        JComboBox<Fortification> titanFortificationCombo = buildFortificationCombo(fortificationCatalog, FortificationType.TITAN);
        this.<Hero>configureTable(heroTable, h -> IconLoader.iconFor(h.imagePath(), MEMBER_ICON_SIZE),
                h -> LanguageService.displayName(h.id()), heroFortificationCombo);
        this.<Titan>configureTable(titanTable, t -> IconLoader.iconFor(t.imagePath(), MEMBER_ICON_SIZE),
                t -> LanguageService.displayName(t.id()), titanFortificationCombo);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(LanguageService.displayName(COLUMN_KEY_HEROES), new JScrollPane(heroTable));
        tabs.addTab(LanguageService.displayName(COLUMN_KEY_TITANS), new JScrollPane(titanTable));
        add(tabs, BorderLayout.CENTER);

        refreshTables();
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

        table.setAutoCreateRowSorter(true);
        if (table.getRowSorter() instanceof TableRowSorter<?> rowSorter) {
            @SuppressWarnings("unchecked")
            TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) rowSorter;
            sorter.setComparator(2, Comparator.<java.util.List<T>, String>comparing(
                    members -> members.isEmpty() ? "" : nameResolver.apply(members.get(0)),
                    String.CASE_INSENSITIVE_ORDER));
        }
    }

    private static JComboBox<Fortification> buildFortificationCombo(java.util.List<Fortification> fortificationCatalog, FortificationType type) {
        java.util.List<Fortification> sorted = fortificationCatalog.stream()
                .filter(f -> f.type() == type)
                .sorted(Comparator.comparing(f -> LanguageService.displayName(f.id())))
                .toList();

        DefaultComboBoxModel<Fortification> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        sorted.forEach(model::addElement);

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


    /**
     * Runs the given algorithm against the current lineup/guild and
     * refreshes this panel's tables/{@link #fortificationMapPanel}
     * accordingly - triggered by the algorithm/"run algorithm" toolbar
     * controls, which live in {@link ToolbarPanel} (moved there 2026-09-09
     * along with the "save guild"/"open guild editor" buttons, see
     * {@link #saveGuild()}), since this panel no longer has a toolbar of its
     * own. Returns the number of teams newly assigned to a fortification by
     * the run, so the caller can report it (e.g. in a status label).
     */
    public int runAlgorithm(LineupAlgorithm algorithm) {
        if (algorithm == null) {
            return 0;
        }
        Lineup currentLineup = appContext.lineup();
        Lineup updatedLineup = algorithm.run(currentLineup, appContext.guild());
        int assigned = updatedLineup.entries().size() - currentLineup.entries().size();
        appContext.setLineup(updatedLineup);
        if (assigned > 0) {
            GuiUtils.editedLineup = true;
        }
        fortificationMapPanel.refresh(updatedLineup);
        refreshTables();
        return assigned;
    }


    /**
     * Saves the current guild (with whatever's currently in the tables, see
     * {@link #guildWithCurrentSelection()}) to disk - triggered by the "save
     * guild" toolbar button, which lives in {@link ToolbarPanel} (see
     * {@link #runAlgorithm} for why).
     */
    public void saveGuild() {
        try {
            Guild updated = guildWithCurrentSelection();
            GuildRepository.save(updated, appContext.guildFilePath());
            appContext.setGuild(updated);
            GuiUtils.editedGuild = false;
            Logger.log("Saved: " + appContext.guildFilePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save guild:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        }
    }


    private boolean handleFortificationSelected(TeamOverviewTableModel.Row<?> row, Fortification fortification) {
        Lineup currentLineup = appContext.lineup();
        java.util.List<Lineup.Entry> otherEntries = new ArrayList<>();
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

        java.util.List<Lineup.Entry> updatedEntries = new ArrayList<>(otherEntries);
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

    private boolean handlePowerEdited(TeamOverviewTableModel.Row<?> row, int newPower) {
        if (newPower < 0) {
            JOptionPane.showMessageDialog(this, "Power must not be negative.",
                    "Invalid power", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }


    private void markGuildEdited() {
        GuiUtils.editedGuild = true;
    }


    private Guild guildWithCurrentSelection() {
        Guild currentGuild = appContext.guild();
        java.util.List<TeamOverviewTableModel.Row<Hero>> heroRows = heroModel.rows();
        java.util.List<TeamOverviewTableModel.Row<Titan>> titanRows = titanModel.rows();
        int heroIndex = 0;
        int titanIndex = 0;
        java.util.List<GuildMember> updatedMembers = new ArrayList<>();
        for (GuildMember member : currentGuild.members()) {
            java.util.List<HeroTeam> updatedHeroTeams = new ArrayList<>();
            for (HeroTeam team : member.heroTeams()) {
                TeamOverviewTableModel.Row<Hero> row = heroRows.get(heroIndex);
                LocalDate lastModified = row.totalPower != team.totalPower() ? LocalDate.now() : team.lastModified();
                updatedHeroTeams.add(new HeroTeam(team.memberId(), team.heroes(), row.totalPower, lastModified));
                heroIndex++;
            }
            java.util.List<TitanTeam> updatedTitanTeams = new ArrayList<>();
            for (TitanTeam team : member.titanTeams()) {
                TeamOverviewTableModel.Row<Titan> row = titanRows.get(titanIndex);
                LocalDate lastModified = row.totalPower != team.totalPower() ? LocalDate.now() : team.lastModified();
                updatedTitanTeams.add(new TitanTeam(team.memberId(), team.titans(), row.totalPower, lastModified));
                titanIndex++;
            }
            updatedMembers.add(new GuildMember(member.id(), member.name(), updatedHeroTeams, updatedTitanTeams));
        }
        return new Guild(currentGuild.id(), currentGuild.name(), updatedMembers,
                currentGuild.season(), currentGuild.seasonStart());
    }


    public void refreshFromContext() {
        refreshTables();
    }


    private void refreshTables() {
        Guild currentGuild = appContext.guild();
        Lineup currentLineup = appContext.lineup();
        java.util.List<TeamOverviewTableModel.Row<Hero>> heroRows = new ArrayList<>();
        java.util.List<TeamOverviewTableModel.Row<Titan>> titanRows = new ArrayList<>();
        for (GuildMember member : currentGuild.members()) {
            String memberLabel = memberLabel(member);

            java.util.List<HeroTeam> heroTeams = member.heroTeams();
            for (int i = 0; i < heroTeams.size(); i++) {
                HeroTeam team = heroTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.HERO, i);
                heroRows.add(new TeamOverviewTableModel.Row<>(memberLabel, team.heroes(), team.totalPower(),
                        member.id(), Lineup.TeamType.HERO, i, assigned));
            }

            java.util.List<TitanTeam> titanTeams = member.titanTeams();
            for (int i = 0; i < titanTeams.size(); i++) {
                TitanTeam team = titanTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.TITAN, i);
                titanRows.add(new TeamOverviewTableModel.Row<>(memberLabel, team.titans(), team.totalPower(),
                        member.id(), Lineup.TeamType.TITAN, i, assigned));
            }
        }
        heroModel.setRows(heroRows);
        titanModel.setRows(titanRows);
        GuiUtils.editedGuild = false;
    }

    /**
     * Looks up the fortification the given team is currently assigned to in
     * lineup (matched by teamMemberId + teamType + teamIndex, see
     * {@link Lineup.Entry}), resolved to the catalog's {@link Fortification}
     * object via {@link FortificationRepository#findById} - null if the team
     * has no entry in lineup, or its entry points at an id no longer in the
     * catalog.
     */
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
     * Table model: one row per team. Column 0 ("Power", editable since
     * 2026-09-04) and column 3 ("Fortification") are editable (Integer -&gt;
     * JTable's own default numeric cell editor, no explicit editor set in
     * {@link #configureTable}, unlike column 3; Fortification -&gt;
     * JComboBox via the cell editor set in {@link #configureTable}).
     * members (column 2) carries the raw member list (Hero or Titan) - the
     * display is handled by {@link MembersCellRenderer}. Column 0 is
     * rendered formatted via {@link PowerCellRenderer}
     * ({@link GuiUtils#NUMBER_FORMAT}) while not being edited.
     */
    static final class TeamOverviewTableModel<T> extends AbstractTableModel {

        /** Language file key (see {@link #COLUMN_KEY_HEROES}/{@link #COLUMN_KEY_TITANS}) for the members column header. */
        private final String membersColumnKey;

        private final BiFunction<Row<T>, Fortification, Boolean> onFortificationSelected;
        private final BiFunction<Row<T>, Integer, Boolean> onPowerEdited;


        private final Runnable onGuildEdited;
        private java.util.List<Row<T>> rows = new ArrayList<>();

        TeamOverviewTableModel(String membersColumnKey, BiFunction<Row<T>, Fortification, Boolean> onFortificationSelected,
                               BiFunction<Row<T>, Integer, Boolean> onPowerEdited, Runnable onGuildEdited) {
            this.membersColumnKey = membersColumnKey;
            this.onFortificationSelected = onFortificationSelected;
            this.onPowerEdited = onPowerEdited;
            this.onGuildEdited = onGuildEdited;
        }

        void setRows(java.util.List<Row<T>> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        java.util.List<Row<T>> rows() {
            return rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
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
            return switch (column) {
                case 0 -> Integer.class;
                case 2 -> java.util.List.class;
                case 3 -> Fortification.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int column) {
            return column == 0 || column == 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            Row<T> row = rows.get(rowIndex);
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
            if (column == 0) {
                Row<T> row = rows.get(rowIndex);
                int newPower = (Integer) value;
                boolean accepted = onPowerEdited == null || onPowerEdited.apply(row, newPower);
                if (accepted) {
                    row.totalPower = newPower;
                    if (onGuildEdited != null) {
                        onGuildEdited.run();
                    }
                }
                fireTableCellUpdated(rowIndex, column);
            } else if (column == 3) {
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
         * One table row = one team. totalPower starts at the team's total
         * power as shown in-game (see {@link HeroTeam#totalPower()}/
         * {@link TitanTeam#totalPower()}) but, since 2026-09-04, is no
         * longer unchangeable - a successful "Power" column edit (see
         * {@link TeamsOverviewPanel#handlePowerEdited}) updates it, and it
         * is written back into the guild's team the same way on Save (see
         * {@link TeamsOverviewPanel#guildWithCurrentSelection}); unlike
         * totalPower, memberLabel/members are still genuinely unchangeable
         * (no column edits either of them). teamMemberId/teamType/teamIndex
         * identify the underlying team exactly like {@link Lineup.Entry}
         * does (see {@link GuildMember#teamLabel(int)}) - needed so a
         * "Fortification" combo box selection can be turned into a
         * {@link Lineup.Entry} for the currently open lineup (see
         * {@link TeamsOverviewPanel#handleFortificationSelected}).
         * assignedFortification starts at whatever the lineup already has
         * for this team (see {@link TeamsOverviewPanel#findAssignedFortification})
         * and is only ever changed via a successful pick in that column -
         * unlike totalPower, it is NOT part of the guild's own saved state.
         */
        static final class Row<T> {
            final String memberLabel;
            final java.util.List<T> members;
            int totalPower;
            final String teamMemberId;
            final Lineup.TeamType teamType;
            final int teamIndex;
            Fortification assignedFortification;

            Row(String memberLabel, java.util.List<T> members, int totalPower,
                String teamMemberId, Lineup.TeamType teamType, int teamIndex, Fortification assignedFortification) {
                this.memberLabel = memberLabel;
                this.members = members;
                this.totalPower = totalPower;
                this.teamMemberId = teamMemberId;
                this.teamType = teamType;
                this.teamIndex = teamIndex;
                this.assignedFortification = assignedFortification;
            }
        }
    }

    /**
     * Renders the team members column as several JLabels side by side in a
     * FlowLayout panel: one icon per member if iconResolver supplies one
     * (heroes/titans via {@link IconLoader}, each
     * {@value TeamsOverviewPanel#MEMBER_ICON_SIZE}x{@value TeamsOverviewPanel#MEMBER_ICON_SIZE}) -
     * otherwise a text fallback with the display name, in case no
     * iconResolver should ever be supplied for a type in the future.
     */
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
            java.util.List<T> members = (java.util.List<T>) value;
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
}

