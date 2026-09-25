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

/**
 * Common base for the lineup summary shown in the fortification map's top
 * row: total power and summed CowScore of one team type. Split into
 * {@link HeroLineupSummaryPanel} (top left) and {@link TitanLineupSummaryPanel}
 * (top right) on 2026-09-25, so each summary can be shown/hidden together with
 * its fortifications (see FortificationMapPanel).
 */
public abstract class LineupSummaryPanel extends JPanel {

    private static final String KEY_MILLIONS_SUFFIX = "lineupSummary.millionsSuffix";

    private static final int MILLIONS_THRESHOLD = 1_000_000;

    private static final NumberFormat MILLIONS_FORMAT = buildOneDecimalFormat();

    /** Same one-decimal-digit, no-grouping shape as {@link #MILLIONS_FORMAT}, just for the CowScore totals (typically single/double digit) rather than power-in-millions. */
    private static final NumberFormat COW_SCORE_FORMAT = buildOneDecimalFormat();

    private final Guild guild;
    private final Lineup.TeamType teamType;
    private final String powerKey;
    private final String cowScoreKey;

    private final JLabel powerLbl = new JLabel();
    private final JLabel cowScoreLbl = new JLabel();

    protected LineupSummaryPanel(Lineup lineup, Guild guild, Lineup.TeamType teamType,
                                 FortificationType fortificationType,
                                 String powerKey, String powerTooltipKey,
                                 String cowScoreKey, String cowScoreTooltipKey) {
        super(new GridLayout(2, 1, 0, 2));
        if (lineup == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a lineup");
        }
        if (guild == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a guild");
        }
        this.guild = guild;
        this.teamType = teamType;
        this.powerKey = powerKey;
        this.cowScoreKey = cowScoreKey;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        setOpaque(false);

        powerLbl.setForeground(fortificationType.getColor());
        cowScoreLbl.setForeground(fortificationType.getColor());

        // Static tooltip text, set once here rather than in refresh(Lineup),
        // since it never changes with the lineup. A summed CowScore total is
        // not self-explanatory the way a power value is.
        powerLbl.setToolTipText(LanguageService.displayName(powerTooltipKey));
        cowScoreLbl.setToolTipText(LanguageService.displayName(cowScoreTooltipKey));

        add(powerLbl);
        add(cowScoreLbl);

        refresh(lineup);
    }

    public void refresh(Lineup lineup) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        powerLbl.setText(LanguageService.displayName(powerKey) + ": "
                + formatPower(totalPower(lineup, guild, teamType)));
        cowScoreLbl.setText(LanguageService.displayName(cowScoreKey) + ": "
                + COW_SCORE_FORMAT.format(sumCowScore(lineup, guild)));
    }

    /** Summed CowScore of this panel's team type - see BuffCalculationService#sumHeroCowScore / #sumTitanCowScore. */
    protected abstract double sumCowScore(Lineup lineup, Guild guild);

    /** Sums every entry's current team totalPower (see {@link BuffCalculationService#totalPowerOf}) over every entry of the given team type. */
    private static int totalPower(Lineup lineup, Guild guild, Lineup.TeamType teamType) {
        int total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() == teamType) {
                total += BuffCalculationService.totalPowerOf(entry, guild);
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
