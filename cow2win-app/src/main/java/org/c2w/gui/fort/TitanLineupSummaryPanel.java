package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.i18n.LanguageService;

/** {@link LineupSummaryPanel} for the titan teams - total titan power, summed titan CowScore and buff titans. */
public class TitanLineupSummaryPanel extends LineupSummaryPanel {

    static final Keys KEYS = new Keys(
            "lineupSummary.titanPower", "lineupSummary.titanPowerTooltip",
            "lineupSummary.titanCowScore", "lineupSummary.titanCowScoreTooltip",
            "lineupSummary.titanBuffCount", "lineupSummary.titanBuffCountValue", "lineupSummary.titanBuffCountTooltip");

    public TitanLineupSummaryPanel(Lineup lineup, Guild guild) {
        super(lineup, guild, FortificationType.TITAN, KEYS);
    }

    /**
     * The plain text of the CowScore tooltip (lines separated by "\n") with the current percentages:
     * element buff, relation, totem, combo and super titans (see {@link CowScoreBonuses}).
     */
    static String cowScoreTooltipText() {
        CowScoreBonuses b = TeamScoreCalculator.bonuses();
        return LanguageService.displayName(KEYS.cowScoreTooltip(), formatPercent(b.elementPercent()),
                formatPercent(b.relationPercent()), formatPercent(b.totemPercent()), formatPercent(b.comboPercent()),
                formatPercent(TeamScoreCalculator.SUPER_TITAN_PERCENT));
    }
}
