package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.BuffTexts;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * One fortification on the {@link FortificationMapPanel}: a flat, self-painted tile with
 * the name at the top and "filled/capacity · power" plus the buff percentage at the
 * bottom. The stripe on the left shows the fill level (see {@link #fillLevel}). A click
 * reports the fortification to the map, which keeps track of the selection.
 */
public class FortificationPanel extends JPanel {

    /** How many of a fortification's places are taken - the color of the stripe on the left. */
    enum FillLevel {
        /** No place taken. */
        EMPTY,
        /** At least one place, but not all of them taken. */
        PARTIAL,
        /** Every place taken. */
        FULL
    }

    /** Language file key of the compact power value, e.g. "{0} Mio". */
    private static final String KEY_POWER_MILLIONS = "fortification.powerMillions";

    private static final int MILLIONS_THRESHOLD = 1_000_000;

    /**
     * Preferred width of every tile, independent of its texts: the map's columns then all
     * get the same width (see FortificationMapPanel#MIN_CELL_SIZE), so they do not shift
     * when the fortification type is switched.
     */
    static final int PREFERRED_WIDTH = 120;

    private static final int ARC = 10;
    private static final int STRIPE_WIDTH = 4;
    private static final Color SURFACE = new Color(255, 255, 255, 22);
    private static final Color OUTLINE = new Color(255, 255, 255, 40);
    private static final Color SELECTED_SURFACE = new Color(
            IconLoader.BLUE.getRed(), IconLoader.BLUE.getGreen(), IconLoader.BLUE.getBlue(), 60);
    /** Orange of a partly filled fortification - deliberately different from the titan gold of the name. */
    static final Color PARTIAL_COLOR = new Color(217, 130, 43);

    private final Fortification fortification;
    private final int filledSlots;
    private final int totalPower;
    /**
     * Change of {@link #totalPower} against the live lineup in {@link FortificationValueMode#LIVE_COMPARISON},
     * otherwise since the lineup was loaded or last saved (see AppContext#fortificationDiffFromLoaded).
     */
    private final int totalPowerDiff;
    /** What the tile shows (see FortificationMapPanel#setValueMode): power and buff, or their change. */
    private final FortificationValueMode valueMode;
    private final int buffPercent;
    /** Change of {@link #buffPercent}, against the same base as {@link #totalPowerDiff}. */
    private final int buffPercentDiff;
    /** Change of the number of buff-matching heroes/titans, against the same base - shown in the power tooltip (see {@link #powerTooltip()}). */
    private final int buffMemberCountDiff;
    private boolean selected;

    private JLabel nameLbl;
    private JLabel powerLbl;
    private JLabel buffPercentLbl;

    /**
     * @param onClick receives {@code fortification} whenever the tile is clicked
     */
    public FortificationPanel(Fortification fortification, int filledSlots, int totalPower, int totalPowerDiff,
                              FortificationValueMode valueMode, int buffPercent, int buffPercentDiff,
                              int buffMemberCountDiff, boolean selected, Consumer<Fortification> onClick) {
        this.fortification = fortification;
        this.filledSlots = Math.min(filledSlots, fortification.capacity());
        this.totalPower = totalPower;
        this.totalPowerDiff = totalPowerDiff;
        this.valueMode = valueMode;
        this.buffPercent = buffPercent;
        this.buffPercentDiff = buffPercentDiff;
        this.buffMemberCountDiff = buffMemberCountDiff;
        this.selected = selected;
        init(onClick);
    }

    private void init(Consumer<Fortification> onClick) {
        setLayout(new BorderLayout(0, 4));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, STRIPE_WIDTH + 8, 6, 8));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(getPowerLabel(), BorderLayout.CENTER);
        bottom.add(getBuffPercentLabel(), BorderLayout.EAST);
        add(getNameLabel(), BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);

        // The labels have tooltips and therefore their own mouse listeners, which keep
        // clicks on them from reaching this panel - so every one of them listens, too.
        MouseAdapter clickListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    onClick.accept(fortification);
                }
            }
        };
        addMouseListener(clickListener);
        nameLbl.addMouseListener(clickListener);
        powerLbl.addMouseListener(clickListener);
        buffPercentLbl.addMouseListener(clickListener);
    }

    Fortification fortification() {
        return fortification;
    }

    int totalPowerDiff() {
        return totalPowerDiff;
    }

    boolean isSelected() {
        return selected;
    }

    /** Highlights this tile as the selected fortification (see FortificationMapPanel#selectedFortification). */
    void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PREFERRED_WIDTH, super.getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, width - 1f, height - 1f, ARC, ARC);

            g2.setColor(selected ? SELECTED_SURFACE : SURFACE);
            g2.fill(shape);

            // Stripe on the left, clipped to the rounded outline.
            Shape oldClip = g2.getClip();
            g2.clip(shape);
            g2.setColor(fillLevelColor(fillLevel(filledSlots, fortification.capacity())));
            g2.fillRect(0, 0, STRIPE_WIDTH, height);
            g2.setClip(oldClip);

            g2.setColor(selected ? IconLoader.BLUE : OUTLINE);
            g2.draw(shape);
        } finally {
            g2.dispose();
        }
    }

    /** {@link FillLevel#EMPTY} for no taken place, {@link FillLevel#FULL} for all of them, {@link FillLevel#PARTIAL} in between. */
    static FillLevel fillLevel(int filled, int capacity) {
        if (filled <= 0) {
            return FillLevel.EMPTY;
        }
        return filled >= capacity ? FillLevel.FULL : FillLevel.PARTIAL;
    }

    static Color fillLevelColor(FillLevel level) {
        return switch (level) {
            case EMPTY -> IconLoader.RED;
            case PARTIAL -> PARTIAL_COLOR;
            case FULL -> IconLoader.GREEN;
        };
    }

    /**
     * The fortification name, bold, in the color of its fortification type; cut off with an
     * ellipsis if too long, the full name is in the tooltip.
     */
    JLabel getNameLabel() {
        if (nameLbl == null) {
            String name = LanguageService.displayName(fortification.id());
            nameLbl = new JLabel(name, JLabel.LEFT);
            nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD));
            nameLbl.setForeground(FortificationTypeStyle.color(fortification.type()));
            nameLbl.setToolTipText(name);
            // Small minimum width, so a long name is cut off instead of widening the column.
            nameLbl.setMinimumSize(new Dimension(0, nameLbl.getPreferredSize().height));
            nameLbl.setPreferredSize(new Dimension(0, nameLbl.getPreferredSize().height));
        }
        return nameLbl;
    }

    /**
     * "filled/capacity · power", e.g. "5/5 · 1,05 Mio" - or, while {@link #valueMode} shows a
     * change, "filled/capacity · {@link #totalPowerDiff}", colored
     * green for a gain and red for a loss (see {@link #diffColor}).
     */
    private JLabel getPowerLabel() {
        if (powerLbl == null) {
            String places = filledSlots + "/" + fortification.capacity() + " · ";
            String power = valueMode.showsChange() ? formatPowerDiff(totalPowerDiff) : compactPower(totalPower);
            powerLbl = new JLabel(places + power, JLabel.LEFT);
            powerLbl.setForeground(valueMode.showsChange() ? diffColor(totalPowerDiff, mutedForeground()) : mutedForeground());
            powerLbl.setToolTipText(powerTooltip());
            powerLbl.setMinimumSize(new Dimension(0, powerLbl.getPreferredSize().height));
        }
        return powerLbl;
    }

    /**
     * The buff percentage, e.g. "+8 %" - or, while {@link #valueMode} shows a change,
     * {@link #buffPercentDiff}, colored green for a gain and red for a loss (see
     * {@link #diffColor}). Empty for a fortification without a buff.
     */
    private JLabel getBuffPercentLabel() {
        if (buffPercentLbl == null) {
            buffPercentLbl = new JLabel("", JLabel.RIGHT);
            if (fortification.buff() != null) {
                int value = valueMode.showsChange() ? buffPercentDiff : buffPercent;
                buffPercentLbl.setText(formatPercent(value));
                buffPercentLbl.setForeground(valueMode.showsChange() ? diffColor(buffPercentDiff, foreground()) : foreground());
                buffPercentLbl.setToolTipText(BuffTexts.describe(fortification.buff()));
            }
        }
        return buffPercentLbl;
    }

    /** "+8 %"/"-5 %"/"0 %". */
    static String formatPercent(int percent) {
        return (percent > 0 ? "+" : "") + percent + " %";
    }

    /** {@code power} in compact form for the configured language, see {@link #compactPower(int, Locale, String)}. */
    private static String compactPower(int power) {
        return compactPower(power, JournalTexts.locale(), LanguageService.displayName(KEY_POWER_MILLIONS));
    }

    /**
     * GUI-free core of the compact power value: from one million on with two decimals in
     * {@code locale} and {@code millionsPattern} (e.g. "{0} Mio"), e.g. "1,05 Mio"; below
     * that the full number with {@link GuiUtils#NUMBER_FORMAT}, e.g. "812.345".
     */
    static String compactPower(int power, Locale locale, String millionsPattern) {
        if (Math.abs(power) < MILLIONS_THRESHOLD) {
            return GuiUtils.NUMBER_FORMAT.format(power);
        }
        NumberFormat format = NumberFormat.getInstance(locale);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        String millions = format.format(power / (double) MILLIONS_THRESHOLD);
        return millionsPattern.replace("{0}", millions);
    }

    /**
     * Tooltip of the power label: the change since the lineup was loaded or last saved while the
     * label shows the total power, the total power while it shows that change, and the change
     * against live - naming that base - in the live comparison.
     */
    private String powerTooltip() {
        return powerTooltip(valueMode, totalPower, totalPowerDiff, fortification.buff() != null, buffMemberCountDiff);
    }

    /**
     * GUI-free core of {@link #powerTooltip()}, e.g. "Since last save: +12.345 power, +1 buff
     * members", "Unchanged since last save", "Total power: 1.234.567" or "Compared to live:
     * +12.345 power". The buff part only appears for a fortification with a buff.
     */
    static String powerTooltip(FortificationValueMode valueMode, int totalPower, int totalPowerDiff,
                               boolean hasBuff, int buffMemberCountDiff) {
        return switch (valueMode) {
            case CHANGES -> LanguageService.displayName("fortification.totalPowerTooltip",
                    GuiUtils.NUMBER_FORMAT.format(totalPower));
            case POWER -> diffTooltip("fortification.unchangedTooltip", "fortification.diffTooltip",
                    "fortification.diffTooltipNoBuff", totalPowerDiff, hasBuff, buffMemberCountDiff);
            case LIVE_COMPARISON -> diffTooltip("fortification.liveUnchangedTooltip", "fortification.liveDiffTooltip",
                    "fortification.liveDiffTooltipNoBuff", totalPowerDiff, hasBuff, buffMemberCountDiff);
        };
    }

    /** The change text with the given keys: unchanged, with buff members, or power only. */
    private static String diffTooltip(String unchangedKey, String diffKey, String diffNoBuffKey,
                                      int totalPowerDiff, boolean hasBuff, int buffMemberCountDiff) {
        if (totalPowerDiff == 0 && (!hasBuff || buffMemberCountDiff == 0)) {
            return LanguageService.displayName(unchangedKey);
        }
        if (hasBuff) {
            return LanguageService.displayName(diffKey,
                    formatPowerDiff(totalPowerDiff), formatPowerDiff(buffMemberCountDiff));
        }
        return LanguageService.displayName(diffNoBuffKey, formatPowerDiff(totalPowerDiff));
    }

    /** "+1.234"/"-1.234"/"0" - {@link GuiUtils#NUMBER_FORMAT} already prefixes a negative diff with "-", so only the "+" for a positive diff needs adding here. */
    private static String formatPowerDiff(int diff) {
        String formatted = GuiUtils.NUMBER_FORMAT.format(diff);
        return diff > 0 ? "+" + formatted : formatted;
    }

    /** {@link IconLoader#GREEN} for a gain, {@link IconLoader#RED} for a loss, {@code unchanged} otherwise. */
    private static Color diffColor(int diff, Color unchanged) {
        if (diff > 0) {
            return IconLoader.GREEN;
        }
        if (diff < 0) {
            return IconLoader.RED;
        }
        return unchanged;
    }

    private static Color foreground() {
        Color color = UIManager.getColor("Label.foreground");
        return color != null ? color : Color.WHITE;
    }

    /** {@link #foreground()}, dimmed - for the secondary "filled/capacity · power" text. */
    private static Color mutedForeground() {
        Color color = foreground();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
    }
}
