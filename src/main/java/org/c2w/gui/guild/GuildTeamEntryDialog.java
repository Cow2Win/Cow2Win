package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortComboBox;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.BuffTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.LineupService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

/**
 * Guild-wide entry dialog for exactly ONE team type (hero or titan - see
 * {@link GuildHeroEntryDialog}/{@link GuildTitanEntryDialog}, the only two
 * subclasses, and {@link TeamTypeSpec}): lets the player build/edit that
 * type's teams for the whole guild in one place, instead of one
 * fortification at a time like {@link org.c2w.gui.fort.FortificationEntryDialog}.
 *
 * <p>Layout: every team is ONE line of a sortable/filterable {@link JTable}
 * (rendered only - no combo boxes per line, so it stays fast with many
 * teams), and a single editor area below the table edits whichever line is
 * selected: fortification, member (typing an unknown name creates the member,
 * see {@link MemberComboEditor}) and one {@link TeamEditorPanel} incl. war
 * flag/pet and the F1-F5 templates. A line may have no fortification - the
 * team is then saved into the member without a {@link Lineup.Entry}.
 *
 * <ul>
 *     <li>Adding a line does not save; unsaved changes are shown in the
 *     status bar and the player is asked on close.</li>
 *     <li>Shortcuts: Ctrl+N adds, Del (in the table) deletes, Ctrl+S saves,
 *     Enter (in the table) jumps into the editor, F1-F5 (in the table) loads
 *     a template into the selected team.</li>
 *     <li>Problems that would make the save fail (no member, no power, too many teams
 *     per member) and overfull fortifications are highlighted in the table
 *     right away; a failed save selects the offending line.</li>
 * </ul>
 *
 * <p>On save, every line is resolved to a (member, teamIndex) slot and the
 * {@link Lineup.Entry} list for {@link #spec}'s {@link Lineup.TeamType} is
 * rebuilt from scratch from the current lines - safe because the lines were
 * seeded with one line per pre-existing entry of that type, so they are
 * always a complete picture of that type's assignments. Entries of the OTHER
 * team type are carried over unchanged - see {@link #performSave}.
 */
abstract class GuildTeamEntryDialog<T> extends JDialog {

    /** Mirrors {@code GuildEditorDialog#MAX_MEMBERS} - enforced the same way when {@link MemberComboEditor} creates a new member inline. */
    private static final int MAX_MEMBERS = 30;

    /** Language file keys (see {@code resources/language/<name>/<name>.properties}). */
    private static final String KEY_NO_SELECTION = "common.none";
    private static final String KEY_SAVE_TEAMS = "guildEntry.saveTeams";
    private static final String KEY_ADD_ROW = "guildEntry.addRow";
    private static final String KEY_DELETE_ROW = "guildEntry.deleteTeamTitle";
    private static final String KEY_WAR_FLAG = "teamEditor.warFlag";
    private static final String KEY_PET = "teamEditor.pet";
    private static final String KEY_TOTEMS = "teamEditor.totems";
    private static final String KEY_COLUMN_FORTIFICATION = "guildEntry.column.fortification";
    private static final String KEY_COLUMN_MEMBER = "guildEntry.column.member";
    private static final String KEY_COLUMN_POWER = "guildEntry.column.power";
    private static final String KEY_COLUMN_TEAM = "guildEntry.column.team";
    private static final String KEY_COLUMN_BUFF = "guildEntry.column.buff";
    private static final String KEY_COLUMN_SCORE = "guildEntry.column.score";
    private static final String KEY_FILTER_ALL = "guildEntry.filterAll";
    private static final String KEY_SEARCH = "guildEntry.search";
    private static final String KEY_SEARCH_HINT = "guildEntry.searchHint";
    private static final String KEY_SELECTED_TEAM = "guildEntry.selectedTeam";
    private static final String KEY_NOTHING_SELECTED = "guildEntry.nothingSelected";
    private static final String KEY_STATUS = "guildEntry.status";
    private static final String KEY_UNSAVED = "guildEntry.unsaved";
    private static final String KEY_UNSAVED_QUESTION = "guildEntry.unsavedQuestion";
    private static final String KEY_POWER_MISSING = "guildEntry.powerMissing";
    private static final String KEY_SHORTCUTS = "guildEntry.shortcuts";
    private static final String KEY_SHORTCUT_ADD = "guildEntry.shortcut.add";
    private static final String KEY_SHORTCUT_SAVE = "guildEntry.shortcut.save";
    private static final String KEY_SHORTCUT_DELETE = "guildEntry.shortcut.delete";
    private static final String KEY_SHORTCUT_EDIT = "guildEntry.shortcut.edit";
    private static final String KEY_SHORTCUT_MEMBER = "guildEntry.shortcut.member";
    private static final String KEY_SHORTCUT_LOAD_TEMPLATE = "guildEntry.shortcut.loadTemplate";
    private static final String KEY_SHORTCUT_SAVE_TEMPLATE = "guildEntry.shortcut.saveTemplate";
    private static final String KEY_SHORTCUT_NEXT_FIELD = "guildEntry.shortcut.nextField";
    private static final String KEY_SHORTCUT_TYPE_AHEAD = "guildEntry.shortcut.typeAhead";

    private static final KeyStroke SHORTCUT_ADD = KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke SHORTCUT_SAVE = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke SHORTCUT_DELETE = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);

    private static final String ICON_SAVE = "/images/app/save.png";
    private static final String ICON_ADD = "/images/app/add.png";
    private static final String ICON_DELETE = "/images/app/delete.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    private static final int ICON_SIZE = 32;
    private static final int TABLE_ROW_HEIGHT = ICON_SIZE + 4;
    /** Width of the fortification/member combo boxes in the editor area - wide enough for the longest fortification name. */
    private static final int COMBO_WIDTH = 180;
    private static final int COMBO_HEIGHT = 41;
    private static final int BUFF_LABEL_WIDTH = 64;

    /** Text of the fortification filter's "show only teams without fortification" entry. */
    private static final Object FILTER_ALL = new Object();
    private static final Object FILTER_NONE = new Object();

    /**
     * Stand-in used to score a line that has no fortification - only its
     * {@code buff() == null} matters to {@link TeamScoreCalculator#scoreFor},
     * so an unassigned team simply scores like at a buff-less fortification.
     */
    private static final Fortification UNASSIGNED_FORTIFICATION =
            new Fortification("__unassigned__", FortificationType.HERO, 1, 0, 0, 0, null, List.of(), 0);

    private final AppContext appContext;
    private final Path originalLineupPath;
    private Lineup originalLineup;
    private final GuildDraft draft;
    private final List<Fortification> fortificationCatalog = FortificationRepository.findAll();
    private final TeamTypeSpec<T> spec;
    private final int maxTeams;
    private final boolean withExtras;
    /** Totems (see {@link TitanTeamExtras}) - titan teams only. */
    private final boolean withTotems;

    /** The table's rows, in model order (= lineup order, new rows appended). */
    private final List<Row<T>> rows = new ArrayList<>();

    private final List<Column> columns;
    private final RowTableModel tableModel = new RowTableModel();
    /** Created in the constructor once {@link #columns} and {@link #rows} are set - see there. */
    private final JTable table;
    private final TableRowSorter<TableModel> sorter;

    private final JComboBox<Object> fortFilter = new JComboBox<>();
    private final JTextField searchField = new JTextField(16);
    private final JLabel statusLabel = new JLabel();

    // --- editor area (one for all rows) ---
    private final FortComboBox fortCombo;
    private final JComboBox<MemberDraft> memberCombo = new JComboBox<>();
    private final JLabel buffLabel = new JLabel("", JLabel.CENTER);
    private final JPanel teamEditorHolder = new JPanel(new BorderLayout());
    private final JPanel editorPanel = new JPanel(new BorderLayout());
    private final JLabel nothingSelectedLabel = new JLabel();
    private TeamEditorPanel<T> teamEditor;

    /** The row shown in the editor area, or null. */
    private Row<T> currentRow;

    /** True while the editor area is (re)bound to a row - suppresses the combos' listeners. */
    private boolean binding;

    private boolean dirty;
    private Fortification lastSelectedFortification;

    protected GuildTeamEntryDialog(Frame owner, AppContext appContext, String titleKey,
                                   TeamTypeSpec<T> spec, int maxTeams) {
        super(owner, LanguageService.displayTitle(titleKey), false);
        if (appContext == null) {
            throw new IllegalArgumentException("GuildTeamEntryDialog needs a appContext");
        }
        this.appContext = appContext;
        this.spec = spec;
        this.maxTeams = maxTeams;
        this.withExtras = spec.teamType() == Lineup.TeamType.HERO;
        this.withTotems = spec.teamType() == Lineup.TeamType.TITAN;
        this.columns = buildColumns();

        this.draft = GuildDraftConverter.fromGuild(appContext.guild());
        for (MemberDraft member : draft.members) {
            ensureTeamCount(spec.teamsOf().apply(member), maxTeams);
        }
        this.originalLineupPath = LineupFiles.originalPathFor(appContext.guildFilePath().getParent());
        this.originalLineup = loadOrSeedOriginalLineup();
        this.fortCombo = new FortComboBox(fortificationCatalog, spec.fortificationType());

        loadRows();
        // Only now: the sorter caches the model's row count when it is created.
        this.table = new JTable(tableModel);
        this.sorter = new TableRowSorter<>(tableModel);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildTablePane(), buildEditorArea());
        split.setResizeWeight(1.0);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        bindShortcuts();
        refreshFortFilter();
        updateStatus();

        if (!rows.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        } else {
            showRow(null);
        }

        GuiUtils.sizeToContent(this, 1150, 800);
        setLocationRelativeTo(owner);
    }

    // ------------------------------------------------------------------ data

    private static <T> void ensureTeamCount(List<TeamDraft<T>> teams, int maxCount) {
        while (teams.size() < maxCount) {
            teams.add(new TeamDraft<>());
        }
    }

    /**
     * Loads the guild's fixed "Original" lineup (see {@link LineupFiles}) if
     * it exists, or seeds a fresh one from the currently open lineup otherwise
     * (also if the file is unreadable) - so the existing in-game deployment
     * only needs adjusting on the first pass. Written out on the first save.
     */
    private Lineup loadOrSeedOriginalLineup() {
        if (Files.exists(originalLineupPath)) {
            try {
                return LineupRepository.load(originalLineupPath);
            } catch (IOException e) {
                Logger.logException("Could not load the Original lineup " + originalLineupPath
                        + " - seeding it from the currently open lineup instead", e);
            }
        }
        Lineup current = appContext.lineup();
        return new Lineup(current.guildId(), current.guildName(), "", LocalDateTime.now(), current.entries());
    }

    /** One line per existing lineup entry of this dialog's type, across every fortification; entries whose member/team no longer exists are logged and skipped. */
    private void loadRows() {
        for (Lineup.Entry entry : originalLineup.entries()) {
            if (entry.teamType() != spec.teamType()) {
                continue;
            }
            MemberDraft member = findMemberDraft(entry.teamMemberId());
            List<TeamDraft<T>> teams = member == null ? null : spec.teamsOf().apply(member);
            if (member == null || teams == null || entry.teamIndex() < 0 || entry.teamIndex() >= teams.size()) {
                Logger.log("Could not restore an assigned team in the guild entry dialog - the member or team no longer exists");
                continue;
            }
            Fortification fortification = fortificationCatalog.stream()
                    .filter(f -> f.id().equals(entry.fortificationId()))
                    .findFirst().orElse(null);
            Row<T> row = new Row<>(member, entry.teamIndex());
            row.draft.copyFrom(teams.get(entry.teamIndex()));
            row.member = member;
            row.fortification = fortification;
            rows.add(row);
        }
    }

    private MemberDraft findMemberDraft(String memberId) {
        return draft.members.stream().filter(m -> m.id.equals(memberId)).findFirst().orElse(null);
    }

    private static String memberDisplayName(MemberDraft member) {
        return member.name.isBlank() ? member.id : member.name;
    }

    private List<MemberDraft> sortedMembers() {
        return draft.members.stream()
                .sorted(Comparator.comparing(GuildTeamEntryDialog::memberDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private MemberDraft findMemberByDisplayOrId(String text) {
        return draft.members.stream()
                .filter(m -> memberDisplayName(m).equalsIgnoreCase(text) || m.id.equalsIgnoreCase(text))
                .findFirst().orElse(null);
    }

    private long occupancy(Fortification fortification) {
        return rows.stream().filter(r -> r.fortification == fortification).count();
    }

    private boolean isOverfull(Fortification fortification) {
        return fortification != null && occupancy(fortification) > fortification.capacity();
    }

    /** A filled-in row without a member - would abort the save. */
    private static boolean lacksMember(Row<?> row) {
        return row.member == null && (row.draft.totalPower > 0 || !row.draft.members.isEmpty());
    }

    /** A team with heroes/titans but power 0 - would otherwise be dropped on save without notice, so it aborts the save. */
    private static boolean lacksPower(Row<?> row) {
        return row.draft.totalPower == 0 && !row.draft.members.isEmpty();
    }

    /** More rows for this member than it has team slots - would abort the save. */
    private boolean memberHasTooManyTeams(MemberDraft member) {
        return member != null && rows.stream().filter(r -> r.member == member).count() > maxTeams;
    }

    private TeamScoreCalculator.Breakdown breakdownOf(Row<T> row) {
        return spec.scoreBreakdownOf().apply(row.draft,
                row.fortification != null ? row.fortification : UNASSIGNED_FORTIFICATION);
    }

    private long buffCountOf(Row<T> row) {
        return row.fortification == null ? 0
                : row.draft.members.stream().filter(m -> spec.matchesBuff().apply(row.fortification, m)).count();
    }

    // ----------------------------------------------------------------- table

    private List<Column> buildColumns() {
        List<Column> list = new ArrayList<>(List.of(Column.NUMBER, Column.FORTIFICATION, Column.MEMBER, Column.POWER));
        if (withExtras) {
            list.add(Column.WAR_FLAG);
            list.add(Column.PET);
        }
        if (withTotems) {
            list.add(Column.TOTEMS);
        }
        list.addAll(List.of(Column.TEAM, Column.BUFF, Column.SCORE));
        return list;
    }

    /** The table's columns - war flag/pet only for hero teams, totems only for titan teams, see {@link #buildColumns}. */
    private enum Column {
        NUMBER(null, Integer.class, 36),
        FORTIFICATION(KEY_COLUMN_FORTIFICATION, String.class, 170),
        MEMBER(KEY_COLUMN_MEMBER, String.class, 150),
        POWER(KEY_COLUMN_POWER, Integer.class, 80),
        WAR_FLAG(KEY_WAR_FLAG, Object.class, 48),
        PET(KEY_PET, Object.class, 48),
        TOTEMS(KEY_TOTEMS, String.class, 120),
        TEAM(KEY_COLUMN_TEAM, Object.class, 5 * (ICON_SIZE + 4) + 12),
        BUFF(KEY_COLUMN_BUFF, Long.class, 50),
        SCORE(KEY_COLUMN_SCORE, Double.class, 60);

        /** Language file key of the header, null for the "#" column. */
        final String headerKey;
        final Class<?> type;
        final int width;

        Column(String headerKey, Class<?> type, int width) {
            this.headerKey = headerKey;
            this.type = type;
            this.width = width;
        }

        String header() {
            return headerKey == null ? "#" : LanguageService.displayName(headerKey);
        }

        boolean sortable() {
            return this != WAR_FLAG && this != PET && this != TEAM;
        }
    }

    private final class RowTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column).header();
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return columns.get(column).type;
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            Row<T> row = rows.get(rowIndex);
            return switch (columns.get(column)) {
                case NUMBER -> rowIndex + 1;
                case FORTIFICATION -> row.fortification == null
                        ? LanguageService.displayName(KEY_NO_SELECTION) : LanguageService.displayName(row.fortification.id());
                case MEMBER -> row.member == null ? "" : memberDisplayName(row.member);
                case POWER -> row.draft.totalPower;
                case WAR_FLAG -> row.draft.warFlag;
                case PET -> row.draft.pet;
                case TOTEMS -> TotemTexts.names(row.draft.totems);
                case TEAM -> row.draft.members;
                case BUFF -> buffCountOf(row);
                case SCORE -> breakdownOf(row).total();
            };
        }
    }

    private JComponent buildTablePane() {
        table.setRowSorter(sorter);
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            TableColumn tableColumn = table.getColumnModel().getColumn(i);
            tableColumn.setPreferredWidth(column.width);
            if (column == Column.WAR_FLAG || column == Column.PET || column == Column.TEAM) {
                tableColumn.setMinWidth(column.width); // icons must never be cut off
            }
            sorter.setSortable(i, column.sortable());
            tableColumn.setCellRenderer(switch (column) {
                case WAR_FLAG -> new ExtraIconRenderer<>(WarFlag::id, WarFlag::imagePath);
                case PET -> new ExtraIconRenderer<>(Pet::id, Pet::imagePath);
                case TEAM -> new TeamRenderer();
                case POWER -> new PowerRenderer();
                case SCORE -> new FormattedRenderer(v -> String.format(Locale.ROOT, "%.1f", (Double) v));
                default -> new ValidatingRenderer(column);
            });
        }

        sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                return matchesFilter(rows.get(entry.getIdentifier()));
            }
        });
        sorter.setSortKeys(List.of(
                new RowSorter.SortKey(columns.indexOf(Column.FORTIFICATION), SortOrder.ASCENDING),
                new RowSorter.SortKey(columns.indexOf(Column.MEMBER), SortOrder.ASCENDING)));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int viewRow = table.getSelectedRow();
            Row<T> selected = viewRow < 0 ? null : rows.get(table.convertRowIndexToModel(viewRow));
            if (selected != currentRow) {
                showRow(selected);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(900, 450));
        return scrollPane;
    }

    private boolean matchesFilter(Row<T> row) {
        if (row == currentRow) {
            return true; // never hide the line being edited
        }
        Object fortSelection = fortFilter.getSelectedItem();
        if (fortSelection == FILTER_NONE && row.fortification != null) {
            return false;
        }
        if (fortSelection instanceof Fortification f && row.fortification != f) {
            return false;
        }
        String text = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return true;
        }
        if (row.member != null && memberDisplayName(row.member).toLowerCase(Locale.ROOT).contains(text)) {
            return true;
        }
        return row.draft.members.stream()
                .anyMatch(m -> spec.label().apply(m).toLowerCase(Locale.ROOT).contains(text));
    }

    /** Re-applies filter and sort, keeping the current row selected. */
    private void refilter() {
        sorter.sort();
        selectRow(currentRow);
    }

    private void selectRow(Row<T> row) {
        int modelIndex = rows.indexOf(row);
        if (modelIndex < 0) {
            return;
        }
        int viewIndex = table.convertRowIndexToView(modelIndex);
        if (viewIndex < 0) {
            return;
        }
        table.setRowSelectionInterval(viewIndex, viewIndex);
        table.scrollRectToVisible(table.getCellRect(viewIndex, 0, true));
    }

    /** Repaints the current row's line plus everything derived from it (other lines' warnings, status, filter counts). */
    private void currentRowChanged() {
        dirty = true;
        int modelIndex = rows.indexOf(currentRow);
        if (modelIndex >= 0) {
            tableModel.fireTableRowsUpdated(modelIndex, modelIndex);
        }
        table.repaint(); // warnings of OTHER lines (overfull fortification, too many teams per member) may change too
        updateBuffLabel();
        updateStatus();
    }

    /** Highlights problems that would make the save fail. */
    private final class ValidatingRenderer extends DefaultTableCellRenderer {
        private final Column column;

        ValidatingRenderer(Column column) {
            this.column = column;
            if (column == Column.NUMBER || column == Column.BUFF) {
                setHorizontalAlignment(CENTER);
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                       int viewRow, int viewColumn) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, viewRow, viewColumn);
            Row<T> row = rows.get(t.convertRowIndexToModel(viewRow));
            setToolTipText(null);
            boolean problem = false;
            switch (column) {
                case FORTIFICATION -> {
                    if (isOverfull(row.fortification)) {
                        problem = true;
                        setText(getText() + "  (" + occupancy(row.fortification) + "/" + row.fortification.capacity() + ")");
                    }
                    if (row.fortification != null && row.fortification.buff() != null) {
                        setToolTipText(BuffTexts.describe(row.fortification.buff()));
                    }
                }
                case MEMBER -> {
                    if (lacksMember(row)) {
                        problem = true;
                        setText("?");
                        setToolTipText(LanguageService.displayName("common.rowWithoutMember"));
                    } else if (memberHasTooManyTeams(row.member)) {
                        problem = true;
                        setToolTipText(LanguageService.displayName("common.noFreeTeamSlot", memberDisplayName(row.member)));
                    }
                }
                default -> {
                }
            }
            // No setBackground here: DefaultTableCellRenderer would keep it as the default for every later cell.
            if (problem) {
                setForeground(isSelected ? t.getSelectionForeground() : IconLoader.RED);
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setForeground(isSelected ? t.getSelectionForeground() : t.getForeground());
            }
            return this;
        }
    }

    /** Power column: formatted number, red and bold for a team with members but power 0 (see {@link #lacksPower}). */
    private final class PowerRenderer extends DefaultTableCellRenderer {
        PowerRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                       int viewRow, int viewColumn) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, viewRow, viewColumn);
            setText(value == null ? "" : GuiUtils.NUMBER_FORMAT.format(value));
            Row<T> row = rows.get(t.convertRowIndexToModel(viewRow));
            // No setBackground here - see ValidatingRenderer.
            if (lacksPower(row)) {
                setForeground(isSelected ? t.getSelectionForeground() : IconLoader.RED);
                setFont(getFont().deriveFont(Font.BOLD));
                setToolTipText(LanguageService.displayName(KEY_POWER_MISSING));
            } else {
                setForeground(isSelected ? t.getSelectionForeground() : t.getForeground());
                setToolTipText(null);
            }
            return this;
        }
    }

    private static final class FormattedRenderer extends DefaultTableCellRenderer {
        private final java.util.function.Function<Object, String> format;

        FormattedRenderer(java.util.function.Function<Object, String> format) {
            this.format = format;
            setHorizontalAlignment(RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            setText(value == null ? "" : format.apply(value));
        }
    }

    /** War flag / pet column: just the icon, name as tooltip. */
    private static final class ExtraIconRenderer<E> extends DefaultTableCellRenderer {
        private final java.util.function.Function<E, String> idOf;
        private final java.util.function.Function<E, String> imagePathOf;

        ExtraIconRenderer(java.util.function.Function<E, String> idOf, java.util.function.Function<E, String> imagePathOf) {
            this.idOf = idOf;
            this.imagePathOf = imagePathOf;
            setHorizontalAlignment(CENTER);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void setValue(Object value) {
            setText(null);
            if (value == null) {
                setIcon(null);
                setToolTipText(null);
                return;
            }
            E typed = (E) value;
            String name = LanguageService.displayName(idOf.apply(typed));
            Icon icon = IconLoader.iconFor(imagePathOf.apply(typed), ICON_SIZE);
            setIcon(icon);
            if (icon == null) {
                setText(name);
            }
            setToolTipText(name);
        }
    }

    /** Team column: the (up to 5) member icons side by side, names as tooltip. */
    private final class TeamRenderer extends JPanel implements TableCellRenderer {
        private final List<JLabel> labels = new ArrayList<>();

        TeamRenderer() {
            super(new FlowLayout(FlowLayout.LEFT, 2, 1));
            for (int i = 0; i < 5; i++) {
                JLabel label = new JLabel();
                labels.add(label);
                add(label);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            List<T> members = value == null ? List.of() : (List<T>) value;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) {
                JLabel label = labels.get(i);
                T member = i < members.size() ? members.get(i) : null;
                Icon icon = member == null || spec.icon() == null ? null : spec.icon().apply(member);
                label.setIcon(icon);
                label.setText(member != null && icon == null ? spec.label().apply(member) : null);
                label.setForeground(isSelected ? t.getSelectionForeground() : t.getForeground());
                if (member != null) {
                    names.add(spec.label().apply(member));
                }
            }
            setToolTipText(names.isEmpty() ? null : String.join(", ", names));
            return this;
        }
    }

    // ---------------------------------------------------------------- editor

    private JComponent buildEditorArea() {
        fortCombo.setPreferredSize(new Dimension(COMBO_WIDTH, COMBO_HEIGHT));
        fortCombo.addActionListener(e -> {
            if (binding || currentRow == null) {
                return;
            }
            Fortification selected = (Fortification) fortCombo.getSelectedItem();
            if (selected == currentRow.fortification) {
                return;
            }
            currentRow.fortification = selected;
            if (selected != null) {
                lastSelectedFortification = selected;
            }
            currentRowChanged();
        });

        memberCombo.setPreferredSize(new Dimension(COMBO_WIDTH, COMBO_HEIGHT));
        memberCombo.setEditable(true);
        memberCombo.setEditor(new MemberComboEditor());
        memberCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? LanguageService.displayName(KEY_NO_SELECTION) : memberDisplayName((MemberDraft) value));
                return this;
            }
        });
        refreshMemberCombo();
        memberCombo.addActionListener(e -> {
            if (binding || currentRow == null) {
                return;
            }
            applyMember((MemberDraft) memberCombo.getSelectedItem());
        });

        buffLabel.setPreferredSize(new Dimension(BUFF_LABEL_WIDTH, COMBO_HEIGHT));
        buffLabel.setForeground(FortificationTypeStyle.color(spec.fortificationType()));
        teamEditorHolder.setOpaque(false);
        // Always reserve a full team editor's size - otherwise a dialog opened
        // without any team (empty guild: only the short "nothing selected" hint
        // in here) is sized too small, and the first real team editor ends up
        // out of sight. Also keeps the layout from jumping between selections.
        Dimension editorSize = buildTeamEditor(new Row<>(null, -1)).getPreferredSize();
        teamEditorHolder.setPreferredSize(editorSize);
        teamEditorHolder.setMinimumSize(editorSize);

        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        line.add(fortCombo);
        line.add(memberCombo);
        line.add(teamEditorHolder);
        line.add(buffLabel);

        // Inside a scroll pane the line keeps its preferred width and never
        // wraps (a wrapped second line would be cut off by the split pane);
        // a too narrow dialog gets a horizontal scroll bar instead.
        JScrollPane lineScroller = new JScrollPane(line,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        lineScroller.setBorder(BorderFactory.createEmptyBorder());
        lineScroller.getViewport().setOpaque(false);
        lineScroller.setOpaque(false);

        nothingSelectedLabel.setText(LanguageService.displayName(KEY_NOTHING_SELECTED, shortcutText(SHORTCUT_ADD)));
        nothingSelectedLabel.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        editorPanel.setBorder(BorderFactory.createTitledBorder(LanguageService.displayName(KEY_SELECTED_TEAM)));
        editorPanel.add(lineScroller, BorderLayout.CENTER);
        skipArrowButtonsInTabOrder(editorPanel);
        return editorPanel;
    }

    /** The team editor (power, war flag/pet for heroes, totems for titans, 5 slots, F1-F5 templates) bound to {@code row}'s draft. */
    private TeamEditorPanel<T> buildTeamEditor(Row<T> row) {
        TeamExtras extras = withExtras
                ? TeamExtras.forOtherDrafts(appContext.catalog(), () -> otherTeamsOfRowMember(row))
                : null;
        TeamEditorPanel<T> panel = new TeamEditorPanel<>(spec.catalog(), spec.label(), spec.icon(), null, row.draft,
                LanguageService.displayName(KEY_NO_SELECTION), spec.catalogOrder(), this::currentRowChanged, extras,
                withTotems ? TitanTeamExtras.ALL : null);
        panel.enableTemplates(spec.templates(), spec.idOf());
        skipArrowButtonsInTabOrder(panel);
        return panel;
    }

    /**
     * Takes the drop-down arrow buttons of every combo box inside
     * {@code container} out of the Tab order - the Material look and feel
     * makes them focusable, so every Tab would otherwise stop twice per combo
     * box (combo, then its arrow). The arrows stay clickable.
     */
    private static void skipArrowButtonsInTabOrder(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComboBox<?> combo) {
                for (Component part : combo.getComponents()) {
                    if (part instanceof JButton arrow) {
                        arrow.setFocusable(false);
                    }
                }
            } else if (child instanceof Container nested) {
                skipArrowButtonsInTabOrder(nested);
            }
        }
    }

    private void applyMember(MemberDraft member) {
        if (member == currentRow.member) {
            return;
        }
        currentRow.member = member;
        // Same convention as FortificationEntryDialog: "- none -" as member clears the team,
        // so an unwanted line is dropped on save (power 0).
        if (member == null && teamEditor != null) {
            teamEditor.clear();
        }
        currentRowChanged();
    }

    /** Binds the editor area to {@code row} (or disables it for null). */
    private void showRow(Row<T> row) {
        commitPendingMemberEdit();
        currentRow = row;

        binding = true;
        try {
            fortCombo.setSelectedItem(row == null ? null : row.fortification);
            memberCombo.setSelectedItem(row == null ? null : row.member);
        } finally {
            binding = false;
        }
        fortCombo.setEnabled(row != null);
        memberCombo.setEnabled(row != null);

        teamEditorHolder.removeAll();
        teamEditor = null;
        if (row == null) {
            teamEditorHolder.add(nothingSelectedLabel, BorderLayout.CENTER);
        } else {
            teamEditor = buildTeamEditor(row);
            teamEditorHolder.add(teamEditor, BorderLayout.CENTER);
        }
        updateBuffLabel();
        teamEditorHolder.revalidate();
        teamEditorHolder.repaint();
    }

    /**
     * Applies text typed into the member combo that was not confirmed with
     * Enter yet - before the editor switches to another row, so it can't end
     * up on the wrong one.
     */
    private void commitPendingMemberEdit() {
        if (currentRow == null || !memberCombo.isEditable()) {
            return;
        }
        String text = ((JTextField) memberCombo.getEditor().getEditorComponent()).getText().trim();
        if (currentRow.member != null && (memberDisplayName(currentRow.member).equalsIgnoreCase(text)
                || currentRow.member.id.equalsIgnoreCase(text))) {
            return; // unchanged - also keeps the right one of two members with the same name
        }
        Object typed = memberCombo.getEditor().getItem();
        if (typed != currentRow.member) {
            applyMember((MemberDraft) typed);
        }
    }

    private void updateBuffLabel() {
        if (currentRow == null) {
            buffLabel.setText("");
            buffLabel.setToolTipText(null);
            return;
        }
        buffLabel.setText(buffCountOf(currentRow) + " ("
                + String.format(Locale.ROOT, "%.1f", breakdownOf(currentRow).total()) + ")");
        buffLabel.setToolTipText(currentRow.fortification != null && currentRow.fortification.buff() != null
                ? BuffTexts.describe(currentRow.fortification.buff()) : "");
    }

    /**
     * The other teams of {@code self}'s member - the source of its blocked
     * war flags/pets (see {@link TeamExtras}): every other line currently
     * showing that member, plus the member's saved teams no line is bound to.
     */
    private List<TeamDraft<T>> otherTeamsOfRowMember(Row<T> self) {
        MemberDraft member = self.member;
        if (member == null) {
            return List.of();
        }
        List<TeamDraft<T>> result = new ArrayList<>();
        Set<Integer> slotsWithRow = new HashSet<>();
        for (Row<T> row : rows) {
            if (row.boundMember == member) {
                slotsWithRow.add(row.boundTeamIndex);
            }
            if (row != self && row.member == member) {
                result.add(row.draft);
            }
        }
        List<TeamDraft<T>> teams = spec.teamsOf().apply(member);
        for (int i = 0; i < teams.size(); i++) {
            if (!slotsWithRow.contains(i)) {
                result.add(teams.get(i));
            }
        }
        return result;
    }

    private void refreshMemberCombo() {
        Object selected = memberCombo.getSelectedItem();
        DefaultComboBoxModel<MemberDraft> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        sortedMembers().forEach(model::addElement);
        binding = true;
        try {
            memberCombo.setModel(model);
            memberCombo.setSelectedItem(selected);
        } finally {
            binding = false;
        }
    }

    /**
     * {@link ComboBoxEditor} of the member combo: shows a member's display name
     * and resolves typed text on commit - to an existing member (by display
     * name or id), to a freshly created one (added to {@link #draft}, capped at
     * {@link #MAX_MEMBERS}), or to {@code null} for blank text.
     */
    private final class MemberComboEditor implements ComboBoxEditor {
        private final JTextField textField = new JTextField();

        MemberComboEditor() {
            textField.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        }

        @Override
        public Component getEditorComponent() {
            return textField;
        }

        @Override
        public void setItem(Object item) {
            textField.setText(item == null ? "" : memberDisplayName((MemberDraft) item));
        }

        @Override
        public Object getItem() {
            String typed = textField.getText().trim();
            if (typed.isEmpty()) {
                return null;
            }
            MemberDraft existing = findMemberByDisplayOrId(typed);
            if (existing != null) {
                return existing;
            }
            if (draft.members.size() >= MAX_MEMBERS) {
                JOptionPane.showMessageDialog(GuildTeamEntryDialog.this,
                        LanguageService.displayName("common.maxMembers", MAX_MEMBERS), LanguageService.displayName("common.notPossibleTitle"),
                        JOptionPane.WARNING_MESSAGE);
                return memberCombo.getSelectedItem();
            }
            MemberDraft created = new MemberDraft(typed, typed);
            ensureTeamCount(spec.teamsOf().apply(created), maxTeams);
            draft.members.add(created);
            Logger.log("Created guild member: " + typed);
            SwingUtilities.invokeLater(GuildTeamEntryDialog.this::refreshMemberCombo);
            return created;
        }

        @Override
        public void selectAll() {
            textField.selectAll();
        }

        @Override
        public void addActionListener(ActionListener listener) {
            textField.addActionListener(listener);
        }

        @Override
        public void removeActionListener(ActionListener listener) {
            textField.removeActionListener(listener);
        }
    }

    // ------------------------------------------------------- toolbar/actions

    private JComponent buildToolbar() {
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        saveButton.setToolTipText(withShortcut(LanguageService.displayName(KEY_SAVE_TEAMS), SHORTCUT_SAVE));
        saveButton.addActionListener(e -> performSave());

        FlatButton addButton = new FlatButton(IconLoader.iconFor(ICON_ADD, TOOLBAR_ICON_SIZE, Color.WHITE));
        addButton.setToolTipText(withShortcut(LanguageService.displayName(KEY_ADD_ROW), SHORTCUT_ADD));
        addButton.addActionListener(e -> addRow());

        FlatButton deleteButton = new FlatButton(IconLoader.iconFor(ICON_DELETE, TOOLBAR_ICON_SIZE, IconLoader.RED));
        deleteButton.setToolTipText(withShortcut(LanguageService.displayName(KEY_DELETE_ROW), SHORTCUT_DELETE));
        deleteButton.addActionListener(e -> deleteCurrentRow());

        fortFilter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == FILTER_ALL) {
                    setText(LanguageService.displayName(KEY_FILTER_ALL) + " (" + rows.size() + ")");
                } else if (value == FILTER_NONE) {
                    setText(LanguageService.displayName(KEY_NO_SELECTION) + " (" + occupancy(null) + ")");
                } else if (value instanceof Fortification f) {
                    long used = occupancy(f);
                    setText(LanguageService.displayName(f.id()) + " (" + used + "/" + f.capacity() + ")");
                    if (!isSelected && used > f.capacity()) {
                        setForeground(IconLoader.RED);
                    }
                }
                return this;
            }
        });
        fortFilter.addActionListener(e -> refilter());

        searchField.setToolTipText(LanguageService.displayName(KEY_SEARCH_HINT));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refilter();
            }
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        left.add(saveButton);
        left.add(addButton);
        left.add(deleteButton);
        left.add(Box.createHorizontalStrut(16));
        left.add(fortFilter);
        left.add(new JLabel(LanguageService.displayName(KEY_SEARCH)));
        left.add(searchField);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.add(buildShortcutsButton());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    /** "Shortcuts" button at the right end of the toolbar - shows {@link #shortcutsHtml()} in a popup below it. */
    private JButton buildShortcutsButton() {
        FlatButton button = new FlatButton(null);
        button.setText(LanguageService.displayName(KEY_SHORTCUTS));
        button.setToolTipText(LanguageService.displayName(KEY_SHORTCUTS));
        button.addActionListener(e -> {
            JLabel content = new JLabel(shortcutsHtml());
            content.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            JPopupMenu popup = new JPopupMenu();
            popup.add(content);
            // Right-aligned under the button, so it doesn't stick out of the dialog's right edge.
            popup.show(button, button.getWidth() - popup.getPreferredSize().width, button.getHeight());
        });
        return button;
    }

    /** Every shortcut of this dialog as an HTML table - key texts in the JVM's language, see {@link #shortcutText}. */
    private String shortcutsHtml() {
        String templateKeys = KeyEvent.getKeyText(KeyEvent.VK_F1) + " – " + KeyEvent.getKeyText(KeyEvent.VK_F5);
        String shift = InputEvent.getModifiersExText(InputEvent.SHIFT_DOWN_MASK);
        String[][] entries = {
                {shortcutText(SHORTCUT_ADD), KEY_SHORTCUT_ADD},
                {shortcutText(SHORTCUT_SAVE), KEY_SHORTCUT_SAVE},
                {shortcutText(SHORTCUT_DELETE), KEY_SHORTCUT_DELETE},
                {KeyEvent.getKeyText(KeyEvent.VK_ENTER), KEY_SHORTCUT_EDIT},
                {KeyEvent.getKeyText(KeyEvent.VK_ENTER), KEY_SHORTCUT_MEMBER},
                {templateKeys, KEY_SHORTCUT_LOAD_TEMPLATE},
                {shift + "+" + templateKeys, KEY_SHORTCUT_SAVE_TEMPLATE},
                {KeyEvent.getKeyText(KeyEvent.VK_TAB) + " / " + shift + "+" + KeyEvent.getKeyText(KeyEvent.VK_TAB),
                        KEY_SHORTCUT_NEXT_FIELD},
                {"A – Z", KEY_SHORTCUT_TYPE_AHEAD},
        };
        StringBuilder html = new StringBuilder("<html><b>")
                .append(escapeHtml(LanguageService.displayName(KEY_SHORTCUTS)))
                .append("</b><table cellpadding='3'>");
        for (String[] entry : entries) {
            html.append("<tr><td nowrap><b>").append(escapeHtml(entry[0])).append("</b></td><td>")
                    .append(escapeHtml(LanguageService.displayName(entry[1]))).append("</td></tr>");
        }
        return html.append("</table></html>").toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Fills the fortification filter: all / none / every fortification of this type (counts are rendered live). */
    private void refreshFortFilter() {
        Object selected = fortFilter.getSelectedItem();
        DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
        model.addElement(FILTER_ALL);
        model.addElement(FILTER_NONE);
        fortificationCatalog.stream()
                .filter(f -> f.type() == spec.fortificationType())
                .sorted(Comparator.comparing(f -> LanguageService.displayName(f.id())))
                .forEach(model::addElement);
        model.setSelectedItem(selected == null ? FILTER_ALL : selected);
        fortFilter.setModel(model);
    }

    private JComponent buildStatusBar() {
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return statusLabel;
    }

    private void updateStatus() {
        long withoutFort = rows.stream().filter(r -> r.fortification == null).count();
        long withoutMember = rows.stream().filter(GuildTeamEntryDialog::lacksMember).count();
        long withoutPower = rows.stream().filter(GuildTeamEntryDialog::lacksPower).count();
        statusLabel.setText(LanguageService.displayName(KEY_STATUS, rows.size(), withoutFort, withoutMember, withoutPower)
                + (dirty ? " · " + LanguageService.displayName(KEY_UNSAVED) : ""));
        fortFilter.repaint();
    }

    private void bindShortcuts() {
        JRootPane root = getRootPane();
        InputMap windowKeys = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap windowActions = root.getActionMap();
        windowKeys.put(SHORTCUT_ADD, "c2w.addRow");
        windowKeys.put(SHORTCUT_SAVE, "c2w.save");
        windowActions.put("c2w.addRow", action(this::addRow));
        windowActions.put("c2w.save", action(this::performSave));

        InputMap tableKeys = table.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap tableActions = table.getActionMap();
        tableKeys.put(SHORTCUT_DELETE, "c2w.deleteRow");
        tableKeys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "c2w.editRow");
        tableActions.put("c2w.deleteRow", action(this::deleteCurrentRow));
        tableActions.put("c2w.editRow", action(() -> {
            if (currentRow != null) {
                memberCombo.requestFocusInWindow();
            }
        }));
        // F1-F5 straight from the table: load template into the selected team.
        for (int slot = TeamTemplate.MIN_SLOT; slot <= TeamTemplate.MAX_SLOT; slot++) {
            int templateSlot = slot;
            String key = "c2w.tableTemplate" + slot;
            tableKeys.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1 + slot - 1, 0), key);
            tableActions.put(key, action(() -> {
                if (teamEditor != null) {
                    teamEditor.loadTemplate(templateSlot);
                }
            }));
        }
    }

    private static Action action(Runnable runnable) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runnable.run();
            }
        };
    }

    /** A shortcut's key text in the JVM's language, e.g. "Strg+N" / "Ctrl+N". */
    private static String shortcutText(KeyStroke shortcut) {
        String modifiers = InputEvent.getModifiersExText(shortcut.getModifiers());
        String key = KeyEvent.getKeyText(shortcut.getKeyCode());
        return modifiers.isEmpty() ? key : modifiers + "+" + key;
    }

    private static String withShortcut(String text, KeyStroke shortcut) {
        return text + " (" + shortcutText(shortcut) + ")";
    }

    /** Appends a new, empty team - no save first (see class Javadoc). */
    private void addRow() {
        commitPendingMemberEdit();
        Row<T> row = new Row<>(null, -1);
        Object filtered = fortFilter.getSelectedItem();
        if (filtered instanceof Fortification f) {
            row.fortification = f;
        } else if (filtered != FILTER_NONE && lastSelectedFortification != null
                && occupancy(lastSelectedFortification) < lastSelectedFortification.capacity()) {
            row.fortification = lastSelectedFortification;
        }
        rows.add(row);
        searchField.setText("");
        tableModel.fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        dirty = true;
        selectRow(row);
        updateStatus();
        memberCombo.requestFocusInWindow();
    }

    /**
     * Deletes the selected line - and removes its team from the guild too (the
     * slot it is bound to, see {@link Row#boundMember}), then saves. Confirmed
     * first, since this cannot be undone. A never-saved line simply disappears.
     */
    private void deleteCurrentRow() {
        Row<T> row = currentRow;
        if (row == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName("guildEntry.deleteTeamConfirm"),
                LanguageService.displayName("guildEntry.deleteTeamTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        if (row.boundMember != null) {
            deleteBoundTeam(row.boundMember, row.boundTeamIndex);
        }
        int viewIndex = table.getSelectedRow();
        int modelIndex = rows.indexOf(row);
        showRow(null);
        rows.remove(modelIndex);
        tableModel.fireTableRowsDeleted(modelIndex, modelIndex);
        if (table.getRowCount() > 0) {
            int next = Math.min(Math.max(viewIndex, 0), table.getRowCount() - 1);
            table.setRowSelectionInterval(next, next);
        }
        performSave();
        updateStatus();
    }

    /**
     * Removes the team at {@code teamIndex} from {@code member}'s teams and
     * re-pads the list to {@link #maxTeams}, so its non-empty entries stay
     * contiguous from index 0; lines bound to a later slot of the same member
     * shift down by one to follow their team.
     */
    private void deleteBoundTeam(MemberDraft member, int teamIndex) {
        List<TeamDraft<T>> teams = spec.teamsOf().apply(member);
        if (teamIndex < 0 || teamIndex >= teams.size()) {
            return;
        }
        teams.remove(teamIndex);
        ensureTeamCount(teams, maxTeams);
        for (Row<T> other : rows) {
            if (other.boundMember == member && other.boundTeamIndex > teamIndex) {
                other.boundTeamIndex--;
            }
        }
    }

    private void onClose() {
        commitPendingMemberEdit();
        if (dirty) {
            int choice = JOptionPane.showConfirmDialog(this, LanguageService.displayName(KEY_UNSAVED_QUESTION), LanguageService.displayName(KEY_UNSAVED),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION && !performSave()) {
                return;
            }
            if (choice == JOptionPane.NO_OPTION) {
                Logger.log("Guild " + spec.teamType() + " entry dialog closed - unsaved changes discarded");
            }
        }
        dispose();
    }

    // ------------------------------------------------------------------ save

    /**
     * The slot a line of {@code memberId} is saved into: its preferred (= bound)
     * slot if still free, else the first free empty slot, else the first free
     * slot at all; -1 if every slot is taken by another line.
     */
    private static <T> int resolveTeamIndex(List<TeamDraft<T>> teams, Set<String> boundKeys, String memberId,
                                            int preferredIndex) {
        if (preferredIndex >= 0 && preferredIndex < teams.size() && !boundKeys.contains(rowKey(memberId, preferredIndex))) {
            return preferredIndex;
        }
        for (int i = 0; i < teams.size(); i++) {
            if (!boundKeys.contains(rowKey(memberId, i)) && teams.get(i).totalPower == 0) {
                return i;
            }
        }
        for (int i = 0; i < teams.size(); i++) {
            if (!boundKeys.contains(rowKey(memberId, i))) {
                return i;
            }
        }
        return -1;
    }

    private static String rowKey(String memberId, int teamIndex) {
        return memberId + "#" + teamIndex;
    }

    /**
     * Resolves every line to a (member, teamIndex) slot - empty lines (power 0)
     * are skipped; a filled-in line without member or without a free slot
     * aborts with a warning, selects that line and returns {@code null}.
     */
    private List<Resolution<T>> resolveResolutions() {
        Set<String> boundKeys = new HashSet<>();
        List<Resolution<T>> resolutions = new ArrayList<>();
        for (Row<T> row : rows) {
            if (lacksPower(row)) {
                Logger.log("Guild " + spec.teamType() + " teams not saved: a team has members but no power");
                selectRow(row);
                JOptionPane.showMessageDialog(this,
                        LanguageService.displayName(KEY_POWER_MISSING),
                        LanguageService.displayName("common.saveNotPossibleTitle"), JOptionPane.WARNING_MESSAGE);
                return null;
            }
            if (row.draft.totalPower == 0) {
                continue; // nothing entered at all (e.g. a fresh line) - simply dropped
            }
            if (row.member == null) {
                Logger.log("Guild " + spec.teamType() + " teams not saved: a filled-in team has no member");
                selectRow(row);
                JOptionPane.showMessageDialog(this,
                        LanguageService.displayName("common.rowWithoutMember"),
                        LanguageService.displayName("common.saveNotPossibleTitle"), JOptionPane.WARNING_MESSAGE);
                return null;
            }
            List<TeamDraft<T>> teams = spec.teamsOf().apply(row.member);
            int preferredIndex = row.member == row.boundMember ? row.boundTeamIndex : -1;
            int idx = resolveTeamIndex(teams, boundKeys, row.member.id, preferredIndex);
            if (idx < 0) {
                Logger.log("Guild " + spec.teamType() + " teams not saved: no free team slot left for member " + row.member.id);
                selectRow(row);
                JOptionPane.showMessageDialog(this,
                        LanguageService.displayName("common.noFreeTeamSlot", memberDisplayName(row.member)),
                        LanguageService.displayName("common.saveNotPossibleTitle"), JOptionPane.WARNING_MESSAGE);
                return null;
            }
            boundKeys.add(rowKey(row.member.id, idx));
            resolutions.add(new Resolution<>(row, row.member, idx));
        }
        return resolutions;
    }

    /**
     * Writes every line into its slot, rebuilds this type's entries of the
     * guild's "Original" lineup (NOT whatever lineup is open in the toolbar)
     * and saves guild + lineup. Returns whether it was saved.
     */
    private boolean performSave() {
        commitPendingMemberEdit();
        List<Resolution<T>> resolutions = resolveResolutions();
        if (resolutions == null) {
            return false;
        }
        for (Resolution<T> resolution : resolutions) {
            spec.teamsOf().apply(resolution.member()).get(resolution.teamIndex()).copyFrom(resolution.row().draft);
            resolution.row().boundMember = resolution.member();
            resolution.row().boundTeamIndex = resolution.teamIndex();
        }
        if (!TeamExtras.confirmNoConflict(this, draft)) {
            Logger.log("Guild " + spec.teamType() + " teams not saved: a member uses the same pet/war flag twice");
            return false;
        }

        List<Lineup.Entry> updatedEntries = new ArrayList<>(originalLineup.entries().stream()
                .filter(e -> e.teamType() != spec.teamType())
                .toList());
        for (Resolution<T> resolution : resolutions) {
            if (resolution.row().fortification != null) {
                updatedEntries.add(new Lineup.Entry(resolution.row().fortification.id(), resolution.member().id,
                        spec.teamType(), resolution.teamIndex()));
            }
        }
        try {
            Lineup updatedOriginal = new Lineup(originalLineup.guildId(), originalLineup.guildName(),
                    "", originalLineup.createdAt(), updatedEntries);
            Guild updatedGuild = GuildDraftConverter.toGuild(draft);
            new LineupService(appContext).saveOriginal(updatedGuild, updatedOriginal, originalLineupPath);
            this.originalLineup = updatedOriginal;
            Logger.log("Saved guild " + spec.teamType() + " teams into the Original lineup");
            dirty = false;
            refreshMemberCombo();
            updateStatus();
            return true;
        } catch (IllegalArgumentException ex) {
            Logger.logException("Guild " + spec.teamType() + " teams not saved: invalid data", ex);
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.invalidData") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            Logger.logException("Guild " + spec.teamType() + " teams not saved: could not write " + originalLineupPath, ex);
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    // ----------------------------------------------------------------- types

    /**
     * One team line. {@code member}/{@code fortification} are what the line
     * currently shows; {@code boundMember}/{@code boundTeamIndex} the slot it
     * was last saved into, so the next save UPDATES that team instead of
     * resolving to a free slot and inserting a duplicate.
     */
    private static final class Row<T> {
        final TeamDraft<T> draft = new TeamDraft<>();
        MemberDraft member;
        Fortification fortification;
        MemberDraft boundMember;
        int boundTeamIndex;

        Row(MemberDraft boundMember, int boundTeamIndex) {
            this.boundMember = boundMember;
            this.boundTeamIndex = boundTeamIndex;
        }
    }

    private record Resolution<T>(Row<T> row, MemberDraft member, int teamIndex) {
    }
}
