package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.SeasonCalendar;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalDatabase;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.gui.journal.JournalBattleTableModel.Column;
import org.c2w.infra.Config;
import org.c2w.service.journal.ImportAnswers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JournalBattleTableModelTest extends JournalGuiTestSupport {

    @Test
    @DisplayName("Without a journal: empty, and no journal file is created")
    void emptyWithoutJournal() throws Exception {
        JournalBattleTableModel.Data data = JournalBattleTableModel.load(context.journal());
        JournalBattleTableModel model = new JournalBattleTableModel();
        model.setData(data);

        assertFalse(data.hasJournal());
        assertEquals(0, model.getRowCount());
        assertFalse(JournalDatabase.exists(guildService.guildDir("Alpha")));
    }

    @Test
    @DisplayName("The 6 German battles after an import: values, order, season filter")
    void sixBattles() throws Exception {
        assertTrue(service().execute(service().prepare(BattleLogTestFiles.season16Files("de")), ImportAnswers.defaults()).isSuccess());
        JournalBattleTableModel model = new JournalBattleTableModel();

        model.setData(JournalBattleTableModel.load(context.journal()));

        assertEquals(6, model.getRowCount());
        assertEquals(Column.values().length, model.getColumnCount());
        assertEquals(Column.SEASON, Column.values()[0], "season is the first column");
        assertEquals(JournalTexts.text("journal.battles.col.season"), model.getColumnName(0));
        assertEquals(LocalDate.class, model.getColumnClass(Column.DATE.ordinal()));
        assertEquals(Integer.class, model.getColumnClass(Column.OWN_POINTS.ordinal()));

        // newest first: 01.10. (running) - ranking points 0 are not shown
        assertEquals(LocalDate.of(2026, 10, 1), model.getValueAt(0, Column.DATE.ordinal()));
        assertTrue(((String) model.getValueAt(0, Column.OPPONENT.ordinal())).startsWith("Союз"));
        assertEquals(JournalTexts.of("battleStatus", BattleStatus.RUNNING), model.getValueAt(0, Column.RESULT.ordinal()));
        assertNull(model.getValueAt(0, Column.RANKING_POINTS.ordinal()));
        assertEquals(4990, model.getValueAt(0, Column.OWN_POINTS.ordinal()));
        assertEquals(2879, model.getValueAt(0, Column.OPPONENT_POINTS.ordinal()));
        assertEquals(JournalTexts.of("battles.logs", LogDirection.ATTACK) + "/"
                + JournalTexts.of("battles.logs", LogDirection.DEFENSE), model.getValueAt(0, Column.LOGS.ordinal()));
        assertEquals(16, model.getValueAt(0, Column.SEASON.ordinal()), "computed from the date");

        int auge = rowOf(model, LocalDate.of(2026, 9, 24));
        assertEquals(851, model.getValueAt(auge, Column.RANKING_POINTS.ordinal()));
        assertEquals(JournalTexts.of("battleResult", BattleResult.WIN), model.getValueAt(auge, Column.RESULT.ordinal()));
        int rakuen = rowOf(model, LocalDate.of(2026, 9, 21));
        assertEquals(-286, model.getValueAt(rakuen, Column.RANKING_POINTS.ordinal()));

        assertEquals(List.of(SeasonCalendar.standard().season(16)), model.data().seasons(), "only seasons with battles");
        model.setSeasonFilter(16);
        assertEquals(6, model.getRowCount());
        model.setSeasonFilter(17);
        assertEquals(0, model.getRowCount());
        model.setData(JournalBattleTableModel.load(context.journal()));
        assertNull(model.seasonFilter(), "a filter on a season without battles is reset");
        assertEquals(6, model.getRowCount());
        model.setSeasonFilter(null);
        assertEquals(6, model.getRowCount());
    }

    @Test
    @DisplayName("Filters: opponent, result, season and combinations; 'n of m battles'")
    void filters() throws Exception {
        assertTrue(service().execute(service().prepare(BattleLogTestFiles.season16Files("de")), ImportAnswers.defaults()).isSuccess());
        JournalBattleTableModel model = new JournalBattleTableModel();
        model.setData(JournalBattleTableModel.load(context.journal()));
        BattleListFilter none = BattleListFilter.NONE;

        model.setFilter(none.withOpponentText("  auge "));
        assertEquals(List.of(LocalDate.of(2026, 9, 24)), dates(model), "contains, case-insensitive");
        model.setFilter(none.withOpponentText("SOJUS"));
        assertEquals(0, model.shownCount());
        model.setFilter(none.withOpponentText("союз"));
        assertEquals(1, model.shownCount(), "Cyrillic, lower case");

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.ALL));
        assertEquals(6, model.shownCount(), "'all' includes the running battle");
        assertTrue(dates(model).contains(LocalDate.of(2026, 10, 1)));

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.WIN));
        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 17)), dates(model));
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS));
        assertEquals(3, model.shownCount());
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.DRAW));
        assertEquals(0, model.shownCount(), "a running battle is no draw");

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.WIN).withOpponentText("AUGE"));
        assertEquals(List.of(LocalDate.of(2026, 9, 24)), dates(model));
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS).withOpponentText("союз"));
        assertEquals(0, model.shownCount(), "the running battle has no result");
        model.setFilter(none.withSeason(16).withOpponentText("союз"));
        assertEquals(List.of(LocalDate.of(2026, 10, 1)), dates(model), "season + opponent, result 'all'");
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS).withOpponentText("r"));
        assertEquals(List.of(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 21)), dates(model),
                "Rebel Alliance, Rakuen (Elysium has no r)");
        assertEquals(JournalTexts.text("journal.battles.count", "2", "6"), model.countText());
        assertEquals(6, model.totalCount());

        model.setSeasonFilter(16);
        assertEquals(2, model.shownCount(), "the season filter keeps the other filters");
        model.setFilter(BattleListFilter.NONE);
        assertEquals(6, model.shownCount());
        assertEquals(-1, model.rowOf(999));
        assertEquals(0, model.rowOf(model.battle(0).battleId()));
    }

    @Test
    @DisplayName("Season filter: only seasons with battles, oldest first; a battle in a break only under 'all'")
    void seasonFilterByNumber() {
        SeasonCalendar calendar = SeasonCalendar.standard();
        List<BattleSummary> battles = List.of(
                battle(1, LocalDate.of(2026, 10, 15)),  // season 17
                battle(2, LocalDate.of(2026, 10, 11)),  // break between 16 and 17
                battle(3, LocalDate.of(2026, 9, 24)),   // season 16
                battle(4, LocalDate.of(2026, 1, 20)));  // season 14 - season 15 has no battle
        JournalBattleTableModel model = new JournalBattleTableModel();
        model.setData(new JournalBattleTableModel.Data(true, battles));

        assertEquals(List.of(calendar.season(14), calendar.season(16), calendar.season(17)), model.data().seasons());
        assertNull(model.getValueAt(model.rowOf(2), Column.SEASON.ordinal()));

        assertEquals(4, model.shownCount());
        model.setSeasonFilter(16);
        assertEquals(List.of(LocalDate.of(2026, 9, 24)), dates(model));
        model.setSeasonFilter(17);
        assertEquals(List.of(LocalDate.of(2026, 10, 15)), dates(model));
        model.setSeasonFilter(null);
        assertEquals(4, model.shownCount(), "the battle in the break appears under 'all' only");

        // all battles of season 17 deleted: the filter falls back to "all"
        model.setSeasonFilter(17);
        model.setData(new JournalBattleTableModel.Data(true, battles.subList(1, 4)));
        assertNull(model.seasonFilter());
        assertEquals(3, model.shownCount());
        assertEquals(List.of(calendar.season(14), calendar.season(16)), model.data().seasons());
    }

    @Test
    @DisplayName("Totals of the shown battles: ranking points without the running battle, points with it")
    void totals() throws Exception {
        assertTrue(service().execute(service().prepare(BattleLogTestFiles.season16Files("de")), ImportAnswers.defaults()).isSuccess());
        JournalBattleTableModel model = new JournalBattleTableModel();
        model.setData(JournalBattleTableModel.load(context.journal()));
        List<BattleSummary> all = model.data().battles();
        BattleListFilter none = BattleListFilter.NONE;

        long finishedRanking = all.stream().filter(b -> b.status() != BattleStatus.RUNNING)
                .mapToLong(BattleSummary::rankingPoints).sum();
        long own = all.stream().mapToLong(BattleSummary::ownPoints).sum();
        long opponent = all.stream().mapToLong(BattleSummary::opponentPoints).sum();
        assertEquals(new JournalBattleTableModel.Totals(finishedRanking, own, opponent), model.shownTotals(), "no filter");

        model.setFilter(none.withOpponentText("союз"));
        assertEquals(new JournalBattleTableModel.Totals(0, 4990, 2879), model.shownTotals(),
                "the running battle: points yes, ranking points no");

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS).withOpponentText("rakuen"));
        assertEquals(-286, model.shownTotals().rankingPoints(), "a defeat is subtracted");
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.WIN).withOpponentText("auge"));
        assertEquals(851, model.shownTotals().rankingPoints());

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS));
        long losses = all.stream().filter(b -> b.status() != BattleStatus.RUNNING && b.result() == BattleResult.LOSS)
                .mapToLong(BattleSummary::rankingPoints).sum();
        assertEquals(losses, model.shownTotals().rankingPoints());
        assertTrue(losses < 0);

        model.setFilter(none.withOpponentText("no such guild"));
        assertEquals(new JournalBattleTableModel.Totals(0, 0, 0), model.shownTotals());
    }

    @Test
    @DisplayName("Totals follow the season filter")
    void totalsBySeason() {
        JournalBattleTableModel model = new JournalBattleTableModel();
        model.setData(new JournalBattleTableModel.Data(true, List.of(
                battle(1, LocalDate.of(2026, 10, 15)),    // season 17
                battle(2, LocalDate.of(2026, 9, 24)),     // season 16
                battle(3, LocalDate.of(2026, 9, 21)))));  // season 16
        assertEquals(new JournalBattleTableModel.Totals(2400, 9000, 6000), model.shownTotals());
        model.setSeasonFilter(16);
        assertEquals(new JournalBattleTableModel.Totals(1600, 6000, 4000), model.shownTotals());
        model.setData(JournalBattleTableModel.Data.EMPTY);
        assertEquals(new JournalBattleTableModel.Totals(0, 0, 0), model.shownTotals());
    }

    private static BattleSummary battle(int id, LocalDate date) {
        return new BattleSummary(id, date, new GuildRef("Opponent " + id, 1, 1000 + id), BattleResult.WIN,
                BattleStatus.FINISHED, 800, 3000, 2000, Set.of(LogDirection.ATTACK),
                SeasonCalendar.standard().numberOf(date).orElse(null));
    }

    private static List<LocalDate> dates(JournalBattleTableModel model) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            dates.add((LocalDate) model.getValueAt(row, Column.DATE.ordinal()));
        }
        return dates;
    }

    @Test
    @DisplayName("Config.lastJournalImportDir is kept and used as the chooser's start folder")
    void lastImportDir() throws Exception {
        String previous = Config.getLastJournalImportDir();
        try {
            Path folder = Files.createDirectories(workspace.resolve("downloads"));
            Config.setLastJournalImportDir(folder.toString());
            assertEquals(folder.toString(), Config.getLastJournalImportDir());
            assertEquals(folder, JournalActions.startDirectory());

            Config.setLastJournalImportDir(workspace.resolve("gone").toString());
            Path home = Path.of(System.getProperty("user.home"));
            Path start = JournalActions.startDirectory();
            assertTrue(start.equals(home) || start.equals(home.resolve("Downloads")), start.toString());
        } finally {
            Config.setLastJournalImportDir(previous);
        }
    }

    private static int rowOf(JournalBattleTableModel model, LocalDate date) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (date.equals(model.getValueAt(row, Column.DATE.ordinal()))) {
                return row;
            }
        }
        throw new AssertionError("no row for " + date);
    }
}
