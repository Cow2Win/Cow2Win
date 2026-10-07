package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;

/**
 * The look of hints on a tinted background with a border in the same color - the "Live, as of ..."
 * label of the context bar and the information area of the CowScore dialog.
 */
public final class HintStyle {

    /** Teal of the "Live" lineup status and of the CowScore information area. */
    public static final Color TEAL = new Color(0x4D, 0xC0, 0xA8);

    /** Opacity (0-255) of the tinted background. */
    public static final int BACKGROUND_ALPHA = 45;

    private HintStyle() {
    }

    /**
     * Paints the background of {@code c}: a rounded rectangle in {@code color} with
     * {@link #BACKGROUND_ALPHA} and a 1 px border in the full {@code color}, antialiased.
     * Call before the component paints its content.
     *
     * @param arc width and height of the corner arcs - e.g. the height for fully rounded ends
     */
    public static void paintHintBackground(Graphics g, JComponent c, Color color, int arc) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), BACKGROUND_ALPHA));
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
            g2.setColor(color);
            g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
        } finally {
            g2.dispose();
        }
    }
}
