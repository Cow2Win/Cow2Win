package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.i18n.LanguageService;

/** {@link LineupSummaryPanel} for the titan teams - total titan power, summed titan CowScore and buff titans. */
public class TitanLineupSummaryPanel extends LineupSummaryPanel {

    private static final Keys KEYS = new Keys(
            "lineupSummary.titanPower", "lineupSummary.titanPowerTooltip",
            "lineupSummary.titanCowScore", "lineupSummary.titanCowScoreTooltip",
            "lineupSummary.titanBuffCount", "lineupSummary.titanBuffCountValue", "lineupSummary.titanBuffCountTooltip");

    public TitanLineupSummaryPanel(Lineup lineup, Guild guild) {
        super(lineup, guild, Lineup.TeamType.TITAN, FortificationType.TITAN, KEYS);
    }

    @Override
    protected double sumCowScore(Lineup lineup, Guild guild) {
        return BuffCalculationService.sumTitanCowScore(lineup, guild);
    }

    @Override
    protected int countIncreasingBuff(Lineup lineup, Guild guild) {
        return BuffCalculationService.countTitansIncreasingBuff(lineup, guild);
    }

    /** The formula with the current percentages: element buff, relation, totem, combo and super titans (see {@link CowScoreBonuses}). */
    @Override
    protected String cowScoreTooltip(String key) {
        CowScoreBonuses b = TeamScoreCalculator.bonuses();
        return LanguageService.displayName(key, formatPercent(b.elementPercent()), formatPercent(b.relationPercent()),
                formatPercent(b.totemPercent()), formatPercent(b.comboPercent()),
                formatPercent(TeamScoreCalculator.SUPER_TITAN_PERCENT));
    }
}
