package org.c2w.gui.common;

import org.c2w.data.model.FortificationType;

import java.awt.*;

/**
 * How a {@link FortificationType} looks in the GUI - its color and the slot
 * icons on the fortification map. Kept here rather than on the enum itself
 * so the data model stays free of AWT/Swing.
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

    /** Classpath path of the icon for a free team slot of a fortification of this type. */
    public static String openSlotIconPath(FortificationType type) {
        return switch (type) {
            case HERO -> "/images/app/square.png";
            case TITAN -> "/images/app/hexagon.png";
        };
    }

    /** Classpath path of the icon for a filled team slot of a fortification of this type. */
    public static String filledSlotIconPath(FortificationType type) {
        return switch (type) {
            case HERO -> "/images/app/square-team.png";
            case TITAN -> "/images/app/hexagon-team.png";
        };
    }
}
