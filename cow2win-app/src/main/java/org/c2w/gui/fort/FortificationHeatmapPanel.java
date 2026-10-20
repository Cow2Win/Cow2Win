package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.domain.FortificationHeatmap;
import org.c2w.gui.common.AppColors;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Mini map of one fortification type in one cell of the {@link FortificationMapPanel}: every
 * fortification of the type is a small square at its {@code row}/{@code column} in a grid of the
 * map's size, colored by its {@link FortificationHeatmap.Level}. Only a picture - no tooltip, no
 * mouse reaction (mouse events go to the map underneath).
 */
public class FortificationHeatmapPanel extends JComponent {

    /** Color of a strong fortification. */
    static final Color HIGH = AppColors.ERROR;
    /** Color of a fortification of about average strength. */
    static final Color MEDIUM = AppColors.WARNING;
    /** Color of a weak fortification. */
    static final Color LOW = AppColors.HEATMAP_LOW;
    /** Color of an empty fortification. */
    static final Color EMPTY = AppColors.tint(AppColors.MUTED, AppColors.TINT_STRONG);

    /** Gap between two squares, in pixels. */
    private static final int GAP = 2;

    /** Width of a box relative to its height - slightly wider than a square (if the cell has room). */
    private static final double WIDTH_RATIO = 1.3;

    private final FortificationHeatmap heatmap;
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

    @Override
    protected void paintComponent(Graphics g) {
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        int maxBoxWidth = (width - (columns - 1) * GAP) / columns;
        int boxHeight = Math.min(maxBoxWidth, (height - (rows - 1) * GAP) / rows);
        int boxWidth = Math.min(maxBoxWidth, (int) Math.round(boxHeight * WIDTH_RATIO));
        if (boxHeight < 1) {
            return;
        }
        int gridWidth = columns * boxWidth + (columns - 1) * GAP;
        int gridHeight = rows * boxHeight + (rows - 1) * GAP;
        int left = insets.left + (width - gridWidth) / 2;
        int top = insets.top + (height - gridHeight) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
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

    /** The color of {@code level} (null counts as empty). */
    static Color colorOf(FortificationHeatmap.Level level) {
        if (level == null) {
            return EMPTY;
        }
        return switch (level) {
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
            case EMPTY -> EMPTY;
        };
    }
}
