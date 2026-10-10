package org.c2w.gui;

import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test of {@link MainMenuBar}: menus, submenus, entry order and separators.
 * Headless - the menu bar is only created, never shown.
 */
class MainMenuBarTest {

    private static final String SEPARATOR = "---";

    /**
     * "menu title key: entries" - the target structure of milestone M1 (steps 1.1-1.5)
     * plus the "Guild" menu: "File", "Guild" and one menu per process stage. A submenu is written as {title: entries}.
     */
    private static final List<String> EXPECTED = List.of(
            "menu.file: SETTINGS, COWSCORE, SHOW_LOG, OPEN_HERO_WARS",
            "menu.guild: NEW_GUILD, REMOVE_GUILD",
            "menu.input: ADD_MEMBER, REMOVE_MEMBER, ---, OPEN_GUILD_TEAM_ENTRY",
            "menu.concept: {" + LanguageService.displayName("menu.lineup") + ": NEW_LINEUP, REMOVE_LINEUP, CLEAR_LINEUP}, ---, "
                    + "RUN_ALGORITHM, COMPARE_LINEUPS, ---, SHOW_TEAMS",
            "menu.output: OPEN_CHANGE_PLAN, GENERATE_REPORT, APPLY_TO_LIVE",
            "menu.journal: JOURNAL_IMPORT, JOURNAL_SYNC, ---, JOURNAL_BATTLES, JOURNAL_BUILD_TEAMS, ---, "
                    + "JOURNAL_PLAYERS, JOURNAL_SEASONS, JOURNAL_NAME_MAPPINGS");

    /** Deliberately toolbar-only actions - the only ones not in any menu. */
    private static final Set<ActionId> TOOLBAR_ONLY = Set.of(ActionId.SAVE_GUILD, ActionId.SAVE_LINEUP);

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;

    @BeforeEach
    void openWorkspace() {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Menu titles, submenus, entries and separators match the expected menu bar - six menus, the journal last")
    void structureMatchesExpectedMenuBar() {
        MainActions actions = allActions();

        MainMenuBar menuBar = new MainMenuBar(actions, context);

        assertEquals(6, menuBar.getMenuCount());
        assertSame(menuBar.journalMenu(), menuBar.getMenu(5));
        assertEquals(EXPECTED.size(), menuBar.getMenuCount());
        for (int i = 0; i < EXPECTED.size(); i++) {
            String[] parts = EXPECTED.get(i).split(": ", 2);
            JMenu menu = menuBar.getMenu(i);
            assertEquals(LanguageService.displayName(parts[0]), menu.getText(), "title of menu " + i);
            assertEquals(parts[1], describeEntries(menu, actions), "entries of " + parts[0]);
        }
    }

    @Test
    @DisplayName("Every action in a menu titled after a stage, submenus included, is of that stage - the guild and all three process menus exist")
    void stageMenusHoldOnlyTheirStage() {
        Set<Stage> stageMenusFound = EnumSet.noneOf(Stage.class);
        for (MainMenuBar.MenuSpec spec : MainMenuBar.MENUS) {
            for (Stage stage : Stage.values()) {
                if (!spec.titleKey().equals(stage.menuTextKey())) {
                    continue;
                }
                stageMenusFound.add(stage);
                for (ActionId id : spec.allActionIds()) {
                    assertEquals(stage, id.stage(), id + " in menu " + spec.titleKey());
                }
            }
        }
        assertEquals(EnumSet.of(Stage.GUILD, Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT, Stage.JOURNAL), stageMenusFound);
    }

    @Test
    @DisplayName("The stage menus input and concept hold no journal action; the journal menu holds all of them")
    void journalActionsOnlyInJournalMenu() {
        for (Stage stage : List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT)) {
            assertTrue(MainMenuBar.menuFor(stage).allActionIds().stream().noneMatch(id -> id.stage() == Stage.JOURNAL),
                    stage.name());
        }
        Set<ActionId> journal = EnumSet.noneOf(ActionId.class);
        for (ActionId id : ActionId.values()) {
            if (id.stage() == Stage.JOURNAL) {
                journal.add(id);
            }
        }
        assertEquals(7, journal.size());
        assertEquals(journal, EnumSet.copyOf(MainMenuBar.menuFor(Stage.JOURNAL).allActionIds()));
        assertNull(Stage.JOURNAL.stageTextKey(), "the journal is no process stage (no tile, no action list)");
    }

    @Test
    @DisplayName("The journal menu is shown only for a guild master's guild - hidden without a guild and without the flag, updated on every guild change")
    void journalMenuFollowsGuildMaster() throws Exception {
        MainMenuBar menuBar = new MainMenuBar(allActions(), context);
        assertFalse(menuBar.journalMenu().isVisible(), "no guild open");

        guildService.createGuild("Plain", false);
        guildService.createGuild("Master", true);
        guildService.switchToGuild("Plain");
        assertFalse(menuBar.journalMenu().isVisible(), "guild without the flag");

        guildService.switchToGuild("Master");
        assertTrue(menuBar.journalMenu().isVisible());

        guildService.switchToGuild("Plain");
        assertFalse(menuBar.journalMenu().isVisible());
    }

    @Test
    @DisplayName("Completeness: every action is in a menu, except the toolbar-only ones")
    void everyActionIsInAMenu() {
        Set<ActionId> inMenus = EnumSet.noneOf(ActionId.class);
        MainMenuBar.MENUS.forEach(spec -> inMenus.addAll(spec.allActionIds()));

        assertEquals(TOOLBAR_ONLY, MainMenuBar.TOOLBAR_ONLY);
        for (ActionId id : ActionId.values()) {
            assertEquals(!TOOLBAR_ONLY.contains(id), inMenus.contains(id),
                    id + (TOOLBAR_ONLY.contains(id) ? " is toolbar-only but in a menu" : " is in no menu"));
        }
    }

    @Test
    @DisplayName("No action appears more than once in the whole menu bar, submenus included")
    void noDuplicateEntries() {
        Set<ActionId> seen = new HashSet<>();
        for (MainMenuBar.MenuSpec spec : MainMenuBar.MENUS) {
            for (ActionId id : spec.allActionIds()) {
                assertTrue(seen.add(id), id + " appears more than once");
            }
        }
    }

    @Test
    @DisplayName("Creating and removing a guild are in the guild menu, the team assignment is input, the CowScore settings stay master data")
    void stageAssignment() {
        assertEquals(Stage.GUILD, ActionId.NEW_GUILD.stage());
        assertEquals(Stage.GUILD, ActionId.REMOVE_GUILD.stage());
        assertEquals(Stage.INPUT, ActionId.OPEN_GUILD_TEAM_ENTRY.stage());
        assertEquals(Stage.MASTER_DATA, ActionId.COWSCORE.stage());
    }

    private static MainActions allActions() {
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        return actions;
    }

    private static String describeEntries(JMenu menu, MainActions actions) {
        List<String> entries = new ArrayList<>();
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JSeparator) {
                entries.add(SEPARATOR);
            } else if (component instanceof JMenu submenu) {
                entries.add("{" + submenu.getText() + ": " + describeEntries(submenu, actions) + "}");
            } else {
                JMenuItem item = (JMenuItem) component;
                AppAction action = (AppAction) item.getAction();
                assertSame(actions.get(action.id()), action);
                assertEquals(AppAction.menuText(action.id()), item.getText());
                assertNull(item.getToolTipText(), "menu entries have no tooltip: " + action.id());
                entries.add(action.id().name());
            }
        }
        return String.join(", ", entries);
    }
}
