package org.c2w.gui.common;

import mdlaf.components.checkbox.MaterialCheckBoxUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * {@link MaterialCheckBoxUI} that draws the text as wide as Swing measured it - see
 * {@link MeasuredText} (otherwise the last letters of a check box's text are cut
 * off). Registered as {@code "CheckBoxUI"} right after the look and feel is installed.
 */
public class MeasuredCheckBoxUI extends MaterialCheckBoxUI {

    public static ComponentUI createUI(JComponent c) {
        return new MeasuredCheckBoxUI();
    }

    @Override
    protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
        AbstractButton b = (AbstractButton) c;
        Color color = b.getModel().isEnabled() ? b.getForeground() : disabledForeground;
        int shift = getTextShiftOffset();
        MeasuredText.draw(c, g, text, b.getDisplayedMnemonicIndex(), textRect.x + shift,
                textRect.y + c.getFontMetrics(c.getFont()).getAscent() + shift, color);
    }
}
