package org.c2w.gui.common;

import org.c2w.data.model.FortificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationTypeStyle#actionIconColor} and {@link IconLoader#iconForType} - headless. */
class FortificationTypeStyleTest {

    /** The icons of the actions that got new icons in version 1.0.10 or follow the fortification type. */
    private static final List<String> ACTION_ICONS = List.of("/images/app/pencil.png", "/images/app/copy.png",
            "/images/app/compare.png", "/images/app/table.png", "/images/app/run.png", "/images/app/lineup-clean.png");

    @Test
    @DisplayName("Action icon color: the normal button color for heroes, the titan color for titans")
    void actionIconColor() {
        assertEquals(UIManager.getColor("Label.foreground"), FortificationTypeStyle.actionIconColor(FortificationType.HERO));
        assertEquals(AppColors.TITAN, FortificationTypeStyle.actionIconColor(FortificationType.TITAN));
        assertEquals(FortificationTypeStyle.color(FortificationType.TITAN),
                FortificationTypeStyle.actionIconColor(FortificationType.TITAN));
    }

    @Test
    @DisplayName("The action icons are found; per type an icon, heroes the same as a button icon")
    void iconsForType() {
        for (String path : ACTION_ICONS) {
            assertNotNull(IconLoader.class.getResource(path), path);
            ImageIcon hero = IconLoader.iconForType(path, FortificationType.HERO);
            ImageIcon titan = IconLoader.iconForType(path, FortificationType.TITAN);
            assertNotNull(hero, path);
            assertNotNull(titan, path);
            assertNotSame(hero, titan, path);
            assertSame(IconLoader.iconForButton(path), hero, path);
        }
    }
}
