package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;

import java.text.NumberFormat;

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

    /** The formula with the current percentages: element buff, relation and totem (see {@link CowScoreBonuses}). */
    @Override
    protected String cowScoreTooltip(String key) {
        CowScoreBonuses b = TeamScoreCalculator.bonuses();
        NumberFormat format = NumberFormat.getNumberInstance(JournalTexts.locale());
        format.setMaximumFractionDigits(2);
        return LanguageService.displayName(key, format.format(b.elementPercent()), format.format(b.relationPercent()),
                format.format(b.totemPercent()));
    }
}
