package org.c2w.gui.action;

/**
 * One constant per function offered in the main window's menu bar or toolbar. Each
 * knows the language file key of its text (menu entry text and tooltip, see
 * {@link AppAction}) and the {@link Stage} it belongs to.
 */
public enum ActionId {

    SETTINGS("menu.settings", Stage.GENERAL),
    SHOW_LOG("menu.showLog", Stage.GENERAL),
    OPEN_HERO_WARS("toolbar.heroWars", Stage.GENERAL),
    NEW_GUILD("toolbar.newGuild", Stage.GENERAL),
    REMOVE_GUILD("toolbar.removeGuild", Stage.GENERAL),

    COWSCORE_HEROES("menu.cowScore", Stage.MASTER_DATA),
    COWSCORE_TITANS("menu.titanCowScore", Stage.MASTER_DATA),
    COWSCORE_PETS("menu.petCowScore", Stage.MASTER_DATA),
    COWSCORE_WAR_FLAGS("menu.warFlagCowScore", Stage.MASTER_DATA),
    OPEN_GUILD_EDITOR("teamsOverview.openGuildEditor", Stage.MASTER_DATA),

    SAVE_GUILD("teamsOverview.saveGuild", Stage.INPUT),
    OPEN_GUILD_HERO_ENTRY("toolbar.openGuildHeroEntry", Stage.INPUT),
    OPEN_GUILD_TITAN_ENTRY("toolbar.openGuildTitanEntry", Stage.INPUT),
    JOURNAL_IMPORT("menu.journal.import", Stage.INPUT),
    JOURNAL_SYNC("menu.journal.sync", Stage.INPUT),
    JOURNAL_NAME_MAPPINGS("journal.action.nameMappings", Stage.INPUT),
    JOURNAL_PLAYERS("journal.action.players", Stage.INPUT),
    JOURNAL_SEASONS("journal.action.seasons", Stage.INPUT),

    NEW_LINEUP("toolbar.newLineup", Stage.CONCEPT),
    REMOVE_LINEUP("toolbar.removeLineup", Stage.CONCEPT),
    CLEAR_LINEUP("toolbar.clearLineup", Stage.CONCEPT),
    SAVE_LINEUP("toolbar.saveLineup", Stage.CONCEPT),
    RUN_ALGORITHM("teamsOverview.runAlgorithm", Stage.CONCEPT),
    COMPARE_LINEUPS("toolbar.compareLineups", Stage.CONCEPT),
    SHOW_HERO_TEAMS("toolbar.heroTeams", Stage.CONCEPT),
    SHOW_TITAN_TEAMS("toolbar.titanTeams", Stage.CONCEPT),
    JOURNAL_BATTLES("menu.journal.battles", Stage.CONCEPT),
    JOURNAL_BUILD_TEAMS("menu.journal.buildTeams", Stage.CONCEPT),

    OPEN_CHANGE_PLAN("toolbar.openChangePlan", Stage.OUTPUT),
    GENERATE_REPORT("toolbar.generateReport", Stage.OUTPUT);

    private final String textKey;
    private final Stage stage;

    ActionId(String textKey, Stage stage) {
        this.textKey = textKey;
        this.stage = stage;
    }

    /** Language file key of the action's text (menu entry text and tooltip). */
    public String textKey() {
        return textKey;
    }

    public Stage stage() {
        return stage;
    }
}
