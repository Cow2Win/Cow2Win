package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.gui.common.GuiUtils;
import org.c2w.util.BuffCalculationService;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class LineupSummaryPanel extends JPanel {


    private static final String KEY_HERO_POWER = "lineupSummary.heroPower";
    private static final String KEY_HERO_COW_SCORE = "lineupSummary.heroCowScore";
    private static final String KEY_TITAN_POWER = "lineupSummary.titanPower";
    private static final String KEY_TITAN_COW_SCORE = "lineupSummary.titanCowScore";

    private static final String KEY_HERO_POWER_TOOLTIP = "lineupSummary.heroPowerTooltip";

    private static final String KEY_TITAN_POWER_TOOLTIP = "lineupSummary.titanPowerTooltip";

    private static final String KEY_HERO_COW_SCORE_TOOLTIP = "lineupSummary.heroCowScoreTooltip";

    private static final String KEY_TITAN_COW_SCORE_TOOLTIP = "lineupSummary.titanCowScoreTooltip";

    private static final String KEY_MILLIONS_SUFFIX = "lineupSummary.millionsSuffix";

    private static final int MILLIONS_THRESHOLD = 1_000_000;

    private static final NumberFormat MILLIONS_FORMAT = buildOneDecimalFormat();

    /** Same one-decimal-digit, no-grouping shape as {@link #MILLIONS_FORMAT}, just for the CowScore totals (typically single/double digit) rather than power-in-millions. */
    private static final NumberFormat COW_SCORE_FORMAT = buildOneDecimalFormat();


    private static final Color BACKGROUND_COLOR = Color.DARK_GRAY;

    private static final Color TEXT_COLOR = Color.WHITE;

    private final Guild guild;

    private final JLabel heroPowerLbl = new JLabel();
    private final JLabel heroCowScoreLbl = new JLabel();
    private final JLabel titanPowerLbl = new JLabel();
    private final JLabel titanCowScoreLbl = new JLabel();

    public LineupSummaryPanel(Lineup lineup, Guild guild) {
        super(new GridLayout(4, 1, 0, 2));
        if (lineup == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a lineup");
        }
        if (guild == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a guild");
        }
        this.guild = guild;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        //setBackground(BACKGROUND_COLOR);
        setOpaque(false);

        heroPowerLbl.setForeground(FortificationType.HERO.getColor());
        heroCowScoreLbl.setForeground(FortificationType.HERO.getColor());
        titanPowerLbl.setForeground(FortificationType.TITAN.getColor());
        titanCowScoreLbl.setForeground(FortificationType.TITAN.getColor());

        // Tooltips added 2026-09-05 alongside shortening the hero/titan power
        // labels themselves (see class Javadoc) - static text, set once here
        // rather than in refresh(Lineup), since neither ever changes with the
        // lineup. The CowScore labels got their own tooltip too (2026-09-15,
        // replacing the former buff-count labels) since a summed CowScore
        // total is not self-explanatory the way a power/count value is.
        heroPowerLbl.setToolTipText(LanguageService.displayName(KEY_HERO_POWER_TOOLTIP));
        titanPowerLbl.setToolTipText(LanguageService.displayName(KEY_TITAN_POWER_TOOLTIP));
        heroCowScoreLbl.setToolTipText(LanguageService.displayName(KEY_HERO_COW_SCORE_TOOLTIP));
        titanCowScoreLbl.setToolTipText(LanguageService.displayName(KEY_TITAN_COW_SCORE_TOOLTIP));

        add(heroPowerLbl);
        add(heroCowScoreLbl);
        add(titanPowerLbl);
        add(titanCowScoreLbl);

        refresh(lineup);
    }

    public void refresh(Lineup lineup) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        heroPowerLbl.setText(LanguageService.displayName(KEY_HERO_POWER) + ": "
                + formatPower(totalPower(lineup, Lineup.TeamType.HERO)));
        heroCowScoreLbl.setText(LanguageService.displayName(KEY_HERO_COW_SCORE) + ": "
                + COW_SCORE_FORMAT.format(BuffCalculationService.sumHeroCowScore(lineup, guild)));
        titanPowerLbl.setText(LanguageService.displayName(KEY_TITAN_POWER) + ": "
                + formatPower(totalPower(lineup, Lineup.TeamType.TITAN)));
        titanCowScoreLbl.setText(LanguageService.displayName(KEY_TITAN_COW_SCORE) + ": "
                + COW_SCORE_FORMAT.format(BuffCalculationService.sumTitanCowScore(lineup, guild)));
    }

    /** Sums {@link Lineup.Entry#totalPower()} over every entry of the given team type. */
    private static int totalPower(Lineup lineup, Lineup.TeamType teamType) {
        int total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() == teamType) {
                total += entry.totalPower();
            }
        }
        return total;
    }

    private static String formatPower(int power) {
        if (Math.abs(power) >= MILLIONS_THRESHOLD) {
            double millions = power / (double) MILLIONS_THRESHOLD;
            return MILLIONS_FORMAT.format(millions) + " " + LanguageService.displayName(KEY_MILLIONS_SUFFIX);
        }
        return GuiUtils.NUMBER_FORMAT.format(power);
    }

  
    private static NumberFormat buildOneDecimalFormat() {
        NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        return format;
    }
}
