package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.i18n.LanguageService;

/** {@link LineupSummaryPanel} for the hero teams - total hero power, summed hero CowScore and buff heroes. */
public class HeroLineupSummaryPanel extends LineupSummaryPanel {

    static final Keys KEYS = new Keys(
            "lineupSummary.heroPower", "lineupSummary.heroPowerTooltip",
            "lineupSummary.heroCowScore", "lineupSummary.heroCowScoreTooltip",
            "lineupSummary.heroBuffCount", "lineupSummary.heroBuffCountValue", "lineupSummary.heroBuffCountTooltip");

    public HeroLineupSummaryPanel(Lineup lineup, Guild guild) {
        super(lineup, guild, FortificationType.HERO, KEYS);
    }

    /**
     * The plain text of the CowScore tooltip (lines separated by "\n") with the current percentages:
     * role buff, relation, pet, war flag and combo (see {@link CowScoreBonuses}).
     */
    static String cowScoreTooltipText() {
        CowScoreBonuses b = TeamScoreCalculator.bonuses();
        return LanguageService.displayName(KEYS.cowScoreTooltip(), formatPercent(b.rolePercent()),
                formatPercent(b.relationPercent()), formatPercent(b.petPercent()), formatPercent(b.warFlagPercent()),
                formatPercent(TeamScoreCalculator.WAR_FLAG_PRESENT_PERCENT), formatPercent(b.comboPercent()));
    }
}
