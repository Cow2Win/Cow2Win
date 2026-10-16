package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Common base for the lineup summary shown in the fortification map's top
 * row: total power, summed CowScore (power term + bonus, see
 * {@link TeamScoreCalculator}) and the number of placed heroes/titans that
 * increase a buff, of one team type. Split into
 * {@link HeroLineupSummaryPanel} (top left) and {@link TitanLineupSummaryPanel}
 * (top right), so each summary can be shown/hidden together with
 * its fortifications (see FortificationMapPanel).
 */
public abstract class LineupSummaryPanel extends JPanel {

    private static final String KEY_MILLIONS_SUFFIX = "lineupSummary.millionsSuffix";

    private static final int MILLIONS_THRESHOLD = 1_000_000;

    private static final NumberFormat MILLIONS_FORMAT = buildOneDecimalFormat(false);

    /**
     * One decimal digit like {@link #MILLIONS_FORMAT}, but with thousands grouping like the power
     * values below a million ({@link GuiUtils#NUMBER_FORMAT}) - the CowScore totals are in the
     * hundreds to thousands (power / 100,000 plus a few percent).
     */
    private static final NumberFormat COW_SCORE_FORMAT = buildOneDecimalFormat(true);

    /**
     * The language file keys of one summary.
     *
     * @param buffCountValue text of the buff count line's value, {0} = the count
     */
    protected record Keys(String power, String powerTooltip, String cowScore, String cowScoreTooltip,
                          String buffCount, String buffCountValue, String buffCountTooltip) {
    }

    private final Guild guild;
    private final Lineup.TeamType teamType;
    private final Keys keys;

    private final JLabel powerLbl = new JLabel();
    private final JLabel cowScoreLbl = new JLabel();
    private final JLabel buffCountLbl = new JLabel();

    protected LineupSummaryPanel(Lineup lineup, Guild guild, Lineup.TeamType teamType,
                                 FortificationType fortificationType, Keys keys) {
        super(new GridLayout(3, 1, 0, 2));
        if (lineup == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a lineup");
        }
        if (guild == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a guild");
        }
        this.guild = guild;
        this.teamType = teamType;
        this.keys = keys;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        setOpaque(false);

        for (JLabel label : new JLabel[]{powerLbl, cowScoreLbl, buffCountLbl}) {
            label.setForeground(FortificationTypeStyle.color(fortificationType));
            add(label);
        }
        powerLbl.setToolTipText(LanguageService.displayName(keys.powerTooltip()));
        buffCountLbl.setToolTipText(LanguageService.displayName(keys.buffCountTooltip()));

        refresh(lineup);
    }

    public void refresh(Lineup lineup) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        powerLbl.setText(LanguageService.displayName(keys.power()) + ": "
                + formatPower(totalPower(lineup, guild, teamType)));
        cowScoreLbl.setText(LanguageService.displayName(keys.cowScore()) + ": "
                + COW_SCORE_FORMAT.format(sumCowScore(lineup, guild)));
        buffCountLbl.setText(LanguageService.displayName(keys.buffCount()) + ": "
                + LanguageService.displayName(keys.buffCountValue(), countIncreasingBuff(lineup, guild)));
        // Here rather than once: the percentages come from the settings and may have changed.
        cowScoreLbl.setToolTipText(htmlTooltip(cowScoreTooltip(keys.cowScoreTooltip())));
    }

    /** Summed CowScore of this panel's team type - see BuffCalculationService#sumHeroCowScore / #sumTitanCowScore. */
    protected abstract double sumCowScore(Lineup lineup, Guild guild);

    /** Placed heroes/titans that increase a buff - see BuffCalculationService#countHeroesIncreasingBuff / #countTitansIncreasingBuff. */
    protected abstract int countIncreasingBuff(Lineup lineup, Guild guild);

    /** The plain text of the CowScore label's tooltip (lines separated by "\n") with the current percentages filled in. */
    protected abstract String cowScoreTooltip(String key);

    /** A percentage for a tooltip: the language's locale, at most 2 decimal digits. */
    protected static String formatPercent(double percent) {
        NumberFormat format = NumberFormat.getNumberInstance(LanguageService.locale());
        format.setMaximumFractionDigits(2);
        return format.format(percent);
    }

    /** Plain text with "\n" line breaks as an HTML tooltip, escaped. */
    static String htmlTooltip(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + escaped.replace("\n", "<br>") + "</html>";
    }

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

    /** The CowScore line's number as shown - for tests. */
    static String formatCowScore(double cowScore) {
        return COW_SCORE_FORMAT.format(cowScore);
    }

    private static NumberFormat buildOneDecimalFormat(boolean grouping) {
        NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
        format.setGroupingUsed(grouping);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        return format;
    }

    // ------------------------------------------------------------- for tests

    JLabel powerLabel() {
        return powerLbl;
    }

    JLabel cowScoreLabel() {
        return cowScoreLbl;
    }

    JLabel buffCountLabel() {
        return buffCountLbl;
    }
}
