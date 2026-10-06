package org.c2w.gui.common;

import mdlaf.utils.MaterialDrawingUtils;

import javax.swing.*;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import java.awt.*;

/**
 * Draws the text of a component as wide as Swing measured it.
 *
 * <p>The Material look and feel paints text with fractional font metrics
 * ({@link MaterialDrawingUtils#getAliasedGraphics}), but a component's preferred
 * size is measured with integer metrics - with the default font the painted text
 * is about 4% wider than the space the layout gives it, so the end of a tightly
 * laid out text is cut off. This keeps Material's antialiasing but paints with
 * integer metrics, like the measurement. Used by {@link MeasuredLabelUI} and
 * {@link MeasuredCheckBoxUI}.
 */
final class MeasuredText {

    private MeasuredText() {
    }

    /** Draws {@code text} with its baseline at {@code y}, underlining the mnemonic character. */
    static void draw(JComponent c, Graphics g, String text, int mnemonicIndex, int x, int y, Color color) {
        Graphics2D g2 = (Graphics2D) MaterialDrawingUtils.getAliasedGraphics(g.create());
        try {
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g2.setColor(color);
            BasicGraphicsUtils.drawStringUnderlineCharAt(c, g2, text, mnemonicIndex, x, y);
        } finally {
            g2.dispose();
        }
    }
}
