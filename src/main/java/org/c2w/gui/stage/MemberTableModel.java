package org.c2w.gui.stage;

import org.c2w.i18n.LanguageService;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only table model of the member overview in {@link InputStageView}, one row per
 * {@link MemberOverviewModel.MemberRow}. The column classes make a {@code TableRowSorter}
 * sort numbers numerically and dates chronologically (an unknown date after every known one).
 */
final class MemberTableModel extends AbstractTableModel {

    static final int COLUMN_MEMBER = 0;
    static final int COLUMN_TEAMS = 1;
    static final int COLUMN_TOTAL_POWER = 2;
    static final int COLUMN_STRONGEST_TEAM = 3;
    static final int COLUMN_LAST_MODIFIED = 4;

    private static final String[] COLUMN_KEYS = {
            "memberOverview.member", "memberOverview.teams", "memberOverview.totalPower",
            "memberOverview.strongestTeam", "memberOverview.lastModified"};

    private static final Class<?>[] COLUMN_CLASSES = {
            String.class, Integer.class, Integer.class, Integer.class, DateValue.class};

    /**
     * A last-modified date as a table value: sorts chronologically, an unknown date (null) after
     * every known one.
     */
    record DateValue(LocalDate date) implements Comparable<DateValue> {

        @Override
        public int compareTo(DateValue other) {
            if (date == null) {
                return other.date == null ? 0 : 1;
            }
            return other.date == null ? -1 : date.compareTo(other.date);
        }

        @Override
        public String toString() {
            return InfoSections.shortDate(date);
        }
    }

    private List<MemberOverviewModel.MemberRow> rows = List.of();

    void setRows(List<MemberOverviewModel.MemberRow> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    MemberOverviewModel.MemberRow row(int modelRow) {
        return rows.get(modelRow);
    }

    /** The model row of the member with {@code memberId}, -1 if there is none. */
    int indexOf(String memberId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).memberId().equals(memberId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_KEYS.length;
    }

    @Override
    public String getColumnName(int column) {
        return LanguageService.displayName(COLUMN_KEYS[column]);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return COLUMN_CLASSES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int column) {
        MemberOverviewModel.MemberRow row = rows.get(rowIndex);
        return switch (column) {
            case COLUMN_MEMBER -> row.name();
            case COLUMN_TEAMS -> row.teamCount();
            case COLUMN_TOTAL_POWER -> row.totalPower();
            case COLUMN_STRONGEST_TEAM -> row.strongestTeamPower();
            case COLUMN_LAST_MODIFIED -> new DateValue(row.lastModified());
            default -> throw new IllegalArgumentException("No column " + column);
        };
    }
}
