package org.c2w.gui.cowscore;

/** The tabs of {@link CowScoreDialog}, in display order. */
public enum CowScoreTab {

    HEROES("cowScore.tab.heroes"),
    TITANS("cowScore.tab.titans"),
    PETS("cowScore.tab.pets"),
    WAR_FLAGS("cowScore.tab.warFlags");

    private final String textKey;

    CowScoreTab(String textKey) {
        this.textKey = textKey;
    }

    /** Language file key of the tab title. */
    public String textKey() {
        return textKey;
    }
}
