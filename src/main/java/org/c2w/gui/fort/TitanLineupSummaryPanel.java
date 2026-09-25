package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.util.BuffCalculationService;

/** {@link LineupSummaryPanel} for the titan teams - total titan power and summed titan CowScore. */
public class TitanLineupSummaryPanel extends LineupSummaryPanel {

    private static final String KEY_POWER = "lineupSummary.titanPower";
    private static final String KEY_POWER_TOOLTIP = "lineupSummary.titanPowerTooltip";
    private static final String KEY_COW_SCORE = "lineupSummary.titanCowScore";
    private static final String KEY_COW_SCORE_TOOLTIP = "lineupSummary.titanCowScoreTooltip";

    public TitanLineupSummaryPanel(Lineup lineup, Guild guild) {
        super(lineup, guild, Lineup.TeamType.TITAN, FortificationType.TITAN,
                KEY_POWER, KEY_POWER_TOOLTIP, KEY_COW_SCORE, KEY_COW_SCORE_TOOLTIP);
    }

    @Override
    protected double sumCowScore(Lineup lineup, Guild guild) {
        return BuffCalculationService.sumTitanCowScore(lineup, guild);
    }
}
