package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.Season;
import org.c2w.data.journal.SeasonCalendar;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.service.JournalStore;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Rows of the battle list: one battle of the journal per row (season first),
 * optionally filtered by season, opponent and result ({@link BattleListFilter}).
 * Values are typed (date, numbers) so the table sorts them correctly; the
 * renderer formats them. Also counts and sums the shown battles ({@link #countText()},
 * {@link #shownTotals()}). Usable without a window (tests).
 */
public final class JournalBattleTableModel extends AbstractTableModel {

    /** The columns, in display order. */
    public enum Column {
        SEASON(Integer.class),
        DATE(LocalDate.class),
        OPPONENT(String.class),
        RESULT(String.class),
        RANKING_POINTS(Integer.class),
        OWN_POINTS(Integer.class),
        OPPONENT_POINTS(Integer.class),
        LOGS(String.class);

        private final Class<?> type;

        Column(Class<?> type) {
            this.type = type;
        }

        /** The value type of the column. */
        public Class<?> type() {
            return type;
        }

        /** Language file key of the header. */
        public String key() {
            return "journal.battles.col." + switch (this) {
                case SEASON -> "season";
                case DATE -> "date";
                case OPPONENT -> "opponent";
                case RESULT -> "result";
                case RANKING_POINTS -> "rankingPoints";
                case OWN_POINTS -> "ownPoints";
                case OPPONENT_POINTS -> "opponentPoints";
                case LOGS -> "logs";
            };
        }
    }

    /**
     * What the battle list shows.
     *
     * @param hasJournal false if the guild has no journal file yet (nothing was created)
     * @param battles    all battles, newest first
     */
    public record Data(boolean hasJournal, List<BattleSummary> battles) {
        public static final Data EMPTY = new Data(false, List.of());

        public Data {
            battles = List.copyOf(battles);
        }

        /**
         * The seasons of the season filter: only those containing at least one battle,
         * oldest first (battles in a break or before the first season have none).
         */
        public List<Season> seasons() {
            SeasonCalendar calendar = SeasonCalendar.standard();
            return battles.stream().map(BattleSummary::seasonNumber).filter(Objects::nonNull)
                    .distinct().sorted().map(calendar::season).toList();
        }
    }

    private Data data = Data.EMPTY;
    private BattleListFilter filter = BattleListFilter.NONE;
    private List<BattleSummary> rows = List.of();

    /** Reads the battles of the open guild's journal - never creates a journal file. */
    public static Data load(JournalStore store) throws JournalException {
        Optional<JournalRepository> repo = store.repository(false);
        if (repo.isEmpty()) {
            return Data.EMPTY;
        }
        return new Data(true, repo.get().listBattles());
    }

    /** New data; a season filter whose season has no battle any more is reset to "all". */
    public void setData(Data newData) {
        this.data = Objects.requireNonNull(newData);
        Integer seasonNumber = filter.seasonNumber();
        if (seasonNumber != null && newData.battles().stream().noneMatch(b -> seasonNumber.equals(b.seasonNumber()))) {
            filter = filter.withSeason(null);
        }
        applyFilter();
    }

    public Data data() {
        return data;
    }

    /** Only the battles of the season with this number ({@code null} = all); the other filters stay. */
    public void setSeasonFilter(Integer seasonNumber) {
        setFilter(filter.withSeason(seasonNumber));
    }

    /** The number of the filtered season, {@code null} = all. */
    public Integer seasonFilter() {
        return filter.seasonNumber();
    }

    /** Shows only the battles passing {@code newFilter}. */
    public void setFilter(BattleListFilter newFilter) {
        this.filter = Objects.requireNonNull(newFilter);
        applyFilter();
    }

    public BattleListFilter filter() {
        return filter;
    }

    private void applyFilter() {
        rows = data.battles().stream().filter(filter::matches).toList();
        fireTableDataChanged();
    }

    /** Number of battles shown (after filtering). */
    public int shownCount() {
        return rows.size();
    }

    /** Number of battles in the journal. */
    public int totalCount() {
        return data.battles().size();
    }

    /** "n of m battles". */
    public String countText() {
        return JournalTexts.text("journal.battles.count", String.valueOf(shownCount()), String.valueOf(totalCount()));
    }

    /**
     * Sums of the shown (filtered) battles - what the table shows in each column.
     *
     * @param rankingPoints  ranking points of the finished battles (a running battle has none yet)
     * @param ownPoints      own points of all shown battles, the running one included (its current state)
     * @param opponentPoints opponent points of all shown battles, the running one included
     */
    public record Totals(long rankingPoints, long ownPoints, long opponentPoints) {
    }

    /** The sums of the shown battles, see {@link Totals}. */
    public Totals shownTotals() {
        long ranking = 0;
        long own = 0;
        long opponent = 0;
        for (BattleSummary b : rows) {
            if (b.status() != BattleStatus.RUNNING && b.rankingPoints() != null) {
                ranking += b.rankingPoints();
            }
            own += b.ownPoints() == null ? 0 : b.ownPoints();
            opponent += b.opponentPoints() == null ? 0 : b.opponentPoints();
        }
        return new Totals(ranking, own, opponent);
    }

    /** The battle of a (model) row. */
    public BattleSummary battle(int row) {
        return rows.get(row);
    }

    /** The model row of a battle, -1 if it is not shown. */
    public int rowOf(int battleId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).battleId() == battleId) {
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
        return Column.values().length;
    }

    @Override
    public String getColumnName(int column) {
        return JournalTexts.text(Column.values()[column].key());
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return Column.values()[column].type;
    }

    @Override
    public Object getValueAt(int row, int column) {
        BattleSummary b = rows.get(row);
        return switch (Column.values()[column]) {
            case SEASON -> b.seasonNumber();
            case DATE -> b.date();
            case OPPONENT -> JournalTexts.opponent(b.opponent());
            case RESULT -> resultText(b);
            case RANKING_POINTS -> b.status() == BattleStatus.RUNNING ? null : b.rankingPoints();
            case OWN_POINTS -> b.ownPoints();
            case OPPONENT_POINTS -> b.opponentPoints();
            case LOGS -> logs(b);
        };
    }

    /** "Victory", "Defeat", "Draw" or "running". */
    public static String resultText(BattleSummary b) {
        if (b.status() == BattleStatus.RUNNING || b.result() == null) {
            return JournalTexts.of("battleStatus", BattleStatus.RUNNING);
        }
        return JournalTexts.of("battleResult", b.result());
    }

    /** The stored log directions, e.g. "A/V". */
    public static String logs(BattleSummary b) {
        StringBuilder sb = new StringBuilder();
        for (LogDirection d : LogDirection.values()) {
            if (b.directions().contains(d)) {
                if (!sb.isEmpty()) {
                    sb.append('/');
                }
                sb.append(JournalTexts.of("battles.logs", d));
            }
        }
        return sb.toString();
    }
}
