package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.BattleStatus;
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
        assertTrue(service().execute(service().prepare(BattleLogTestFiles.files("de")), ImportAnswers.defaults()).isSuccess());
        JournalBattleTableModel model = new JournalBattleTableModel();

        model.setData(JournalBattleTableModel.load(context.journal()));

        assertEquals(6, model.getRowCount());
        assertEquals(Column.values().length, model.getColumnCount());
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
        assertEquals(1, model.getValueAt(0, Column.SEASON.ordinal()));

        int auge = rowOf(model, LocalDate.of(2026, 9, 24));
        assertEquals(851, model.getValueAt(auge, Column.RANKING_POINTS.ordinal()));
        assertEquals(JournalTexts.of("battleResult", BattleResult.WIN), model.getValueAt(auge, Column.RESULT.ordinal()));
        int rakuen = rowOf(model, LocalDate.of(2026, 9, 21));
        assertEquals(-286, model.getValueAt(rakuen, Column.RANKING_POINTS.ordinal()));

        int seasonId = model.data().seasons().get(0).id();
        model.setSeasonFilter(seasonId);
        assertEquals(6, model.getRowCount());
        model.setSeasonFilter(seasonId + 100);
        assertEquals(0, model.getRowCount());
        model.setData(JournalBattleTableModel.load(context.journal()));
        assertNull(model.seasonFilter(), "a filter on a season that does not exist is reset");
        assertEquals(6, model.getRowCount());
        model.setSeasonFilter(null);
        assertEquals(6, model.getRowCount());
    }

    @Test
    @DisplayName("Filters: opponent, period, status, result and combinations; 'n of m battles'")
    void filters() throws Exception {
        assertTrue(service().execute(service().prepare(BattleLogTestFiles.files("de")), ImportAnswers.defaults()).isSuccess());
        JournalBattleTableModel model = new JournalBattleTableModel();
        model.setData(JournalBattleTableModel.load(context.journal()));
        BattleListFilter none = BattleListFilter.NONE;

        model.setFilter(none.withOpponentText("  auge "));
        assertEquals(List.of(LocalDate.of(2026, 9, 24)), dates(model), "contains, case-insensitive");
        model.setFilter(none.withOpponentText("SOJUS"));
        assertEquals(0, model.shownCount());
        model.setFilter(none.withOpponentText("союз"));
        assertEquals(1, model.shownCount(), "Cyrillic, lower case");

        model.setFilter(none.withPeriod(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 24)));
        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 17)),
                dates(model), "both ends included");
        model.setFilter(none.withPeriod(LocalDate.of(2026, 9, 25), null));
        assertEquals(2, model.shownCount(), "open end");
        model.setFilter(none.withPeriod(null, LocalDate.of(2026, 9, 14)));
        assertEquals(1, model.shownCount(), "open start");

        model.setFilter(none.withStatus(BattleListFilter.StatusFilter.RUNNING));
        assertEquals(List.of(LocalDate.of(2026, 10, 1)), dates(model));
        model.setFilter(none.withStatus(BattleListFilter.StatusFilter.FINISHED));
        assertEquals(5, model.shownCount());

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.WIN));
        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 17)), dates(model));
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS));
        assertEquals(3, model.shownCount());
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.DRAW));
        assertEquals(0, model.shownCount(), "a running battle is no draw");

        model.setFilter(none.withResult(BattleListFilter.ResultFilter.WIN).withPeriod(LocalDate.of(2026, 9, 20), null));
        assertEquals(List.of(LocalDate.of(2026, 9, 24)), dates(model));
        model.setFilter(none.withResult(BattleListFilter.ResultFilter.LOSS).withOpponentText("r")
                .withStatus(BattleListFilter.StatusFilter.FINISHED));
        assertEquals(List.of(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 21)), dates(model),
                "Rebel Alliance, Rakuen (Elysium has no r)");
        assertEquals(JournalTexts.text("journal.battles.count", "2", "6"), model.countText());
        assertEquals(6, model.totalCount());

        int seasonId = model.data().seasons().get(0).id();
        model.setSeasonFilter(seasonId);
        assertEquals(2, model.shownCount(), "the season filter keeps the other filters");
        model.setFilter(BattleListFilter.NONE);
        assertEquals(6, model.shownCount());
        assertEquals(-1, model.rowOf(999));
        assertEquals(0, model.rowOf(model.battle(0).battleId()));
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
