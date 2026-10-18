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
     * "menu title key: entries" of the menu bar: "File", "Guild" and the journal - the process
     * stages have no menu there (see {@link #EXPECTED_STAGE_ACTIONS}).
     */
    private static final List<String> EXPECTED = List.of(
            "menu.file: SETTINGS, COWSCORE, CATALOG_UPDATE, SHOW_LOG, OPEN_HERO_WARS",
            "menu.guild: NEW_GUILD, REMOVE_GUILD, ---, EDIT_GUILD_NOTE",
            "menu.journal: JOURNAL_BATTLES, ---, JOURNAL_SYNC, JOURNAL_BUILD_TEAMS, ---, JOURNAL_PLAYERS");

    /**
     * "title key: entries" of the process stages' action lists, unchanged from their former menus.
     * A submenu (group) is written as {title key: entries}.
     */
    private static final List<String> EXPECTED_STAGE_ACTIONS = List.of(
            "menu.input: ADD_MEMBER, REMOVE_MEMBER, ---, OPEN_GUILD_TEAM_ENTRY",
            "menu.concept: {menu.lineup: NEW_LINEUP, REMOVE_LINEUP, CLEAR_LINEUP, COPY_LINEUP}, ---, "
                    + "RUN_ALGORITHM, COMPARE_LINEUPS, ---, SHOW_TEAMS",
            "menu.output: OPEN_CHANGE_PLAN, GENERATE_REPORT, APPLY_TO_LIVE");

    /** Deliberately toolbar-only actions - the only ones neither in a menu nor in a stage action list. */
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
    @DisplayName("Menu titles, entries and separators match the expected menu bar - three menus, the journal last")
    void structureMatchesExpectedMenuBar() {
        MainActions actions = allActions();

        MainMenuBar menuBar = new MainMenuBar(actions, context);

        assertEquals(3, menuBar.getMenuCount());
        assertSame(menuBar.journalMenu(), menuBar.getMenu(2));
        assertEquals(EXPECTED.size(), menuBar.getMenuCount());
        for (int i = 0; i < EXPECTED.size(); i++) {
            String[] parts = EXPECTED.get(i).split(": ", 2);
            JMenu menu = menuBar.getMenu(i);
            assertEquals(LanguageService.displayName(parts[0]), menu.getText(), "title of menu " + i);
            assertEquals(parts[1], describeEntries(menu, actions), "entries of " + parts[0]);
        }
    }

    @Test
    @DisplayName("The menu bar has no menu for the process stages input, concept and output")
    void noProcessStageMenus() {
        MainMenuBar menuBar = new MainMenuBar(allActions(), context);
        for (Stage stage : List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT)) {
            String title = LanguageService.displayName(stage.menuTextKey());
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                assertNotEquals(title, menuBar.getMenu(i).getText(), stage.name());
            }
            assertTrue(MainMenuBar.MENUS.stream().noneMatch(spec -> spec.titleKey().equals(stage.menuTextKey())),
                    stage.name());
        }
    }

    @Test
    @DisplayName("The action lists of input, concept and output are unchanged: entries, order, separators and the lineup group")
    void stageActionListsUnchanged() {
        assertEquals(EXPECTED_STAGE_ACTIONS.size(), MainMenuBar.STAGE_ACTIONS.size());
        List<String> actual = new ArrayList<>();
        for (Stage stage : List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT)) {
            MainMenuBar.MenuSpec spec = MainMenuBar.menuFor(stage);
            actual.add(spec.titleKey() + ": " + describeSpec(spec));
        }
        assertEquals(EXPECTED_STAGE_ACTIONS, actual);
    }

    @Test
    @DisplayName("Every action in a spec titled after a stage (menu or stage action list), submenus included, is of that stage - the guild, the journal and all three process stages have one")
    void stageMenusHoldOnlyTheirStage() {
        Set<Stage> stageMenusFound = EnumSet.noneOf(Stage.class);
        for (MainMenuBar.MenuSpec spec : allSpecs()) {
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
        assertEquals(4, journal.size());
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
    @DisplayName("Completeness: every action is in a menu or a stage action list, except the toolbar-only ones")
    void everyActionIsInAMenu() {
        Set<ActionId> inMenus = EnumSet.noneOf(ActionId.class);
        allSpecs().forEach(spec -> inMenus.addAll(spec.allActionIds()));

        assertEquals(TOOLBAR_ONLY, MainMenuBar.TOOLBAR_ONLY);
        for (ActionId id : ActionId.values()) {
            assertEquals(!TOOLBAR_ONLY.contains(id), inMenus.contains(id),
                    id + (TOOLBAR_ONLY.contains(id) ? " is toolbar-only but in a menu or action list"
                            : " is in no menu and no action list"));
        }
    }

    @Test
    @DisplayName("No action appears more than once in the menu bar and the stage action lists together, submenus included")
    void noDuplicateEntries() {
        Set<ActionId> seen = new HashSet<>();
        for (MainMenuBar.MenuSpec spec : allSpecs()) {
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

    /** The specs of the menu bar and of the stage action lists. */
    private static List<MainMenuBar.MenuSpec> allSpecs() {
        List<MainMenuBar.MenuSpec> specs = new ArrayList<>(MainMenuBar.MENUS);
        specs.addAll(MainMenuBar.STAGE_ACTIONS);
        return specs;
    }

    /** The entries of {@code spec} without Swing: action names, "---", submenus as {title key: entries}. */
    private static String describeSpec(MainMenuBar.MenuSpec spec) {
        List<String> entries = new ArrayList<>();
        for (MainMenuBar.Entry entry : spec.entries()) {
            switch (entry) {
                case MainMenuBar.Separator separator -> entries.add(SEPARATOR);
                case MainMenuBar.MenuSpec submenu -> entries.add("{" + submenu.titleKey() + ": " + describeSpec(submenu) + "}");
                case MainMenuBar.Item item -> entries.add(item.id().name());
            }
        }
        return String.join(", ", entries);
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
