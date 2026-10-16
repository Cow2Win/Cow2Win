package org.c2w.gui;

import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.c2w.gui.action.ActionId.*;

/**
 * The main window's menu bar, built only from the actions registered in a
 * {@link MainActions} - no handler logic here. {@link #MENUS} describes its structure.
 */
public final class MainMenuBar extends JMenuBar {

    /** One entry of a {@link MenuSpec}: an action, a separator or a submenu. */
    public sealed interface Entry permits Item, Separator, MenuSpec {
    }

    /** A menu entry bound to the action registered for {@code id}. */
    public record Item(ActionId id) implements Entry {
    }

    /** A separator line. */
    public record Separator() implements Entry {
    }

    /** A menu (or submenu): language file key of its title and its entries. */
    public record MenuSpec(String titleKey, List<Entry> entries) implements Entry {

        /** The ids of every action in this menu, including its submenus. */
        public List<ActionId> allActionIds() {
            List<ActionId> ids = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry instanceof Item item) {
                    ids.add(item.id());
                } else if (entry instanceof MenuSpec submenu) {
                    ids.addAll(submenu.allActionIds());
                }
            }
            return ids;
        }
    }

    static final Separator SEPARATOR = new Separator();

    /**
     * The menus in display order: "File", "Guild", one menu per process stage and last the
     * "Journal" ({@link Stage#JOURNAL}, shown for a guild master only) - its first
     * entry is the battle list, where battle logs are also imported; the name mappings are opened
     * from the players window. Every action in a menu titled with a {@link Stage#menuTextKey()},
     * submenus included, is of that stage. "File" also holds the master data entries for now (no
     * menu of their own yet). Every action appears in exactly one menu, except the
     * {@link #TOOLBAR_ONLY} ones.
     */
    static final List<MenuSpec> MENUS = List.of(
            menu("menu.file", SETTINGS, COWSCORE, SHOW_LOG, OPEN_HERO_WARS),
            menu(Stage.GUILD.menuTextKey(), NEW_GUILD, REMOVE_GUILD),
            menu(Stage.INPUT.menuTextKey(),
                    ADD_MEMBER, REMOVE_MEMBER,
                    SEPARATOR, OPEN_GUILD_TEAM_ENTRY),
            menu(Stage.CONCEPT.menuTextKey(),
                    menu("menu.lineup", NEW_LINEUP, REMOVE_LINEUP, CLEAR_LINEUP, COPY_LINEUP),
                    SEPARATOR, RUN_ALGORITHM, COMPARE_LINEUPS,
                    SEPARATOR, SHOW_TEAMS),
            menu(Stage.OUTPUT.menuTextKey(), OPEN_CHANGE_PLAN, GENERATE_REPORT, APPLY_TO_LIVE),
            menu(Stage.JOURNAL.menuTextKey(),
                    JOURNAL_BATTLES,
                    SEPARATOR, JOURNAL_SYNC, JOURNAL_BUILD_TEAMS,
                    SEPARATOR, JOURNAL_PLAYERS));

    /** The actions deliberately offered in the toolbar only, not in any menu. */
    static final Set<ActionId> TOOLBAR_ONLY = Set.of(SAVE_GUILD, SAVE_LINEUP);

    /** The "Journal" menu - visible only while {@link AppContext#isJournalAvailable()}. */
    private final JMenu journalMenu;

    /**
     * Builds every menu; the journal menu follows {@code context} - shown only while a guild
     * master's guild is open, updated on every guild change.
     */
    public MainMenuBar(MainActions actions, AppContext context) {
        JMenu journal = null;
        for (MenuSpec spec : MENUS) {
            JMenu menu = buildMenu(spec, actions);
            if (spec.titleKey().equals(Stage.JOURNAL.menuTextKey())) {
                journal = menu;
            }
            add(menu);
        }
        this.journalMenu = journal;
        Runnable update = () -> journalMenu.setVisible(context.isJournalAvailable());
        update.run();
        context.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                update.run();
            }
        });
    }

    /** The "Journal" menu - package-visible for tests. */
    JMenu journalMenu() {
        return journalMenu;
    }

    /** A menu spec from {@link ActionId}s, {@link #SEPARATOR}s and submenu {@link MenuSpec}s. */
    private static MenuSpec menu(String titleKey, Object... entries) {
        return new MenuSpec(titleKey, Arrays.stream(entries)
                .map(entry -> entry instanceof ActionId id ? new Item(id) : (Entry) entry)
                .toList());
    }

    /**
     * The top-level menu titled with {@code stage}'s {@link Stage#menuTextKey()} - also the
     * source of that stage's action list (see {@code org.c2w.gui.stage.StageActionList}).
     */
    public static MenuSpec menuFor(Stage stage) {
        return MENUS.stream()
                .filter(spec -> spec.titleKey().equals(stage.menuTextKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No menu for stage " + stage));
    }

    /**
     * The entries of {@code spec} as Swing components - menu items bound to the actions,
     * separators and submenus - to be added to a {@link JMenu}.
     */
    static List<JComponent> menuItems(MenuSpec spec, MainActions actions) {
        List<JComponent> items = new ArrayList<>();
        for (Entry entry : spec.entries()) {
            switch (entry) {
                case Separator separator -> items.add(new JPopupMenu.Separator());
                case MenuSpec submenu -> items.add(buildMenu(submenu, actions));
                case Item item -> {
                    JMenuItem menuItem = new JMenuItem(actions.get(item.id()));
                    // The action's SHORT_DESCRIPTION is meant for toolbar buttons - menu entries have no tooltip.
                    menuItem.setToolTipText(null);
                    items.add(menuItem);
                }
            }
        }
        return items;
    }

    private static JMenu buildMenu(MenuSpec spec, MainActions actions) {
        JMenu menu = new JMenu(LanguageService.displayName(spec.titleKey()));
        menuItems(spec, actions).forEach(menu::add);
        return menu;
    }
}
