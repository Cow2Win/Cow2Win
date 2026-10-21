package org.c2w.gui.guild;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.common.InfoArea;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * "New team" of the guild-wide hero team dialog (never shown): a new line goes to the next free
 * fortification if the preset one is full, and the keyboard hint below the toolbar. Skipped
 * without a display.
 */
class GuildTeamEntryDialogAddRowTest {

    /** Columns of the dialog's table (#, fortification, member, ...). */
    private static final int FORT_COLUMN = 1;
    /** Fortification filter entries before the fortifications: "all", "none". */
    private static final int FILTER_FIRST_FORT = 2;
    /** Hero team slots of a member. */
    private static final int TEAMS_PER_MEMBER = 3;

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private GuildHeroEntryDialog dialog;

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
    @DisplayName("Filter on a full fortification: filter and new line jump to the next free one")
    void fullFilteredFortificationJumpsToNextFree() throws Exception {
        open(Map.of("bastion", 5));
        onEdt(() -> setFortFilter("bastion"));
        Fortification expected = after("bastion");

        onEdt(dialog::addRowForTest);

        assertSame(expected, dialog.fortFilter().getSelectedItem());
        assertNewLineAt(expected);
    }

    @Test
    @DisplayName("Filter on a fortification with room: the new line goes there, the filter stays")
    void filteredFortificationWithRoomUnchanged() throws Exception {
        open(Map.of("bastion", 4));
        onEdt(() -> setFortFilter("bastion"));

        onEdt(dialog::addRowForTest);

        assertSame(fortification("bastion"), dialog.fortFilter().getSelectedItem());
        assertEquals(5, dialog.table().getRowCount());
        assertNewLineAt(fortification("bastion"));
    }

    @Test
    @DisplayName("Every fortification full: the new line has no fortification, the filter shows \"none\"")
    void allFullGoesToNone() throws Exception {
        Map<String, Integer> full = new LinkedHashMap<>();
        fortifications().forEach(f -> full.put(f.id(), f.capacity()));
        open(full);
        onEdt(() -> setFortFilter("bastion"));

        onEdt(dialog::addRowForTest);

        assertSame(dialog.fortFilter().getItemAt(1), dialog.fortFilter().getSelectedItem());
        assertEquals(1, dialog.table().getRowCount());
        assertEquals(0, dialog.table().getSelectedRow());
        assertNull(dialog.fortCombo().getSelectedItem());
        assertTrue(dialog.isDirty());
    }

    @Test
    @DisplayName("Filter \"all\", last chosen fortification full: the new line goes to the next free one, the filter stays")
    void lastSelectedFullGoesToNextFree() throws Exception {
        open(Map.of("bastion", 4, "foundry", 1));
        Fortification expected = after("bastion");
        // Moves the foundry line to the bastion in the editor: bastion 5/5, last chosen fortification.
        onEdt(() -> {
            selectLineAt("foundry");
            dialog.fortCombo().setSelectedItem(fortification("bastion"));
        });

        onEdt(dialog::addRowForTest);

        assertSame(dialog.fortFilter().getItemAt(0), dialog.fortFilter().getSelectedItem());
        assertNewLineAt(expected);

        onEdt(dialog::addRowForTest);
        assertNewLineAt(expected); // the next new line continues there
    }

    @Test
    @DisplayName("Below the toolbar: the keyboard hint with a hero example in an InfoArea")
    void showsKeyboardHint() throws Exception {
        open(Map.of());

        List<InfoArea> infos = new ArrayList<>();
        collect(dialog.getContentPane(), infos);
        assertEquals(1, infos.size());
        assertEquals(LanguageService.displayName("guildEntry.keyboardHint.hero"), infos.get(0).getText());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Opens the dialog on a guild with {@code teamsPerFort} lineup entries per fortification id -
     * one hero team each, up to {@link #TEAMS_PER_MEMBER} per member (a guild has at most 30 members).
     */
    private void open(Map<String, Integer> teamsPerFort) throws Exception {
        Hero hero = context.catalog().heroes().findAll().get(0);
        List<String> forts = new ArrayList<>();
        teamsPerFort.forEach((fortId, count) -> forts.addAll(Collections.nCopies(count, fortId)));
        List<GuildMember> members = new ArrayList<>();
        List<Lineup.Entry> entries = new ArrayList<>();
        for (int first = 0; first < forts.size(); first += TEAMS_PER_MEMBER) {
            String id = "m" + first;
            List<HeroTeam> teams = new ArrayList<>();
            for (int index = 0; index < TEAMS_PER_MEMBER && first + index < forts.size(); index++) {
                teams.add(new HeroTeam(id, index, List.of(hero), 50_000));
                entries.add(new Lineup.Entry(forts.get(first + index), id, Lineup.TeamType.HERO, index));
            }
            members.add(new GuildMember(id, id, teams, List.of()));
        }
        Guild guild = context.guild();
        guildService.saveGuild(new Guild(guild.id(), guild.name(), members, guild.gameGuildId()));
        LineupRepository.save(new Lineup(guild.id(), guild.name(), "", LocalDateTime.now(), entries),
                LineupFiles.originalPathFor(context.guildFilePath().getParent()));

        SwingUtilities.invokeAndWait(() -> dialog = new GuildHeroEntryDialog(null, context));
    }

    /** The new line is the selected one and stands at {@code expected}. */
    private void assertNewLineAt(Fortification expected) {
        JTable table = dialog.table();
        int selected = table.getSelectedRow();
        assertTrue(selected >= 0);
        assertEquals(LanguageService.displayName(expected.id()), table.getValueAt(selected, FORT_COLUMN));
        assertSame(expected, dialog.fortCombo().getSelectedItem());
        assertTrue(dialog.isDirty());
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** The hero fortifications in the filter's order. */
    private List<Fortification> fortifications() throws Exception {
        if (dialog == null) {
            // Before the dialog exists (see allFullGoesToNone): only the ids and capacities matter.
            return FortificationRepository.findAll().stream()
                    .filter(f -> f.type() == FortificationType.HERO).toList();
        }
        List<Fortification> order = new ArrayList<>();
        JComboBox<Object> filter = dialog.fortFilter();
        for (int i = FILTER_FIRST_FORT; i < filter.getItemCount(); i++) {
            order.add((Fortification) filter.getItemAt(i));
        }
        return order;
    }

    /** The fortification right after {@code fortId} in the filter (wrapping around). */
    private Fortification after(String fortId) throws Exception {
        List<Fortification> order = fortifications();
        return order.get((order.indexOf(fortification(fortId)) + 1) % order.size());
    }

    private void setFortFilter(String fortId) {
        dialog.fortFilter().setSelectedItem(fortification(fortId));
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
            if (LanguageService.displayName(fortId).equals(table.getValueAt(row, FORT_COLUMN))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new IllegalArgumentException("No line at " + fortId);
    }

    private static void collect(Container container, List<InfoArea> found) {
        for (Component child : container.getComponents()) {
            if (child instanceof InfoArea info) {
                found.add(info);
            } else if (child instanceof Container nested) {
                collect(nested, found);
            }
        }
    }
}
