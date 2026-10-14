package org.c2w.gui.common;

import org.c2w.data.model.FortificationType;

import java.awt.*;

/**
 * How a {@link FortificationType} looks in the GUI - its color. Kept here
 * rather than on the enum itself so the data model stays free of AWT/Swing.
 */
public final class FortificationTypeStyle {

    private static final Color TITAN_COLOR = new Color(215, 186, 89);

    private FortificationTypeStyle() {
        // Utility class, no instantiation
    }

    /** Text/icon color for everything belonging to this side (map labels, tables, toolbar). */
    public static Color color(FortificationType type) {
        return switch (type) {
            case HERO -> Color.WHITE;
            case TITAN -> TITAN_COLOR;
        };
    }
}
