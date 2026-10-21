package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.LineupKeyFigures;
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
    private final FortificationType fortificationType;
    private final Keys keys;

    private final JLabel powerLbl = new JLabel();
    private final JLabel cowScoreLbl = new JLabel();
    private final JLabel buffCountLbl = new JLabel();

    protected LineupSummaryPanel(Lineup lineup, Guild guild, FortificationType fortificationType, Keys keys) {
        super(new GridLayout(3, 1, 0, 2));
        if (lineup == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a lineup");
        }
        if (guild == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a guild");
        }
        this.guild = guild;
        this.fortificationType = fortificationType;
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
        LineupKeyFigures figures = LineupKeyFigures.of(lineup, guild, fortificationType);
        powerLbl.setText(LanguageService.displayName(keys.power()) + ": " + formatPower(figures.power()));
        cowScoreLbl.setText(LanguageService.displayName(keys.cowScore()) + ": " + formatCowScore(figures.cowScore()));
        buffCountLbl.setText(LanguageService.displayName(keys.buffCount()) + ": "
                + LanguageService.displayName(keys.buffCountValue(), figures.buffHits()));
        // Here rather than once: the percentages come from the settings and may have changed.
        cowScoreLbl.setToolTipText(cowScoreTooltip(fortificationType));
    }

    /**
     * The HTML tooltip explaining the CowScore of {@code type}'s teams, with the current percentages
     * - also used by the lineup comparison.
     */
    public static String cowScoreTooltip(FortificationType type) {
        return htmlTooltip(type == FortificationType.HERO ? HeroLineupSummaryPanel.cowScoreTooltipText()
                : TitanLineupSummaryPanel.cowScoreTooltipText());
    }

    /** The tooltip explaining the buff count of {@code type} - also used by the lineup comparison. */
    public static String buffCountTooltip(FortificationType type) {
        return LanguageService.displayName(type == FortificationType.HERO
                ? HeroLineupSummaryPanel.KEYS.buffCountTooltip() : TitanLineupSummaryPanel.KEYS.buffCountTooltip());
    }

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

    /** A total power as shown: from one million on with one decimal and "Mio.", e.g. "56,5 Mio.", below in full. */
    public static String formatPower(int power) {
        if (Math.abs(power) >= MILLIONS_THRESHOLD) {
            double millions = power / (double) MILLIONS_THRESHOLD;
            return MILLIONS_FORMAT.format(millions) + " " + LanguageService.displayName(KEY_MILLIONS_SUFFIX);
        }
        return GuiUtils.NUMBER_FORMAT.format(power);
    }

    /** A summed CowScore as shown: thousands grouping, one decimal, e.g. "1.234,5". */
    public static String formatCowScore(double cowScore) {
        return COW_SCORE_FORMAT.format(cowScore);
    }

    /**
     * A power difference in the unit {@link #formatPower} shows {@code reference} in, with sign:
     * "+0,3" for millions (without the suffix), "-12.345" below - the full difference if it would
     * round to 0 in millions, so a difference is never hidden.
     */
    public static String formatPowerDiff(int diff, int reference) {
        String formatted;
        if (Math.abs(reference) >= MILLIONS_THRESHOLD && Math.abs(diff) >= MILLIONS_THRESHOLD / 20) {
            formatted = MILLIONS_FORMAT.format(diff / (double) MILLIONS_THRESHOLD);
        } else {
            formatted = GuiUtils.NUMBER_FORMAT.format(diff);
        }
        return diff > 0 ? "+" + formatted : formatted;
    }

    /** A CowScore difference like {@link #formatCowScore}, with sign, e.g. "+12,4". */
    public static String formatCowScoreDiff(double diff) {
        String formatted = COW_SCORE_FORMAT.format(diff);
        if (formatted.equals("0") || formatted.equals("-0")) {
            return "0";
        }
        return diff > 0 ? "+" + formatted : formatted;
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
