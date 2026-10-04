package org.c2w.gui.action;

/**
 * One constant per function offered in the main window's menu bar or toolbar. Each
 * knows the language file key of its text (menu entry text and tooltip, see
 * {@link AppAction}) and the {@link Stage} it belongs to.
 */
public enum ActionId {

    SETTINGS("menu.settings", Stage.GENERAL, Opens.WINDOW),
    SHOW_LOG("menu.showLog", Stage.GENERAL, Opens.WINDOW),
    OPEN_HERO_WARS("toolbar.heroWars", Stage.GENERAL),

    /** Master data: the CowScore marks apply to every guild (menu "File" for now, see {@link Stage#MASTER_DATA}). */
    COWSCORE("menu.cowScoreDialog", Stage.MASTER_DATA, Opens.WINDOW),

    /** Input: creating and removing a guild, like editing it, is about the data of one guild. */
    NEW_GUILD("toolbar.newGuild", Stage.INPUT, Opens.WINDOW),
    REMOVE_GUILD("toolbar.removeGuild", Stage.INPUT),
    /** Input, not master data: members and their teams are the ever-changing data of one guild. */
    OPEN_GUILD_EDITOR("teamsOverview.openGuildEditor", Stage.INPUT, Opens.WINDOW),
    SAVE_GUILD("teamsOverview.saveGuild", Stage.INPUT),
    OPEN_GUILD_HERO_ENTRY("toolbar.openGuildHeroEntry", Stage.INPUT, Opens.WINDOW),
    OPEN_GUILD_TITAN_ENTRY("toolbar.openGuildTitanEntry", Stage.INPUT, Opens.WINDOW),
    JOURNAL_IMPORT("menu.journal.import", Stage.INPUT, Opens.WINDOW),
    JOURNAL_SYNC("menu.journal.sync", Stage.INPUT, Opens.WINDOW),
    JOURNAL_NAME_MAPPINGS("journal.action.nameMappings", Stage.INPUT, Opens.WINDOW),
    JOURNAL_PLAYERS("journal.action.players", Stage.INPUT, Opens.WINDOW),
    JOURNAL_SEASONS("journal.action.seasons", Stage.INPUT, Opens.WINDOW),

    NEW_LINEUP("toolbar.newLineup", Stage.CONCEPT, Opens.WINDOW),
    REMOVE_LINEUP("toolbar.removeLineup", Stage.CONCEPT),
    CLEAR_LINEUP("toolbar.clearLineup", Stage.CONCEPT),
    SAVE_LINEUP("toolbar.saveLineup", Stage.CONCEPT),
    RUN_ALGORITHM("teamsOverview.runAlgorithm", Stage.CONCEPT),
    COMPARE_LINEUPS("toolbar.compareLineups", Stage.CONCEPT, Opens.WINDOW),
    SHOW_HERO_TEAMS("toolbar.heroTeams", Stage.CONCEPT, Opens.WINDOW),
    SHOW_TITAN_TEAMS("toolbar.titanTeams", Stage.CONCEPT, Opens.WINDOW),
    JOURNAL_BATTLES("menu.journal.battles", Stage.CONCEPT, Opens.WINDOW),
    JOURNAL_BUILD_TEAMS("menu.journal.buildTeams", Stage.CONCEPT, Opens.WINDOW),

    OPEN_CHANGE_PLAN("toolbar.openChangePlan", Stage.OUTPUT, Opens.WINDOW),
    GENERATE_REPORT("toolbar.generateReport", Stage.OUTPUT, Opens.WINDOW);

    /**
     * Readability marker for the constructor: {@code Opens.WINDOW} = the action opens a window
     * or dialog (see {@link #opensWindow()}). A nested class, because an enum constant's
     * arguments cannot refer to the enum's own static fields by simple name.
     */
    private static final class Opens {
        static final boolean WINDOW = true;
    }

    private final String textKey;
    private final Stage stage;
    private final boolean opensWindow;

    ActionId(String textKey, Stage stage) {
        this(textKey, stage, false);
    }

    ActionId(String textKey, Stage stage, boolean opensWindow) {
        this.textKey = textKey;
        this.stage = stage;
        this.opensWindow = opensWindow;
    }

    /** Language file key of the action's text (menu entry text and tooltip). */
    public String textKey() {
        return textKey;
    }

    public Stage stage() {
        return stage;
    }

    /**
     * True if the action opens a window or dialog to work in - its menu entry then ends
     * with "…" (see {@link AppAction}). A mere confirmation question (remove, clear) does not count.
     */
    public boolean opensWindow() {
        return opensWindow;
    }
}
