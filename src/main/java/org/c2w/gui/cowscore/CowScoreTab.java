package org.c2w.gui.cowscore;

import org.c2w.data.model.FortificationType;

/**
 * The tabs of {@link CowScoreDialog}, in display order - the tab index is the
 * {@link #ordinal()}. {@link #HERO_COMBOS} comes last, so the other indices stay.
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

    /** The tab the dialog opens on for the selected fortification type: heroes or titans. */
    public static CowScoreTab forFortificationType(FortificationType fortificationType) {
        return fortificationType == FortificationType.TITAN ? TITANS : HEROES;
    }
}
