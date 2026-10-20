package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;

/** A small "i" in a circle, muted - for hints and the buttons that show an explanation. */
public final class InfoIcon implements Icon {

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppColors.MUTED);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(x, y, 12, 12);
        g2.fillRect(x + 5, y + 5, 2, 5);
        g2.fillRect(x + 5, y + 2, 2, 2);
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return 13;
    }

    @Override
    public int getIconHeight() {
        return 13;
    }
}
