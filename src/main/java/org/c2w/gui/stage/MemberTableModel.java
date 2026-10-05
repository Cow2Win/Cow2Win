package org.c2w.gui.stage;

import org.c2w.gui.StageStatus;
import org.c2w.i18n.LanguageService;
import org.c2w.service.DataStatus;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-only table model of the member overview in {@link InputStageView}, one row per
 * {@link MemberOverviewModel.MemberRow}, with the traffic light of its data status in the first
 * column. The column classes make a {@code TableRowSorter}
 * sort numbers numerically and dates chronologically (an unknown date after every known one).
 */
final class MemberTableModel extends AbstractTableModel {

    /** The traffic light of the member's data status - no header text. */
    static final int COLUMN_STATUS = 0;
    static final int COLUMN_MEMBER = 1;
    static final int COLUMN_TEAMS = 2;
    static final int COLUMN_TOTAL_POWER = 3;
    static final int COLUMN_STRONGEST_TEAM = 4;
    static final int COLUMN_LAST_MODIFIED = 5;

    private static final String KEY_STALE_JOURNAL = "memberOverview.stale.journal";
    private static final String KEY_STALE_DAYS = "memberOverview.stale.days";
    private static final String KEY_STALE_UNKNOWN = "memberOverview.stale.unknown";
    private static final String KEY_STALE_NONE = "memberOverview.stale.none";

    private static final String[] COLUMN_KEYS = {
            null, "memberOverview.member", "memberOverview.teams", "memberOverview.totalPower",
            "memberOverview.strongestTeam", "memberOverview.lastModified"};

    private static final Class<?>[] COLUMN_CLASSES = {
            StatusValue.class, String.class, Integer.class, Integer.class, Integer.class, DateValue.class};

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

    /**
     * A member's data status as a table value: sorts by severity (outdated per journal, too old,
     * no finding); {@code finding} is null while the data status is not known yet.
     */
    record StatusValue(DataStatus.MemberFinding finding) implements Comparable<StatusValue> {

        /** The traffic light: red outdated per journal, orange too old, green otherwise; none while unknown. */
        StageStatus light() {
            if (finding == null) {
                return StageStatus.NONE;
            }
            return switch (finding.state()) {
                case STALE_JOURNAL -> StageStatus.ACTION_NEEDED;
                case TOO_OLD -> StageStatus.ATTENTION;
                case OK, NO_TEAMS -> StageStatus.OK;
            };
        }

        /** The tooltip of the traffic light, null for none. */
        String tooltip() {
            if (finding == null) {
                return null;
            }
            return switch (finding.state()) {
                case STALE_JOURNAL -> LanguageService.displayName(KEY_STALE_JOURNAL, InfoSections.shortDate(finding.logDate()));
                case NO_TEAMS -> LanguageService.displayName(KEY_STALE_NONE);
                case TOO_OLD, OK -> finding.ageDays() != null
                        ? LanguageService.displayName(KEY_STALE_DAYS, finding.ageDays())
                        : finding.state() == DataStatus.MemberState.TOO_OLD ? LanguageService.displayName(KEY_STALE_UNKNOWN) : null;
            };
        }

        /** True for a red or orange member - see {@link DataStatus.MemberFinding#isStale()}. */
        boolean isStale() {
            return finding != null && finding.isStale();
        }

        private int severity() {
            if (finding == null) {
                return 0;
            }
            return switch (finding.state()) {
                case STALE_JOURNAL -> 2;
                case TOO_OLD -> 1;
                case OK, NO_TEAMS -> 0;
            };
        }

        @Override
        public int compareTo(StatusValue other) {
            return Integer.compare(severity(), other.severity());
        }
    }

    private List<MemberOverviewModel.MemberRow> rows = List.of();
    /** Per member id its finding of the last data status; empty while none is known. */
    private Map<String, DataStatus.MemberFinding> findings = Map.of();

    void setRows(List<MemberOverviewModel.MemberRow> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    /** Sets the members' findings of a new data status - shown with the next {@link #setRows}. */
    void setFindings(Map<String, DataStatus.MemberFinding> findings) {
        this.findings = Map.copyOf(findings);
    }

    /** The data status of the member in {@code modelRow}. */
    StatusValue status(int modelRow) {
        return new StatusValue(findings.get(rows.get(modelRow).memberId()));
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
        return COLUMN_KEYS[column] == null ? "" : LanguageService.displayName(COLUMN_KEYS[column]);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return COLUMN_CLASSES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int column) {
        MemberOverviewModel.MemberRow row = rows.get(rowIndex);
        return switch (column) {
            case COLUMN_STATUS -> status(rowIndex);
            case COLUMN_MEMBER -> row.name();
            case COLUMN_TEAMS -> row.teamCount();
            case COLUMN_TOTAL_POWER -> row.totalPower();
            case COLUMN_STRONGEST_TEAM -> row.strongestTeamPower();
            case COLUMN_LAST_MODIFIED -> new DateValue(row.lastModified());
            default -> throw new IllegalArgumentException("No column " + column);
        };
    }
}
