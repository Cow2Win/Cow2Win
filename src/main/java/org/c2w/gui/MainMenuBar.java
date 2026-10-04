package org.c2w.gui;

import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static org.c2w.gui.action.ActionId.*;

/**
 * The main window's menu bar, built only from the actions registered in a
 * {@link MainActions} - no handler logic here. {@link #MENUS} describes its structure.
 */
public final class MainMenuBar extends JMenuBar {

    /** One menu: language file key of its title and its entries, {@code null} standing for a separator. */
    record MenuSpec(String titleKey, List<ActionId> entries) {
    }

    /** Placeholder for a separator in {@link MenuSpec#entries()}. */
    static final ActionId SEPARATOR = null;

    static final List<MenuSpec> MENUS = List.of(
            new MenuSpec("menu.file", Arrays.asList(SETTINGS, COWSCORE_HEROES, COWSCORE_TITANS, COWSCORE_PETS,
                    COWSCORE_WAR_FLAGS, SHOW_LOG, OPEN_HERO_WARS)),
            new MenuSpec("menu.guild", Arrays.asList(NEW_GUILD, OPEN_GUILD_EDITOR, REMOVE_GUILD)),
            new MenuSpec("menu.lineup", Arrays.asList(NEW_LINEUP, REMOVE_LINEUP, CLEAR_LINEUP)),
            new MenuSpec("menu.journal", Arrays.asList(JOURNAL_IMPORT, JOURNAL_BATTLES, JOURNAL_BUILD_TEAMS,
                    JOURNAL_SYNC, SEPARATOR, JOURNAL_PLAYERS, JOURNAL_SEASONS, JOURNAL_NAME_MAPPINGS)));

    public MainMenuBar(MainActions actions) {
        for (MenuSpec spec : MENUS) {
            JMenu menu = new JMenu(LanguageService.displayName(spec.titleKey()));
            for (ActionId id : spec.entries()) {
                if (id == SEPARATOR) {
                    menu.addSeparator();
                } else {
                    JMenuItem item = new JMenuItem(actions.get(id));
                    // The action's SHORT_DESCRIPTION is meant for toolbar buttons - menu entries have no tooltip.
                    item.setToolTipText(null);
                    menu.add(item);
                }
            }
            add(menu);
        }
    }
}
