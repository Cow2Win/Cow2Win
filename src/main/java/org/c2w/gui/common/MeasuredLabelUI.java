package org.c2w.gui.common;

import mdlaf.components.label.MaterialLabelUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * {@link MaterialLabelUI} that draws the text as wide as Swing measured it - see
 * {@link MeasuredText} (otherwise e.g. the longest line of a {@link JOptionPane}
 * message is cut off). Registered as {@code "LabelUI"} right after the look and
 * feel is installed.
 */
public class MeasuredLabelUI extends MaterialLabelUI {

    public static ComponentUI createUI(JComponent c) {
        return new MeasuredLabelUI();
    }

    @Override
    protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
        MeasuredText.draw(l, g, s, l.getDisplayedMnemonicIndex(), textX, textY, l.getForeground());
    }

    @Override
    protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY) {
        MeasuredText.draw(l, g, s, l.getDisplayedMnemonicIndex(), textX, textY,
                UIManager.getColor("Label.disabledForeground"));
    }
}
