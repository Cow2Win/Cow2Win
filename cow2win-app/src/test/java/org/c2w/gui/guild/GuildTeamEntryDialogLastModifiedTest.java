package org.c2w.gui.guild;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.common.DateValue;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The "Last modified" column of the live lineup dialog (never shown): last column for heroes and
 * titans, the team's date, empty for a new line and for an unknown date, which sorts last.
 * Skipped without a display.
 */
class GuildTeamEntryDialogLastModifiedTest {

    private static final int MEMBER_COLUMN = 2;
    private static final LocalDate OLD = LocalDate.of(2026, 9, 1);
    private static final LocalDate NEWER = LocalDate.of(2026, 10, 1);

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private JDialog dialog;

    @BeforeEach
    void openWorkspace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
    }

    @AfterEach
    void closeWorkspace() throws Exception {
        if (context == null) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Heroes and titans: the last column is \"Last modified\"")
    void lastColumnForHeroesAndTitans() throws Exception {
        String header = LanguageService.displayName("memberOverview.lastModified");

        SwingUtilities.invokeAndWait(() -> dialog = new GuildHeroEntryDialog(null, context));
        assertEquals(header, lastColumnName(((GuildHeroEntryDialog) dialog).table()));
        SwingUtilities.invokeAndWait(() -> dialog.dispose());

        SwingUtilities.invokeAndWait(() -> dialog = new GuildTitanEntryDialog(null, context));
        JTable table = ((GuildTitanEntryDialog) dialog).table();
        assertEquals(header, lastColumnName(table));
        assertEquals(DateValue.class, table.getColumnClass(table.getColumnCount() - 1));
    }

    @Test
    @DisplayName("Shows the team's date; an unknown date is empty and sorts after every known one")
    void showsDateAndSortsUnknownLast() throws Exception {
        GuildHeroEntryDialog heroes = open();
        JTable table = heroes.table();
        int column = table.getColumnCount() - 1;

        assertEquals(new DateValue(OLD), valueOf(table, "old", column));
        assertEquals(DateValue.shortDate(OLD), valueOf(table, "old", column).toString());
        assertEquals(DateValue.NONE, valueOf(table, "unknown", column));
        assertEquals("", valueOf(table, "unknown", column).toString());

        onEdt(() -> table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(column, SortOrder.ASCENDING))));
        assertEquals(List.of("old", "newer", "unknown"), memberOrder(table));
    }

    @Test
    @DisplayName("A new, empty line shows no date - although its draft already carries today")
    void newLineIsEmpty() throws Exception {
        GuildHeroEntryDialog heroes = open();
        onEdt(heroes::addRowForTest);

        JTable table = heroes.table();
        Object value = table.getValueAt(table.getSelectedRow(), table.getColumnCount() - 1);
        assertEquals(DateValue.NONE, value);
    }

    @Test
    @DisplayName("Changing the team in the editor shows today's date in its line right away")
    void editShowsToday() throws Exception {
        GuildHeroEntryDialog heroes = open();
        JTable table = heroes.table();
        int column = table.getColumnCount() - 1;
        onEdt(() -> {
            selectMember(table, "old");
            ((JTextField) heroes.teamEditor().getComponent(0)).setText("60000");
        });

        assertEquals(new DateValue(LocalDate.now()), valueOf(table, "old", column));
    }

    // ------------------------------------------------------------------ helpers

    /** A guild with three members, one hero team each: modified {@link #OLD}, {@link #NEWER} and never. */
    private GuildHeroEntryDialog open() throws Exception {
        Hero hero = context.catalog().heroes().findAll().get(0);
        List<GuildMember> members = new ArrayList<>();
        List<Lineup.Entry> entries = new ArrayList<>();
        String[] ids = {"newer", "unknown", "old"};
        LocalDate[] dates = {NEWER, null, OLD};
        for (int i = 0; i < ids.length; i++) {
            members.add(new GuildMember(ids[i], ids[i], List.of(new HeroTeam(ids[i], 0, List.of(hero), 50_000, dates[i])), List.of()));
            entries.add(new Lineup.Entry("bastion", ids[i], Lineup.TeamType.HERO, 0));
        }
        Guild guild = context.guild();
        guildService.saveGuild(new Guild(guild.id(), guild.name(), members, guild.gameGuildId()));
        LineupRepository.save(new Lineup(guild.id(), guild.name(), "", LocalDateTime.now(), entries),
                LineupFiles.originalPathFor(context.guildFilePath().getParent()));

        SwingUtilities.invokeAndWait(() -> dialog = new GuildHeroEntryDialog(null, context));
        return (GuildHeroEntryDialog) dialog;
    }

    private static String lastColumnName(JTable table) {
        return table.getColumnName(table.getColumnCount() - 1);
    }

    private static Object valueOf(JTable table, String member, int column) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (member.equals(table.getValueAt(row, MEMBER_COLUMN))) {
                return table.getValueAt(row, column);
            }
        }
        throw new IllegalArgumentException("No line of " + member);
    }

    private static void selectMember(JTable table, String member) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (member.equals(table.getValueAt(row, MEMBER_COLUMN))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new IllegalArgumentException("No line of " + member);
    }

    private static List<String> memberOrder(JTable table) {
        List<String> order = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            order.add((String) table.getValueAt(row, MEMBER_COLUMN));
        }
        return order;
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
}
