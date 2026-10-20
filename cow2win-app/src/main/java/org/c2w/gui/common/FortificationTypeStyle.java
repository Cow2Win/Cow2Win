package org.c2w.gui.common;

import org.c2w.data.model.FortificationType;

import javax.swing.*;
import java.awt.*;

/**
 * How a {@link FortificationType} looks in the GUI - its color from {@link AppColors}. Kept here
 * rather than on the enum itself so the data model stays free of AWT/Swing.
 */
public final class FortificationTypeStyle {

    private FortificationTypeStyle() {
        // Utility class, no instantiation
    }

    /** Text/icon color for everything belonging to this side (map labels, tables, toolbar). */
    public static Color color(FortificationType type) {
        return switch (type) {
            case HERO -> AppColors.HERO;
            case TITAN -> AppColors.TITAN;
        };
    }

    /**
     * Icon color of an action that follows the selected fortification type: the normal button
     * color ({@code Label.foreground}, like {@link IconLoader#iconForButton}) for heroes, the
     * {@link #color titan color} for titans - see {@link IconLoader#iconForType}.
     */
    public static Color actionIconColor(FortificationType type) {
        return type == FortificationType.TITAN ? color(type) : UIManager.getColor("Label.foreground");
    }
}
