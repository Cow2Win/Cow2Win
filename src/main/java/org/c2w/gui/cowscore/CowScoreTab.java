package org.c2w.gui.cowscore;

import org.c2w.data.model.FortificationType;

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

    /** The tab the dialog opens on for the selected fortification type: heroes or titans. */
    public static CowScoreTab forFortificationType(FortificationType fortificationType) {
        return fortificationType == FortificationType.TITAN ? TITANS : HEROES;
    }
}
