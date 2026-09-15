package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.util.LanguageService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;

public class FortificationDisplayLabel extends JLabel {

    FortificationDisplayLabel(Fortification fortification){
        super(LanguageService.displayName(fortification.id()),JLabel.CENTER);
       // setBackground(fortification.type().getColor());
        setForeground(fortification.type().getColor());
        setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        //displayLbl.setOpaque(true);
        setPreferredSize(new Dimension(160,20));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(LanguageService.displayName("fortificationDisplay.tooltip"));
    }
/**
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            String text = getText();
            FontMetrics fm = g2.getFontMetrics(getFont());
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            int paddingX = 8;
            int paddingY = 2;
            int rectWidth = textWidth + paddingX * 2;
            int rectHeight = textHeight + paddingY * 2;
            int rectX = (getWidth() - rectWidth) / 2;
            int rectY = (getHeight() - rectHeight) / 2;
            int arc = 10;

            // TODO: Farbe/Transparenz spaeter anpassen
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fillRoundRect(rectX, rectY, rectWidth, rectHeight, arc, arc);
        } finally {
            g2.dispose();
        }

        super.paintComponent(g);
    }
    **/
}
