package org.c2w.gui.action;

/**
 * Where an {@link ActionId} belongs in the planned process-oriented user interface:
 * the three process stages input, strategic concept and output, plus master data and
 * general functions. Not shown anywhere yet (GUI rework M0) - the menus still follow
 * the old structure, see {@code MainMenuBar}.
 */
public enum Stage {

    /** Application-wide functions (settings, log, guild administration, ...) - no menu or stage name of their own. */
    GENERAL(null, null),
    MASTER_DATA("menu.masterData", null),
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
