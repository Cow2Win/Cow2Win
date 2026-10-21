package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.domain.FortificationHeatmap;
import org.c2w.gui.common.AppColors;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Mini map of one fortification type in one cell of the {@link FortificationMapPanel}: every
 * fortification of the type is a small square at its {@code row}/{@code column} in a grid of the
 * map's size, colored by its {@link FortificationHeatmap.Level}, with a small caption below naming
 * the value ("Avg. power per slot") if the cell has room for it. Only a picture - no tooltip, no
 * mouse reaction (mouse events go to the map underneath).
 */
public class FortificationHeatmapPanel extends JComponent {

    /** Color of a very strong fortification. */
    static final Color VERY_HIGH = AppColors.HEATMAP_VERY_HIGH;
    /** Color of a strong fortification (average or slightly above). */
    static final Color HIGH = AppColors.HEATMAP_HIGH;
    /** Color of a slightly weak fortification. */
    static final Color MEDIUM = AppColors.HEATMAP_MEDIUM;
    /** Color of a weak fortification. */
    static final Color LOW = AppColors.HEATMAP_LOW;
    /** Color of an empty fortification. */
    static final Color EMPTY = AppColors.tint(AppColors.MUTED, AppColors.TINT_STRONG);

    /** Gap between two squares, in pixels. */
    private static final int GAP = 2;

    /** Width of a box relative to its height - slightly wider than a square (if the cell has room). */
    private static final double WIDTH_RATIO = 1.3;

    /** Language file key of the caption below the boxes - what the heatmap colors by. */
    private static final String KEY_CAPTION = "fortificationMap.heatmapCaption";

    /** Gap between the boxes and the caption, in pixels. */
    private static final int CAPTION_GAP = 2;

    /** How much smaller (in points) the caption is than the component's font. */
    private static final float CAPTION_FONT_SHRINK = 2f;

    /** The boxes shrink for the caption only down to this height (in pixels) - below it the caption is left out. */
    static final int MIN_BOX_HEIGHT_WITH_CAPTION = 4;

    private final FortificationHeatmap heatmap;
    private final String caption = LanguageService.displayName(KEY_CAPTION);
    private final List<Fortification> fortifications;
    private final int rows;
    private final int columns;

    /**
     * @param heatmap        the levels to show
     * @param fortifications the fortifications of the heatmap's type (their cells)
     * @param rows           rows of the grid (as on the map)
     * @param columns        columns of the grid (as on the map)
     */
    public FortificationHeatmapPanel(FortificationHeatmap heatmap, List<Fortification> fortifications,
                                     int rows, int columns) {
        this.heatmap = heatmap;
        this.fortifications = fortifications.stream()
                .filter(fort -> fort.type() == heatmap.type())
                .toList();
        this.rows = rows;
        this.columns = columns;
        setOpaque(false);
    }

    public FortificationHeatmap heatmap() {
        return heatmap;
    }

    @Override
    public boolean contains(int x, int y) {
        // Never the target of mouse events - clicks fall through to the map.
        return false;
    }

    /** The caption below the boxes, e.g. "Avg. power per slot". */
    String caption() {
        return caption;
    }

    /**
     * Size of the boxes and whether the caption fits.
     *
     * @param boxWidth    width of one box, in pixels
     * @param boxHeight   height of one box, in pixels (below 1: nothing is painted)
     * @param showCaption true if the caption is painted below the boxes
     */
    record Layout(int boxWidth, int boxHeight, boolean showCaption) {
    }

    /**
     * GUI-free layout of a {@code width} x {@code height} area for a grid of {@code rows} x
     * {@code columns} boxes and a caption of {@code captionWidth} x {@code captionHeight} below it.
     * The boxes take precedence: they shrink for the caption only down to {@link #MIN_BOX_HEIGHT_WITH_CAPTION};
     * if they would get smaller, or the caption is wider than the area, the caption is left out and
     * the boxes keep the whole height.
     */
    static Layout layout(int width, int height, int rows, int columns, int captionWidth, int captionHeight) {
        int heightWithCaption = height - captionHeight - CAPTION_GAP;
        int boxHeightWithCaption = boxHeight(width, heightWithCaption, rows, columns);
        if (captionWidth <= width && boxHeightWithCaption >= MIN_BOX_HEIGHT_WITH_CAPTION) {
            return new Layout(boxWidth(width, boxHeightWithCaption, columns), boxHeightWithCaption, true);
        }
        int boxHeight = boxHeight(width, height, rows, columns);
        return new Layout(boxWidth(width, boxHeight, columns), boxHeight, false);
    }

    private static int boxHeight(int width, int height, int rows, int columns) {
        int maxBoxWidth = (width - (columns - 1) * GAP) / columns;
        return Math.min(maxBoxWidth, (height - (rows - 1) * GAP) / rows);
    }

    private static int boxWidth(int width, int boxHeight, int columns) {
        int maxBoxWidth = (width - (columns - 1) * GAP) / columns;
        return Math.min(maxBoxWidth, (int) Math.round(boxHeight * WIDTH_RATIO));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        Font captionFont = captionFont();
        FontMetrics metrics = g.getFontMetrics(captionFont);
        Layout layout = layout(width, height, rows, columns, metrics.stringWidth(caption), metrics.getHeight());
        int boxWidth = layout.boxWidth();
        int boxHeight = layout.boxHeight();
        if (boxHeight < 1) {
            return;
        }
        int gridWidth = columns * boxWidth + (columns - 1) * GAP;
        int gridHeight = rows * boxHeight + (rows - 1) * GAP;
        int blockHeight = gridHeight + (layout.showCaption() ? CAPTION_GAP + metrics.getHeight() : 0);
        int left = insets.left + (width - gridWidth) / 2;
        int top = insets.top + (height - blockHeight) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (layout.showCaption()) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(captionFont);
                g2.setColor(AppColors.TEXT_MUTED);
                int captionX = insets.left + (width - metrics.stringWidth(caption)) / 2;
                g2.drawString(caption, captionX, top + gridHeight + CAPTION_GAP + metrics.getAscent());
            }
            for (Fortification fort : fortifications) {
                if (fort.row() >= rows || fort.column() >= columns) {
                    continue;
                }
                int x = left + fort.column() * (boxWidth + GAP);
                int y = top + fort.row() * (boxHeight + GAP);
                g2.setColor(colorOf(heatmap.level(fort.id())));
                g2.fillRect(x, y, boxWidth, boxHeight);
                g2.setColor(AppColors.LINE);
                g2.drawRect(x, y, boxWidth - 1, boxHeight - 1);
            }
        } finally {
            g2.dispose();
        }
    }

    /** The font of the caption: the component's font (the label font if it has none), a little smaller. */
    private Font captionFont() {
        Font font = getFont() != null ? getFont() : UIManager.getFont("Label.font");
        if (font == null) {
            font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return font.deriveFont(font.getSize2D() - CAPTION_FONT_SHRINK);
    }

    /** The color of {@code level} (null counts as empty). */
    static Color colorOf(FortificationHeatmap.Level level) {
        if (level == null) {
            return EMPTY;
        }
        return switch (level) {
            case VERY_HIGH -> VERY_HIGH;
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
            case EMPTY -> EMPTY;
        };
    }
}
