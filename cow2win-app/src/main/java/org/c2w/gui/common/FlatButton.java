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

    /** An icon-only button bound to {@code action}: icon, tooltip and enabled state come from the action. */
    public static FlatButton forAction(Action action) {
        FlatButton button = new FlatButton((Icon) null);
        button.setHideActionText(true);
        button.setAction(action);
        return button;
    }

    public FlatButton(Icon icon, boolean withCursor) {
        super(icon);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        if(withCursor) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
    }
}

