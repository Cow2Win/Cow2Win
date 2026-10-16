package org.c2w.gui.guild;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
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
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Fortification filter and search of the guild-wide hero team dialog (never shown): which line is
 * shown and selected after a filter change. Skipped without a display.
 */
class GuildTeamEntryDialogFilterTest {

    /** Columns of the dialog's table (#, fortification, member, ...). */
    private static final int FORT_COLUMN = 1;
    private static final int MEMBER_COLUMN = 2;

    private static final List<Lineup.Entry> ENTRIES = List.of(
            new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0),
            new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 1),
            new Lineup.Entry("foundry", "bert", Lineup.TeamType.HERO, 0),
            new Lineup.Entry("foundry", "carl", Lineup.TeamType.HERO, 0));

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildHeroEntryDialog dialog;

    @BeforeEach
    void openWorkspace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");

        Hero hero = context.catalog().heroes().findAll().get(0);
        // Bastion: Anna's two teams; foundry: Bert and Carl; citadel: no team.
        GuildMember anna = new GuildMember("anna", "Anna", List.of(new HeroTeam("anna", 0, List.of(hero), 100_000),
                new HeroTeam("anna", 1, List.of(hero), 90_000)), List.of());
        GuildMember bert = new GuildMember("bert", "Bert", List.of(new HeroTeam("bert", 0, List.of(hero), 70_000)), List.of());
        GuildMember carl = new GuildMember("carl", "Carl", List.of(new HeroTeam("carl", 0, List.of(hero), 60_000)), List.of());
        Guild guild = context.guild();
        guildService.saveGuild(new Guild(guild.id(), guild.name(), List.of(anna, bert, carl), guild.gameGuildId()));
        LineupRepository.save(new Lineup(guild.id(), guild.name(), "", LocalDateTime.now(), ENTRIES), livePath());

        SwingUtilities.invokeAndWait(() -> dialog = new GuildHeroEntryDialog(null, context));
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
    @DisplayName("Line at foundry selected, filter on bastion: only bastion lines, the first one selected and in the editor")
    void filterSelectsFirstMatchingLine() throws Exception {
        onEdt(() -> {
            selectLineAt("foundry");
            setFortFilter("bastion");
        });

        JTable table = dialog.table();
        assertEquals(2, table.getRowCount());
        for (int row = 0; row < table.getRowCount(); row++) {
            assertEquals(fortName("bastion"), table.getValueAt(row, FORT_COLUMN));
        }
        assertEquals(0, table.getSelectedRow());
        assertEquals("bastion", ((Fortification) dialog.fortCombo().getSelectedItem()).id());
        assertEquals("Anna", memberName(dialog.memberCombo().getSelectedItem()));
        assertNotNull(dialog.teamEditor());
    }

    @Test
    @DisplayName("Filter on a fortification without teams: nothing selected, the disabled fortification combo shows it; a new team goes there")
    void emptyListShowsFilteredFortification() throws Exception {
        onEdt(() -> setFortFilter("citadel"));

        assertEquals(0, dialog.table().getRowCount());
        assertEquals(-1, dialog.table().getSelectedRow());
        assertNull(dialog.teamEditor(), "the editor shows \"nothing selected\"");
        assertEquals("citadel", ((Fortification) dialog.fortCombo().getSelectedItem()).id());
        assertFalse(dialog.fortCombo().isEnabled());
        assertFalse(dialog.isDirty(), "showing the fortification changes no team");

        onEdt(() -> setFortFilter(null));
        assertEquals(4, dialog.table().getRowCount(), "no team was moved to the citadel");
        onEdt(() -> setFortFilter("citadel"));

        onEdt(dialog::addRowForTest);

        assertEquals(1, dialog.table().getRowCount());
        assertEquals(0, dialog.table().getSelectedRow());
        assertEquals(fortName("citadel"), dialog.table().getValueAt(0, FORT_COLUMN));
        assertEquals("citadel", ((Fortification) dialog.fortCombo().getSelectedItem()).id());
        assertTrue(dialog.fortCombo().isEnabled());
    }

    @Test
    @DisplayName("Search text excluding the selected line: the first matching line is selected")
    void searchSelectsFirstMatchingLine() throws Exception {
        onEdt(() -> {
            selectLineAt("bastion");
            dialog.searchField().setText("bert");
        });

        JTable table = dialog.table();
        assertEquals(1, table.getRowCount());
        assertEquals(0, table.getSelectedRow());
        assertEquals("Bert", table.getValueAt(0, MEMBER_COLUMN));
        assertEquals("Bert", memberName(dialog.memberCombo().getSelectedItem()));
    }

    @Test
    @DisplayName("Line moved in the editor away from the filtered fortification: stays shown and selected until the next filter change")
    void editedLineKeptUntilFilterChange() throws Exception {
        onEdt(() -> {
            setFortFilter("bastion");
            dialog.fortCombo().setSelectedItem(fortification("foundry"));
        });

        JTable table = dialog.table();
        assertEquals(2, table.getRowCount());
        int selected = table.getSelectedRow();
        assertTrue(selected >= 0);
        assertEquals(fortName("foundry"), table.getValueAt(selected, FORT_COLUMN));
        assertEquals("foundry", ((Fortification) dialog.fortCombo().getSelectedItem()).id());

        onEdt(() -> ((TableRowSorter<?>) table.getRowSorter()).sort()); // e.g. a click on a column header
        assertEquals(2, table.getRowCount(), "re-sorting keeps the edited line");
        assertEquals(fortName("foundry"), table.getValueAt(table.getSelectedRow(), FORT_COLUMN));

        onEdt(() -> {
            setFortFilter(null);
            setFortFilter("bastion");
        });
        assertEquals(1, table.getRowCount());
        assertEquals(fortName("bastion"), table.getValueAt(0, FORT_COLUMN));
        assertEquals(0, table.getSelectedRow());
    }

    @Test
    @DisplayName("Saving after filtering keeps every team, also the hidden ones")
    void saveKeepsHiddenLines() throws Exception {
        boolean[] saved = new boolean[1];
        onEdt(() -> {
            setFortFilter("citadel");
            saved[0] = dialog.saveForTest();
        });

        assertTrue(saved[0]);
        assertEquals(new HashSet<>(ENTRIES), new HashSet<>(LineupRepository.load(livePath()).entries()));
        Set<Integer> powers = new HashSet<>();
        context.guild().members().forEach(m -> m.heroTeams().forEach(t -> powers.add(t.totalPower())));
        assertTrue(powers.containsAll(List.of(100_000, 90_000, 70_000, 60_000)), powers.toString());
    }

    // ------------------------------------------------------------------ helpers

    private Path livePath() {
        return LineupFiles.originalPathFor(context.guildFilePath().getParent());
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** Sets the fortification filter to {@code fortId}, or to "all" for null. */
    private void setFortFilter(String fortId) {
        JComboBox<Object> filter = dialog.fortFilter();
        filter.setSelectedItem(fortId == null ? filter.getItemAt(0) : fortification(fortId));
    }

    private Fortification fortification(String fortId) {
        JComboBox<Object> filter = dialog.fortFilter();
        for (int i = 0; i < filter.getItemCount(); i++) {
            if (filter.getItemAt(i) instanceof Fortification f && f.id().equals(fortId)) {
                return f;
            }
        }
        throw new IllegalArgumentException("No fortification " + fortId);
    }

    private void selectLineAt(String fortId) {
        JTable table = dialog.table();
        for (int row = 0; row < table.getRowCount(); row++) {
            if (fortName(fortId).equals(table.getValueAt(row, FORT_COLUMN))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new IllegalArgumentException("No line at " + fortId);
    }

    private static String fortName(String fortId) {
        return LanguageService.displayName(fortId);
    }

    private static String memberName(Object member) {
        return member == null ? null : ((MemberDraft) member).name;
    }
}
