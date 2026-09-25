package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.util.BuffCalculationService;

/** {@link LineupSummaryPanel} for the hero teams - total hero power and summed hero CowScore. */
public class HeroLineupSummaryPanel extends LineupSummaryPanel {

    private static final String KEY_POWER = "lineupSummary.heroPower";
    private static final String KEY_POWER_TOOLTIP = "lineupSummary.heroPowerTooltip";
    private static final String KEY_COW_SCORE = "lineupSummary.heroCowScore";
    private static final String KEY_COW_SCORE_TOOLTIP = "lineupSummary.heroCowScoreTooltip";

    public HeroLineupSummaryPanel(Lineup lineup, Guild guild) {
        super(lineup, guild, Lineup.TeamType.HERO, FortificationType.HERO,
                KEY_POWER, KEY_POWER_TOOLTIP, KEY_COW_SCORE, KEY_COW_SCORE_TOOLTIP);
    }

    @Override
    protected double sumCowScore(Lineup lineup, Guild guild) {
        return BuffCalculationService.sumHeroCowScore(lineup, guild);
    }
}
