package org.c2w.gui;

import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test of {@link MainMenuBar}: menus, entry order and separators exactly as
 * before the GUI rework (M0). Headless - the menu bar is only created, never shown.
 */
class MainMenuBarTest {

    private static final String SEPARATOR = "---";

    /** "menu title key: entries" - the menu bar as it was built by Cow2Frame/JournalActions before M0. */
    private static final List<String> EXPECTED = List.of(
            "menu.file: SETTINGS, COWSCORE_HEROES, COWSCORE_TITANS, COWSCORE_PETS, COWSCORE_WAR_FLAGS, SHOW_LOG, OPEN_HERO_WARS",
            "menu.guild: NEW_GUILD, OPEN_GUILD_EDITOR, REMOVE_GUILD",
            "menu.lineup: NEW_LINEUP, REMOVE_LINEUP, CLEAR_LINEUP",
            "menu.journal: JOURNAL_IMPORT, JOURNAL_BATTLES, JOURNAL_BUILD_TEAMS, JOURNAL_SYNC, ---, "
                    + "JOURNAL_PLAYERS, JOURNAL_SEASONS, JOURNAL_NAME_MAPPINGS");

    @Test
    @DisplayName("Menu titles, entries and separators match the menu bar before the rework")
    void structureMatchesPreviousMenuBar() {
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }

        MainMenuBar menuBar = new MainMenuBar(actions);

        assertEquals(EXPECTED.size(), menuBar.getMenuCount());
        for (int i = 0; i < EXPECTED.size(); i++) {
            String[] parts = EXPECTED.get(i).split(": ", 2);
            JMenu menu = menuBar.getMenu(i);
            assertEquals(LanguageService.displayName(parts[0]), menu.getText(), "title of menu " + i);
            assertEquals(parts[1], describeEntries(menu, actions), "entries of " + parts[0]);
        }
    }

    private static String describeEntries(JMenu menu, MainActions actions) {
        List<String> entries = new ArrayList<>();
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JSeparator) {
                entries.add(SEPARATOR);
            } else {
                JMenuItem item = (JMenuItem) component;
                AppAction action = (AppAction) item.getAction();
                assertSame(actions.get(action.id()), action);
                assertEquals(LanguageService.displayName(action.id().textKey()), item.getText());
                assertNull(item.getToolTipText(), "menu entries have no tooltip: " + action.id());
                entries.add(action.id().name());
            }
        }
        return String.join(", ", entries);
    }
}
