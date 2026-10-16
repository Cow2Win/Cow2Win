package org.c2w.gui.action;

/**
 * Where an {@link ActionId} belongs in the process-oriented user interface: the three
 * process stages input, strategic concept and output, plus the guild, master data and
 * general functions and the Clash of Worlds journal. Each process stage has a menu of its own,
 * see {@code MainMenuBar}, and so have the guild (next to "File") and the journal (last, for a
 * guild master only); master data and general functions are in the "File" menu.
 */
public enum Stage {

    /** Application-wide functions (settings, log, Hero Wars website, ...) - no menu or stage name of their own. */
    GENERAL(null, null),
    /**
     * Data that rarely changes and applies to every guild: catalogs, CowScore marks,
     * templates, combos. No menu of its own yet (it would hold a single entry) - its
     * entries stay in the "File" menu for now.
     */
    MASTER_DATA("menu.masterData", null),
    /**
     * Functions about the guild as a whole (create, edit, remove) - the frame all three
     * process stages work in, so not part of one of them; menu of its own next to "File".
     */
    GUILD("menu.guild", null),
    /**
     * Entering the ever-changing data of one guild: its members and their teams (team
     * assignment).
     */
    INPUT("menu.input", "stage.input"),
    CONCEPT("menu.concept", "stage.concept"),
    OUTPUT("menu.output", "stage.output"),
    /**
     * The Clash of Worlds journal (battle logs - only a guild master can export them from the
     * game): not a process stage - no tile in the process bar, no action list. A menu of its
     * own, shown only while a guild master's guild is open (see {@code AppContext#isJournalAvailable()}).
     */
    JOURNAL("menu.journal", null);

    private final String menuTextKey;
    private final String stageTextKey;

    Stage(String menuTextKey, String stageTextKey) {
        this.menuTextKey = menuTextKey;
        this.stageTextKey = stageTextKey;
    }

    /** Language file key of this stage's menu title, or {@code null} for {@link #GENERAL}. */
    public String menuTextKey() {
        return menuTextKey;
    }

    /** Language file key of the process stage's name, or {@code null} if this is not one of the three process stages. */
    public String stageTextKey() {
        return stageTextKey;
    }

    /**
     * Language file key of the process stage's short description (the subtitle of its tile in
     * the process bar), or {@code null} if this is not one of the three process stages.
     */
    public String subtitleKey() {
        return stageTextKey == null ? null : stageTextKey + ".subtitle";
    }
}
