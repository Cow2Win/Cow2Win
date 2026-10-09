package org.c2w.gui.cowscore;

/**
 * The editable tabs of {@link CowScoreDialog}, in display order. They follow the dialog's
 * "Info" tab (which is no {@code CowScoreTab}), so the tab index is <b>not</b> the
 * {@link #ordinal()} - the dialog converts between the two in one place.
 */
public enum CowScoreTab {

    HEROES("cowScore.tab.heroes"),
    TITANS("cowScore.tab.titans"),
    PETS("cowScore.tab.pets"),
    WAR_FLAGS("cowScore.tab.warFlags"),
    /** The hero combos ({@code heroCombos.json}) - see {@code HeroComboPanel}. */
    HERO_COMBOS("cowScore.tab.heroCombos");

    private final String textKey;

    CowScoreTab(String textKey) {
        this.textKey = textKey;
    }

    /** Language file key of the tab title. */
    public String textKey() {
        return textKey;
    }
}
