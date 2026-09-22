package org.c2w.gui.hero;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.GuildRepository;
import org.c2w.gui.ReportViewerDialog;
import org.c2w.gui.titan.TitanValueOverviewDialog;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.util.AppContext;
import org.c2w.util.BuffCalculationService;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;
import org.c2w.util.TeamScoreCalculator;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * HERO counterpart of {@link TitanValueOverviewDialog} - one row per hero
 * team, same editable "Fortification" assignment column, same per-fortification
 * extra columns whose displayed value (score or role-match count) is toggled
 * via {@link ValueMode} (see {@link #buildValueModeCombo()}). Split
 * 2026-09-20 out of the former (now removed) {@code TeamsValueOverviewDialog},
 * which held both hero and titan tables behind a {@link JTabbedPane} - each
 * team type now gets its own toolbar button/dialog instead of a tab (see
 * {@code ToolbarPanel}). Deliberately a near-duplicate of
 * {@link TitanValueOverviewDialog}, matching this codebase's own "redundant
 * copy" convention (see e.g. the removed {@code AllTeamsOverviewDialog}'s
 * cell renderer Javadocs) rather than sharing code across two otherwise-
 * independent dialogs.
 *
 * <p>Column 0 ("Power") is editable, the same way {@code TeamsOverviewPanel}'s
 * own table makes it editable (see {@link HeroValueTableModel#isCellEditable}) -
 * unlike that panel, this dialog has no toolbar of its own to host a "save
 * guild" button, so {@link #buildSaveButton()} puts an equivalent
 * {@link FlatButton} directly in the dialog's top panel instead, ahead of the
 * {@link ValueMode} combo box (see {@link #saveGuild()}).
 */
public class HeroValueOverviewDialog extends JDialog {

    /** Dialog title - hardcoded, not localized (matches {@link ReportViewerDialog}). */
    private static final String BASE_TITLE = "Cow2 - Hero Teams";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for "no fortification assigned". */
    private static final String KEY_NO_FORTIFICATION = "common.none";

    /** Target size of the member icons (Heroes column). */
    private static final int MEMBER_ICON_SIZE = 24;

    /** Row height matching {@link #MEMBER_ICON_SIZE} (24px icon + 6px margin). */
    private static final int ROW_HEIGHT = MEMBER_ICON_SIZE + 6;

    /** Preferred width of each per-fortification value column (see class Javadoc). */
    private static final int VALUE_COLUMN_WIDTH = 70;

    /** Size of {@link #buildSaveButton()}'s icon - matches {@code ToolbarPanel}'s own TOOLBAR_ICON_SIZE. */
    private static final int SAVE_ICON_SIZE = 20;

    /**
     * Language file keys (see {@code resources/language/<name>/<name>.properties}) for the table
     * column headers - the SAME keys {@link TitanValueOverviewDialog}
     * uses (the German/English/French text is identical, only the Java
     * constants/classes here are a redundant copy, not the resource keys).
     */
    private static final String COLUMN_KEY_POWER = "teamsOverview.power";
    private static final String COLUMN_KEY_MEMBER = "teamsOverview.member";
    private static final String COLUMN_KEY_HEROES = "teamsOverview.heroes";
    private static final String COLUMN_KEY_FORTIFICATION = "teamsOverview.fortification";

    /** Language file key for the shared "no buff" value column header - see {@link ValueColumn}. */
    private static final String COLUMN_KEY_NO_BUFF = "teamsOverview.noBuff";

    /** Language file key for the label in front of {@link #buildValueModeCombo()}. */
    private static final String KEY_VALUE_MODE_LABEL = "teamsOverview.valueMode";

    /** Language file key (see {@code TeamsOverviewPanel}/{@code ToolbarPanel}'s own "save guild" button) for {@link #buildSaveButton()}'s tooltip - reused since this button does the exact same thing. */
    private static final String KEY_SAVE_GUILD = "teamsOverview.saveGuild";

    /** Classpath path of {@link #buildSaveButton()}'s icon (see {@link IconLoader}) - same file {@code ToolbarPanel}'s own "save guild" button uses. */
    private static final String ICON_SAVE_GUILD = "/images/app/save.png";

    /** What the per-fortification value columns show - toggled via {@link #buildValueModeCombo()}. */
    enum ValueMode {
        /** {@link TeamScoreCalculator} total score. */
        COW_SCORE("teamsOverview.valueMode.cowScore"),
        /** Number of heroes with the fortification's required role. */
        ROLE_MATCH_COUNT("teamsOverview.valueMode.roleMatchCount");

        private final String labelKey;

        ValueMode(String labelKey) {
            this.labelKey = labelKey;
        }

        String displayName() {
            return LanguageService.displayName(labelKey);
        }
    }

    private final AppContext appContext;

    private final FortificationMapPanel fortificationMapPanel;

    private final List<Fortification> heroFortifications = sortedFortifications(FortificationType.HERO);

    /** This dialog's own value-table columns - one per buffed HERO fortification plus one shared "no buff" column (see {@link ValueColumn}/class Javadoc). */
    private final List<ValueColumn> heroValueColumns = buildValueColumns(heroFortifications);

    /** Currently selected value mode (see {@link #buildValueModeCombo()}). */
    private ValueMode valueMode = ValueMode.COW_SCORE;

    private final HeroValueTableModel heroModel =
            new HeroValueTableModel(COLUMN_KEY_HEROES, heroValueColumns, this::handleFortificationSelected,
                    this::handlePowerEdited, () -> valueMode);
    private final JTable heroTable = new JTable(heroModel);

    public HeroValueOverviewDialog(Frame owner, AppContext appContext,
                                   FortificationMapPanel fortificationMapPanel) {
        super(owner, BASE_TITLE, false);
        if (appContext == null) {
            throw new IllegalArgumentException("HeroValueOverviewDialog needs a guildContext");
        }
        if (fortificationMapPanel == null) {
            throw new IllegalArgumentException("HeroValueOverviewDialog needs a fortificationMapPanel");
        }
        this.appContext = appContext;
        this.fortificationMapPanel = fortificationMapPanel;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JComboBox<Fortification> heroFortificationCombo = buildFortificationCombo(heroFortifications);
        configureTable(heroTable, h -> IconLoader.iconFor(h.imagePath(), MEMBER_ICON_SIZE),
                h -> LanguageService.displayName(h.id()), heroFortificationCombo);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topPanel.add(buildSaveButton());
        topPanel.add(new JLabel(LanguageService.displayName(KEY_VALUE_MODE_LABEL)));
        topPanel.add(buildValueModeCombo());

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(topPanel, BorderLayout.NORTH);
        content.add(new JScrollPane(heroTable), BorderLayout.CENTER);
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);

        refreshTable();

        setSize(900, 580);
        setLocationRelativeTo(owner);
    }

    /**
     * The {@link FlatButton} that persists the currently displayed Power
     * values (see {@link HeroValueTableModel#isCellEditable}) to the guild
     * file - {@link #saveGuild()}, triggered by this button, is otherwise
     * the exact counterpart of {@code TeamsOverviewPanel#saveGuild()}/
     * {@code ToolbarPanel}'s own "save guild" button, reusing the same
     * tooltip key/icon since it does the same thing.
     */
    private FlatButton buildSaveButton() {
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_GUILD, SAVE_ICON_SIZE, IconLoader.BLUE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_GUILD));
        saveButton.addActionListener(e -> saveGuild());
        return saveButton;
    }

    /**
     * The combo box that switches {@link #valueMode} - only the
     * per-fortification value columns' rendering changes (see
     * {@link ValueCellRenderer}); the underlying data for both modes is
     * always computed together in {@link #refreshTable()}/kept in sync on
     * every Power edit (see {@link #handlePowerEdited}), so switching is an
     * immediate repaint, no recomputation.
     */
    private JComboBox<ValueMode> buildValueModeCombo() {
        JComboBox<ValueMode> combo = new JComboBox<>(ValueMode.values());
        combo.setSelectedItem(valueMode);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(((ValueMode) value).displayName());
                return this;
            }
        });
        combo.addActionListener(e -> {
            valueMode = (ValueMode) combo.getSelectedItem();
            heroModel.fireValuesChanged();
        });
        return combo;
    }

    private static List<Fortification> sortedFortifications(FortificationType type) {
        return FortificationRepository.findAll().stream()
                .filter(f -> f.type() == type)
                .sorted(Comparator.comparing(f -> LanguageService.displayName(f.id())))
                .toList();
    }

    /**
     * Turns {@code fortifications} (already sorted) into this dialog's value
     * columns: every buffed fortification keeps its own column (same order),
     * and every buff-less fortification is folded into a single shared
     * column appended at the end - see {@link ValueColumn}/class Javadoc.
     * Returns only the buffed columns, with no shared column appended, if
     * {@code fortifications} has no buff-less entry at all.
     */
    private static List<ValueColumn> buildValueColumns(List<Fortification> fortifications) {
        List<ValueColumn> columns = new ArrayList<>();
        Fortification firstBuffLess = null;
        for (Fortification fortification : fortifications) {
            if (fortification.buff() != null) {
                columns.add(ValueColumn.forBuffedFortification(fortification));
            } else if (firstBuffLess == null) {
                firstBuffLess = fortification;
            }
        }
        if (firstBuffLess != null) {
            columns.add(ValueColumn.sharedNoBuff(firstBuffLess));
        }
        return List.copyOf(columns);
    }

    private static void configureTable(JTable table, Function<Hero, Icon> iconResolver, Function<Hero, String> nameResolver,
                                       JComboBox<Fortification> fortificationCombo) {
        table.setRowHeight(ROW_HEIGHT);
        table.setForeground(FortificationType.HERO.getColor());
        table.setSelectionForeground(table.getForeground());
        table.setGridColor(Color.BLACK);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(0).setCellRenderer(new PowerCellRenderer());
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setCellRenderer(new MembersCellRenderer(iconResolver, nameResolver));
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setCellRenderer(new FortificationCellRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(fortificationCombo));
        for (int col = HeroValueTableModel.FIXED_COLUMN_COUNT; col < table.getColumnModel().getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(VALUE_COLUMN_WIDTH);
            table.getColumnModel().getColumn(col).setCellRenderer(new ValueCellRenderer());
        }

        // Column-header sorting (see TeamsOverviewPanel#configureTable for
        // the full rationale) - the per-fortification value columns are
        // always exposed as Double (see HeroValueTableModel#getColumnClass),
        // so the default sorter already compares them correctly without a
        // custom comparator, same as column 0 (Power).
        table.setAutoCreateRowSorter(true);
        if (table.getRowSorter() instanceof TableRowSorter<?> rowSorter) {
            @SuppressWarnings("unchecked")
            TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) rowSorter;
            sorter.setComparator(2, Comparator.<List<Hero>, String>comparing(
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

    private boolean handleFortificationSelected(HeroValueTableModel.Row row, Fortification fortification) {
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
            updatedEntries.add(new Lineup.Entry(fortification.id(), row.teamMemberId, row.teamType, row.teamIndex));
        }
        Lineup updatedLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                currentLineup.algorithmName(), currentLineup.createdAt(), updatedEntries);
        appContext.setLineup(updatedLineup);
        GuiUtils.editedLineup = true;
        fortificationMapPanel.refresh(updatedLineup);
        return true;
    }

    /**
     * Validates a "Power" column edit (same rule as {@code TeamsOverviewPanel#handlePowerEdited}:
     * negative power is rejected) and, if accepted, recomputes {@code row}'s
     * {@link HeroValueTableModel.Row#matchCounts}/{@link HeroValueTableModel.Row#scores}
     * for {@code newPower} (both otherwise-static value columns are derived
     * from Power, see {@link #matchCountsFor}/{@link #scoresFor}) and marks
     * the guild dirty, so {@link #buildSaveButton()}'s save (and the "unsaved
     * changes" prompt in {@code ToolbarPanel#confirmDiscardUnsavedChanges})
     * pick it up exactly like a {@code TeamsOverviewPanel} Power edit would.
     */
    private boolean handlePowerEdited(HeroValueTableModel.Row row, int newPower) {
        if (newPower < 0) {
            JOptionPane.showMessageDialog(this, "Power must not be negative.",
                    "Invalid power", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Guild currentGuild = appContext.guild();
        Lineup currentLineup = appContext.lineup();
        row.matchCounts = matchCountsFor(heroValueColumns, row.teamMemberId, row.teamType, row.teamIndex,
                currentGuild, currentLineup);
        HeroTeam syntheticTeam = new HeroTeam(row.teamMemberId, row.teamIndex, row.members, newPower, LocalDate.now());
        row.scores = scoresFor(syntheticTeam, heroValueColumns);
        GuiUtils.editedGuild = true;
        return true;
    }

    /**
     * Persists the currently displayed Power values to the guild file - the
     * counterpart of {@code TeamsOverviewPanel#saveGuild()}, scoped to hero
     * teams only (see {@link #guildWithCurrentSelection()}); triggered by
     * {@link #buildSaveButton()}.
     */
    private void saveGuild() {
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

    /**
     * {@code appContext.guild()} with every hero team's totalPower replaced
     * by what {@link #heroModel} currently shows - titan teams are copied
     * over unchanged, since this dialog has no data for them at all (see
     * {@link TitanValueOverviewDialog} for the titan counterpart of this
     * save).
     */
    private Guild guildWithCurrentSelection() {
        Guild currentGuild = appContext.guild();
        List<HeroValueTableModel.Row> heroRows = heroModel.rows();
        int heroIndex = 0;
        List<GuildMember> updatedMembers = new ArrayList<>();
        for (GuildMember member : currentGuild.members()) {
            List<HeroTeam> updatedHeroTeams = new ArrayList<>();
            for (HeroTeam team : member.heroTeams()) {
                HeroValueTableModel.Row row = heroRows.get(heroIndex);
                LocalDate lastModified = row.totalPower != team.totalPower() ? LocalDate.now() : team.lastModified();
                updatedHeroTeams.add(new HeroTeam(team.memberId(), team.index(), team.heroes(), row.totalPower, lastModified));
                heroIndex++;
            }
            updatedMembers.add(new GuildMember(member.id(), member.name(), updatedHeroTeams, member.titanTeams()));
        }
        return new Guild(currentGuild.id(), currentGuild.name(), updatedMembers,
                currentGuild.season(), currentGuild.seasonStart());
    }

    private void refreshTable() {
        Guild currentGuild = appContext.guild();
        Lineup currentLineup = appContext.lineup();
        List<HeroValueTableModel.Row> heroRows = new ArrayList<>();
        for (GuildMember member : currentGuild.members()) {
            String memberLabel = memberLabel(member);

            List<HeroTeam> heroTeams = member.heroTeams();
            for (int i = 0; i < heroTeams.size(); i++) {
                HeroTeam team = heroTeams.get(i);
                Fortification assigned = findAssignedFortification(currentLineup, member.id(), Lineup.TeamType.HERO, i);
                int[] matchCounts = matchCountsFor(heroValueColumns, member.id(), Lineup.TeamType.HERO, i,
                        currentGuild, currentLineup);
                double[] scores = scoresFor(team, heroValueColumns);
                heroRows.add(new HeroValueTableModel.Row(memberLabel, team.heroes(), team.totalPower(),
                        member.id(), Lineup.TeamType.HERO, i, assigned, matchCounts, scores));
            }
        }
        heroModel.setRows(heroRows);
    }

    /**
     * {@code ROLE_MATCH_COUNT} data: the number of heroes in a synthetic
     * single-entry lineup (this team assigned to each column's fortification
     * in turn) that match that fortification's buff role - see
     * {@link BuffCalculationService#countMatchingMembersForFortification}.
     * For the shared "no buff" column (see {@link ValueColumn}) this always
     * yields 0 ({@code buff() == null}), which is the correct "no role to
     * match" value.
     */
    private static int[] matchCountsFor(List<ValueColumn> valueColumns, String teamMemberId, Lineup.TeamType teamType,
                                        int teamIndex, Guild guild, Lineup currentLineup) {
        int[] counts = new int[valueColumns.size()];
        for (int i = 0; i < valueColumns.size(); i++) {
            Fortification fortification = valueColumns.get(i).representative();
            Lineup.Entry syntheticEntry = new Lineup.Entry(fortification.id(), teamMemberId, teamType, teamIndex);
            Lineup syntheticLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                    currentLineup.algorithmName(), currentLineup.createdAt(), List.of(syntheticEntry));
            counts[i] = BuffCalculationService.countMatchingMembersForFortification(
                    fortification.id(), syntheticLineup, guild, fortification.buff());
        }
        return counts;
    }

    /**
     * {@code COW_SCORE} data: {@code team}'s {@link TeamScoreCalculator}
     * score against every entry of {@code valueColumns}, same order - for
     * the shared "no buff" column (see {@link ValueColumn}), this uses that
     * column's representative buff-less fortification, which yields the
     * same result as any other buff-less fortification would (see class
     * Javadoc).
     */
    private static double[] scoresFor(HeroTeam team, List<ValueColumn> valueColumns) {
        double[] scores = new double[valueColumns.size()];
        for (int i = 0; i < valueColumns.size(); i++) {
            scores[i] = TeamScoreCalculator.scoreFor(team, valueColumns.get(i).representative()).total();
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
     * One row per hero team. Column 0 ("Power") and column 3
     * ("Fortification") are editable (see {@link #isCellEditable}) - unlike
     * the former (merged) dialog, Power is no longer read-only here (see
     * class Javadoc): a successful edit (see {@link HeroValueOverviewDialog#handlePowerEdited})
     * updates {@link Row#totalPower} and its derived
     * {@link Row#matchCounts}/{@link Row#scores} together, and is only
     * written back to disk on an explicit {@link HeroValueOverviewDialog#saveGuild()}.
     * Every entry of {@code valueColumns} contributes one always-read-only
     * column, whose displayed value depends on the dialog's current
     * {@link ValueMode} (see {@link #mode()}) - both {@link Row#matchCounts}/
     * {@link Row#scores} are always kept in sync, so switching
     * {@link ValueMode} never needs new data, only a repaint (see
     * {@link #fireValuesChanged()}).
     */
    static final class HeroValueTableModel extends AbstractTableModel {

        /** Number of fixed columns before the per-fortification value columns start (Power, Member, Heroes, Fortification). */
        static final int FIXED_COLUMN_COUNT = 4;

        private final String membersColumnKey;

        /** This table's own value columns, in display order - see {@link HeroValueOverviewDialog#scoresFor}/{@link HeroValueOverviewDialog#matchCountsFor}. */
        private final List<ValueColumn> valueColumns;

        /**
         * Called from {@link #setValueAt} after a "Fortification" combo box
         * selection (column 3) was committed for a row - see
         * {@link HeroValueOverviewDialog#handleFortificationSelected}.
         * Returns whether the pick is accepted; only then is the row's own
         * assignedFortification field updated.
         */
        private final BiFunction<Row, Fortification, Boolean> onFortificationSelected;

        /**
         * Called from {@link #setValueAt} after a "Power" edit (column 0)
         * was committed for a row - see {@link HeroValueOverviewDialog#handlePowerEdited}.
         * Returns whether the new value is accepted; only then is the row's
         * own totalPower (and its derived matchCounts/scores, updated by the
         * callback itself) applied.
         */
        private final BiFunction<Row, Integer, Boolean> onPowerEdited;

        /** Read on every {@link #getValueAt} call for a value column - see {@link HeroValueOverviewDialog#valueMode}. */
        private final Supplier<ValueMode> modeSupplier;

        private List<Row> rows = new ArrayList<>();

        HeroValueTableModel(String membersColumnKey, List<ValueColumn> valueColumns,
                           BiFunction<Row, Fortification, Boolean> onFortificationSelected,
                           BiFunction<Row, Integer, Boolean> onPowerEdited,
                           Supplier<ValueMode> modeSupplier) {
            this.membersColumnKey = membersColumnKey;
            this.valueColumns = valueColumns;
            this.onFortificationSelected = onFortificationSelected;
            this.onPowerEdited = onPowerEdited;
            this.modeSupplier = modeSupplier;
        }

        void setRows(List<Row> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        List<Row> rows() {
            return rows;
        }

        /** This table's own value columns, in display order - see {@link ValueCellRenderer}. */
        List<ValueColumn> valueColumns() {
            return valueColumns;
        }

        /** The dialog's currently selected {@link ValueMode} - see {@link ValueCellRenderer}. */
        ValueMode mode() {
            return modeSupplier.get();
        }

        /** Repaints every value cell with the newly selected {@link ValueMode} - no row data changed, only what {@link #getValueAt} returns for the value columns. */
        void fireValuesChanged() {
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return FIXED_COLUMN_COUNT + valueColumns.size();
        }

        @Override
        public String getColumnName(int column) {
            if (column >= FIXED_COLUMN_COUNT) {
                return valueColumns.get(column - FIXED_COLUMN_COUNT).headerText();
            }
            return switch (column) {
                case 0 -> LanguageService.displayName(COLUMN_KEY_POWER);
                case 1 -> LanguageService.displayName(COLUMN_KEY_MEMBER);
                case 2 -> LanguageService.displayName(membersColumnKey);
                case 3 -> LanguageService.displayName(COLUMN_KEY_FORTIFICATION);
                default -> throw new IllegalArgumentException("Unknown column: " + column);
            };
        }

        /**
         * Every value column is exposed as {@code Double} regardless of
         * {@link ValueMode} - {@link #getValueAt} always boxes the
         * mode-appropriate number (score or match count) as a {@code double}
         * - so the row sorter's comparator (see {@link HeroValueOverviewDialog#configureTable})
         * stays valid across a {@link ValueMode} switch instead of throwing
         * a {@link ClassCastException} on a stale {@code Integer}/{@code Double} mix.
         */
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

        /**
         * Column 0 ("Power", editable since this dialog's Power/Save split -
         * see class Javadoc) and column 3 ("Fortification") are editable,
         * exactly like {@code TeamsOverviewPanel.TeamOverviewTableModel}'s
         * own table - every value column stays read-only.
         */
        @Override
        public boolean isCellEditable(int rowIndex, int column) {
            return column == 0 || column == 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            Row row = rows.get(rowIndex);
            if (column >= FIXED_COLUMN_COUNT) {
                int valueIndex = column - FIXED_COLUMN_COUNT;
                return mode() == ValueMode.COW_SCORE ? row.scores[valueIndex] : (double) row.matchCounts[valueIndex];
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
            if (column == 0) {
                Row row = rows.get(rowIndex);
                int newPower = (Integer) value;
                boolean accepted = onPowerEdited == null || onPowerEdited.apply(row, newPower);
                if (accepted) {
                    row.totalPower = newPower;
                }
                // Fires the whole row, not just column 0 - a successful edit
                // also updates row.matchCounts/row.scores (see onPowerEdited),
                // so every value column needs repainting too.
                fireTableRowsUpdated(rowIndex, rowIndex);
            } else if (column == 3) {
                Row row = rows.get(rowIndex);
                Fortification fortification = (Fortification) value;
                boolean accepted = onFortificationSelected == null || onFortificationSelected.apply(row, fortification);
                if (accepted) {
                    row.assignedFortification = fortification;
                }
                fireTableCellUpdated(rowIndex, column);
            }
        }

        /**
         * One table row = one hero team. totalPower starts at the team's
         * total power as shown in-game but, like {@code TeamsOverviewPanel.TeamOverviewTableModel.Row}'s
         * own totalPower, is no longer unchangeable - a successful "Power"
         * column edit (see {@link HeroValueOverviewDialog#handlePowerEdited})
         * updates it (and, together with it, matchCounts/scores - both
         * derived from totalPower, see {@link HeroValueOverviewDialog#matchCountsFor}/
         * {@link HeroValueOverviewDialog#scoresFor}), and it is written back
         * into the guild's team the same way on Save (see
         * {@link HeroValueOverviewDialog#guildWithCurrentSelection()}).
         */
        static final class Row {
            final String memberLabel;
            final List<Hero> members;
            int totalPower;
            final String teamMemberId;
            final Lineup.TeamType teamType;
            final int teamIndex;
            Fortification assignedFortification;
            int[] matchCounts;
            double[] scores;

            Row(String memberLabel, List<Hero> members, int totalPower,
                String teamMemberId, Lineup.TeamType teamType, int teamIndex, Fortification assignedFortification,
                int[] matchCounts, double[] scores) {
                this.memberLabel = memberLabel;
                this.members = members;
                this.totalPower = totalPower;
                this.teamMemberId = teamMemberId;
                this.teamType = teamType;
                this.teamIndex = teamIndex;
                this.assignedFortification = assignedFortification;
                this.matchCounts = matchCounts;
                this.scores = scores;
            }
        }
    }

    static final class MembersCellRenderer implements TableCellRenderer {

        private final Function<Hero, Icon> iconResolver;
        private final Function<Hero, String> nameResolver;

        MembersCellRenderer(Function<Hero, Icon> iconResolver, Function<Hero, String> nameResolver) {
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
            List<Hero> members = (List<Hero>) value;
            if (members != null) {
                for (Hero member : members) {
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
            label.setForeground(FortificationType.HERO.getColor());
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
            label.setForeground(FortificationType.HERO.getColor());
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return label;
        }
    }

    /**
     * Renders one value cell - "12.5" in {@link ValueMode#COW_SCORE}, "3" in
     * {@link ValueMode#ROLE_MATCH_COUNT} (see {@link HeroValueTableModel#mode()}) -
     * same light-green highlight for a row's currently assigned
     * fortification's own column regardless of mode (for the shared "no
     * buff" column, see {@link ValueColumn}, that means ANY buff-less
     * assignment, not one specific fortification).
     */
    static final class ValueCellRenderer implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            double numericValue = (Double) value;
            HeroValueTableModel model = (HeroValueTableModel) table.getModel();
            String text = model.mode() == ValueMode.COW_SCORE
                    ? String.format(Locale.ROOT, "%.1f", numericValue)
                    : String.valueOf(Math.round(numericValue));
            JLabel label = new JLabel(text, JLabel.CENTER);
            label.setOpaque(true);
            label.setForeground(FortificationType.HERO.getColor());
            label.setBackground(backgroundFor(table, isSelected, row, column));
            return label;
        }

        private static Color backgroundFor(JTable table, boolean isSelected, int row, int column) {
            if (isSelected) {
                return table.getSelectionBackground();
            }
            HeroValueTableModel model = (HeroValueTableModel) table.getModel();
            int modelRow = table.convertRowIndexToModel(row);
            int modelColumn = table.convertColumnIndexToModel(column);
            ValueColumn valueColumn = model.valueColumns().get(modelColumn - HeroValueTableModel.FIXED_COLUMN_COUNT);
            Fortification assignedFortification = model.rows().get(modelRow).assignedFortification;
            return valueColumn.matchesAssignment(assignedFortification) ? GuiUtils.VALUE_HIGHLIGHT_BACKGROUND : table.getBackground();
        }
    }

    /**
     * One value-table column (see class Javadoc): either one specific
     * BUFFED fortification, or the single column shared by every buff-less
     * fortification of this type. A buff-less fortification's
     * {@link TeamScoreCalculator} score always uses the generalScore-based
     * branch, and its role-match count is always 0 - never that specific
     * fortification's (non-existent) buff - so both are numerically
     * identical no matter which buff-less fortification is actually
     * assigned; {@link #representative()} is therefore only ever used to
     * drive that shared calculation, never to tell two buff-less
     * fortifications apart.
     */
    static final class ValueColumn {

        private final Fortification representative;
        private final boolean sharedNoBuffColumn;

        private ValueColumn(Fortification representative, boolean sharedNoBuffColumn) {
            this.representative = representative;
            this.sharedNoBuffColumn = sharedNoBuffColumn;
        }

        /** One column for exactly this (buffed) fortification. */
        static ValueColumn forBuffedFortification(Fortification fortification) {
            return new ValueColumn(fortification, false);
        }

        /**
         * The single shared "no buff" column - {@code anyBuffLessFortification}
         * is an arbitrary representative (any buff-less fortification of
         * this type does equally well, see class Javadoc) used only to
         * compute the (shared) value, never shown or compared by identity.
         */
        static ValueColumn sharedNoBuff(Fortification anyBuffLessFortification) {
            return new ValueColumn(anyBuffLessFortification, true);
        }

        /** The fortification to compute the value against for this column - see class Javadoc. */
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
