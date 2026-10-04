package org.c2w.gui.action;

/**
 * Where an {@link ActionId} belongs in the process-oriented user interface: the three
 * process stages input, strategic concept and output, plus master data and general
 * functions. Each process stage has a menu of its own, see {@code MainMenuBar}; master
 * data and general functions are in the "File" menu.
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
     * Entering the ever-changing data of one guild: the guild itself (create, remove), its
     * members and their teams (guild editor, team assignment), and the battle logs of the
     * Weltenschlacht journal.
     */
    INPUT("menu.input", "stage.input"),
    CONCEPT("menu.concept", "stage.concept"),
    OUTPUT("menu.output", "stage.output");

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
}
