package org.c2w.gui.common;

import mdlaf.components.label.MaterialLabelUI;
import mdlaf.utils.MaterialDrawingUtils;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import java.awt.*;

/**
 * {@link MaterialLabelUI} that draws the text as wide as Swing measured it.
 *
 * <p>The Material look and feel paints label text with fractional font metrics
 * ({@link MaterialDrawingUtils#getAliasedGraphics}), but a label's preferred
 * size is measured with integer metrics - with the default font the painted
 * text is about 4% wider than the space the layout gives it, so the end of
 * every tightly laid out label is cut off (e.g. the longest line of a
 * {@link JOptionPane} message). This UI keeps Material's antialiasing but
 * paints with integer metrics, like the measurement. Registered as
 * {@code "LabelUI"} right after the look and feel is installed.
 */
public class MeasuredLabelUI extends MaterialLabelUI {

    public static ComponentUI createUI(JComponent c) {
        return new MeasuredLabelUI();
    }

    @Override
    protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
        paintText(l, g, s, textX, textY, l.getForeground());
    }

    @Override
    protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY) {
        paintText(l, g, s, textX, textY, UIManager.getColor("Label.disabledForeground"));
    }

    private static void paintText(JLabel l, Graphics g, String s, int textX, int textY, Color color) {
        Graphics2D g2 = (Graphics2D) MaterialDrawingUtils.getAliasedGraphics(g.create());
        try {
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g2.setColor(color);
            BasicGraphicsUtils.drawStringUnderlineCharAt(l, g2, s, l.getDisplayedMnemonicIndex(), textX, textY);
        } finally {
            g2.dispose();
        }
    }
}
