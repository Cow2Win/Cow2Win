package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;

public class FlatButton extends JButton {

    private static final int PADDING = 2;

    public FlatButton(Icon icon) {
        super(icon);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}

