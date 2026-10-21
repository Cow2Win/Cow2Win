package org.c2w.gui.compare;

import org.c2w.gui.common.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A tornado chart drawn with Java2D: one row per item, the label in a centered column, the
 * left value's bar growing from the center to the left, the right value's bar growing to the
 * right, and the difference (right - left) at the far right - in the success text color if
 * the right side has more, in the error text color if less. Both sides share one scale, the
 * maximum over all enabled values (see {@link #scaleMax}). A disabled row is shown dimmed with
 * {@link #setDisabledText its text} instead of values, without bars and without difference. Optionally a total row follows.
 *
 * <p>Without a right side (see {@link #setRows}) only the left values and bars are drawn.
 */
public final class TornadoChart extends JComponent {

    /** One row: {@code tooltip} may be null; a row that is not {@code enabled} has no bars. */
    public record Row(String label, int left, int right, String tooltip, boolean enabled) {
    }

    private static final int ROW_HEIGHT = 25;
    private static final int BAR_INSET = 5;
    private static final int NAME_PADDING = 12;
    private static final int VALUE_GAP = 6;
    private static final int DIFF_GAP = 16;
    private static final int MIN_BAR_WIDTH = 80;
    private static final int TITLE_GAP = 8;
    private static final int TOTAL_GAP = 6;

    private final String title;
    private final IntFunction<String> valueFormat;
    private final IntFunction<String> diffFormat;
    private List<Row> rows = List.of();
    private boolean rightAvailable;
    private String totalLabel;
    private String disabledText = "";
    private Color leftColor = Color.WHITE;

    /**
     * @param valueFormat formats a value, e.g. "1,05 Mio"
     * @param diffFormat  formats a difference with sign, e.g. "+230.000"
     */
    public TornadoChart(String title, IntFunction<String> valueFormat, IntFunction<String> diffFormat) {
        this.title = title;
        this.valueFormat = valueFormat;
        this.diffFormat = diffFormat;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        // Registers with the tooltip manager - the text itself comes from getToolTipText(MouseEvent).
        setToolTipText("");
    }

    /** Shows {@code rows}; without {@code rightAvailable} only the left side is drawn. */
    public void setRows(List<Row> rows, boolean rightAvailable) {
        this.rows = List.copyOf(rows);
        this.rightAvailable = rightAvailable;
        revalidate();
        repaint();
    }

    /** Shows a total row labeled {@code label} below the rows, null for none. */
    public void setTotalLabel(String label) {
        this.totalLabel = label;
        revalidate();
        repaint();
    }

    /** Text a disabled row shows instead of its values, e.g. "no buff". */
    public void setDisabledText(String disabledText) {
        this.disabledText = disabledText == null ? "" : disabledText;
        repaint();
    }

    /** Color of the left bars, e.g. the color of the fortification type; the right bars are {@link AppColors#INFO}. */
    public void setLeftColor(Color left) {
        this.leftColor = left;
        repaint();
    }

    public List<Row> rows() {
        return rows;
    }

    public boolean isRightAvailable() {
        return rightAvailable;
    }

    /** The shared scale of both sides - see {@link #scaleMax(List, boolean)}. */
    public int scaleMax() {
        return scaleMax(rows, rightAvailable);
    }

    public int totalLeft() {
        return rows.stream().filter(Row::enabled).mapToInt(Row::left).sum();
    }

    public int totalRight() {
        return rightAvailable ? rows.stream().filter(Row::enabled).mapToInt(Row::right).sum() : 0;
    }

    /** The largest value of the enabled rows on both sides (the right side only if available), at least 0. */
    static int scaleMax(List<Row> rows, boolean rightAvailable) {
        int max = 0;
        for (Row row : rows) {
            if (row.enabled()) {
                max = Math.max(max, row.left());
                if (rightAvailable) {
                    max = Math.max(max, row.right());
                }
            }
        }
        return max;
    }

    /** Length of the bar of {@code value} if {@code max} takes {@code available} pixels - 0 without a scale. */
    static int barLength(int value, int max, int available) {
        if (max <= 0 || value <= 0 || available <= 0) {
            return 0;
        }
        // In double: available * value overflows an int for power values in the millions on a wide chart.
        return (int) Math.round(available * (double) Math.min(value, max) / max);
    }

    /** {@link AppColors#SUCCESS_TEXT} if the right side has more, {@link AppColors#ERROR_TEXT} if less, {@code neutral} otherwise. */
    static Color diffColor(int diff, Color neutral) {
        if (diff > 0) {
            return AppColors.SUCCESS_TEXT;
        }
        if (diff < 0) {
            return AppColors.ERROR_TEXT;
        }
        return neutral;
    }

    // --- layout ---

    /** Column widths of the current rows for the font metrics {@code fm}. */
    private record Columns(int nameWidth, int valueWidth, int diffWidth) {
    }

    private Columns columns(FontMetrics fm) {
        int nameWidth = totalLabel == null ? 0 : fm.stringWidth(totalLabel);
        int valueWidth = fm.stringWidth(disabledText);
        int diffWidth = 0;
        for (Row row : rows) {
            nameWidth = Math.max(nameWidth, fm.stringWidth(row.label()));
            valueWidth = Math.max(valueWidth, fm.stringWidth(valueFormat.apply(row.left())));
            valueWidth = Math.max(valueWidth, fm.stringWidth(valueFormat.apply(row.right())));
            diffWidth = Math.max(diffWidth, fm.stringWidth(diffFormat.apply(row.right() - row.left())));
        }
        if (totalLabel != null) {
            valueWidth = Math.max(valueWidth, fm.stringWidth(valueFormat.apply(totalLeft())));
            valueWidth = Math.max(valueWidth, fm.stringWidth(valueFormat.apply(totalRight())));
            diffWidth = Math.max(diffWidth, fm.stringWidth(diffFormat.apply(totalRight() - totalLeft())));
        }
        return new Columns(nameWidth + 2 * NAME_PADDING, valueWidth, diffWidth);
    }

    private int titleHeight() {
        return getFontMetrics(titleFont()).getHeight() + TITLE_GAP;
    }

    private Font titleFont() {
        return getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 1f);
    }

    @Override
    public Font getFont() {
        Font font = super.getFont();
        if (font != null) {
            return font;
        }
        Font labelFont = UIManager.getFont("Label.font");
        return labelFont != null ? labelFont : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Insets insets = getInsets();
        Columns columns = columns(getFontMetrics(getFont()));
        int side = columns.valueWidth() + VALUE_GAP + MIN_BAR_WIDTH;
        int width = insets.left + insets.right + 2 * side + columns.nameWidth() + DIFF_GAP + columns.diffWidth();
        int height = insets.top + insets.bottom + titleHeight() + rows.size() * ROW_HEIGHT
                + (totalLabel != null ? TOTAL_GAP + ROW_HEIGHT : 0);
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** Geometry of the current size: x of the name column, its width, the bar space per side and the diff column. */
    private record Geometry(int nameStart, int nameEnd, int barSpace, int valueWidth, int diffEnd, int top) {
    }

    private Geometry geometry(FontMetrics fm) {
        Insets insets = getInsets();
        Columns columns = columns(fm);
        int inner = getWidth() - insets.left - insets.right;
        int side = Math.max(0, (inner - columns.nameWidth() - DIFF_GAP - columns.diffWidth()) / 2);
        int nameStart = insets.left + side;
        int nameEnd = nameStart + columns.nameWidth();
        int barSpace = Math.max(0, side - columns.valueWidth() - VALUE_GAP);
        return new Geometry(nameStart, nameEnd, barSpace, columns.valueWidth(),
                getWidth() - insets.right, insets.top + titleHeight());
    }

    // --- painting ---

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color foreground = foreground();
            Color dimmed = AppColors.TEXT_MUTED;

            Insets insets = getInsets();
            g2.setFont(titleFont());
            g2.setColor(foreground);
            g2.drawString(title == null ? "" : title, insets.left, insets.top + g2.getFontMetrics().getAscent());

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            Geometry geometry = geometry(fm);
            int max = scaleMax();

            int y = geometry.top();
            for (Row row : rows) {
                paintRow(g2, fm, geometry, row, y, max, foreground, dimmed);
                y += ROW_HEIGHT;
            }

            // The center column's borders across all rows.
            g2.setColor(AppColors.LINE_STRONG);
            g2.drawLine(geometry.nameStart(), geometry.top(), geometry.nameStart(), y);
            g2.drawLine(geometry.nameEnd(), geometry.top(), geometry.nameEnd(), y);

            if (totalLabel != null) {
                g2.drawLine(insets.left, y + TOTAL_GAP / 2, geometry.diffEnd(), y + TOTAL_GAP / 2);
                paintTotal(g2, geometry, y + TOTAL_GAP, foreground);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintRow(Graphics2D g2, FontMetrics fm, Geometry geometry, Row row, int y, int max,
                          Color foreground, Color dimmed) {
        int baseline = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(row.enabled() ? foreground : dimmed);
        drawCentered(g2, row.label(), geometry.nameStart(), geometry.nameEnd(), baseline);

        if (!row.enabled()) {
            g2.setColor(dimmed);
            drawRightAligned(g2, disabledText, geometry.nameStart() - VALUE_GAP, baseline);
            if (rightAvailable) {
                g2.drawString(disabledText, geometry.nameEnd() + VALUE_GAP, baseline);
            }
            return;
        }

        int barTop = y + BAR_INSET;
        int barHeight = ROW_HEIGHT - 2 * BAR_INSET;

        int leftLength = barLength(row.left(), max, geometry.barSpace());
        paintBar(g2, geometry.nameStart() - leftLength, barTop, leftLength, barHeight, leftColor);
        g2.setColor(foreground);
        drawRightAligned(g2, valueFormat.apply(row.left()), geometry.nameStart() - leftLength - VALUE_GAP, baseline);

        if (rightAvailable) {
            int rightLength = barLength(row.right(), max, geometry.barSpace());
            paintBar(g2, geometry.nameEnd(), barTop, rightLength, barHeight, AppColors.INFO);
            g2.setColor(foreground);
            g2.drawString(valueFormat.apply(row.right()), geometry.nameEnd() + rightLength + VALUE_GAP, baseline);

            int diff = row.right() - row.left();
            g2.setColor(diffColor(diff, foreground));
            drawRightAligned(g2, diffFormat.apply(diff), geometry.diffEnd(), baseline);
        }
    }

    private void paintTotal(Graphics2D g2, Geometry geometry, int y, Color foreground) {
        Font bold = getFont().deriveFont(Font.BOLD);
        g2.setFont(bold);
        FontMetrics fm = g2.getFontMetrics();
        int baseline = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(foreground);
        drawCentered(g2, totalLabel, geometry.nameStart(), geometry.nameEnd(), baseline);
        drawRightAligned(g2, valueFormat.apply(totalLeft()), geometry.nameStart() - VALUE_GAP, baseline);
        if (rightAvailable) {
            g2.drawString(valueFormat.apply(totalRight()), geometry.nameEnd() + VALUE_GAP, baseline);
            int diff = totalRight() - totalLeft();
            g2.setColor(diffColor(diff, foreground));
            drawRightAligned(g2, diffFormat.apply(diff), geometry.diffEnd(), baseline);
        }
        g2.setFont(getFont());
    }

    /** A filled bar with a faint outline ({@link AppColors#LINE_STRONG}). */
    private static void paintBar(Graphics2D g2, int x, int y, int width, int height, Color fill) {
        if (width <= 0) {
            return;
        }
        g2.setColor(fill);
        g2.fillRect(x, y, width, height);
        g2.setColor(AppColors.LINE_STRONG);
        g2.drawRect(x, y, width - 1, height - 1);
    }

    private static void drawRightAligned(Graphics2D g2, String text, int right, int baseline) {
        g2.drawString(text, right - g2.getFontMetrics().stringWidth(text), baseline);
    }

    private static void drawCentered(Graphics2D g2, String text, int left, int right, int baseline) {
        int width = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, left + (right - left - width) / 2, baseline);
    }

    private static Color foreground() {
        Color color = UIManager.getColor("Label.foreground");
        return color != null ? color : Color.WHITE;
    }

    // --- tooltip ---

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = rowAt(event.getY());
        return index < 0 ? null : rows.get(index).tooltip();
    }

    /** Index of the row at {@code y}, -1 outside the rows. */
    int rowAt(int y) {
        int top = getInsets().top + titleHeight();
        if (y < top) {
            return -1;
        }
        int index = (y - top) / ROW_HEIGHT;
        return index < rows.size() ? index : -1;
    }
}
