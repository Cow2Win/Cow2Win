package org.c2w.gui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link AppColors}: readable text colors (WCAG contrast) and the helpers. */
class AppColorsTest {

    /** Background of the Material Oceanic look and feel. */
    private static final Color BACKGROUND = new Color(0x26, 0x32, 0x38);
    /** The fortification tile: white with alpha 22 over the background. */
    private static final Color TILE = AppColors.mix(BACKGROUND, Color.WHITE, 22 / 255.0);
    private static final double MIN_TEXT_CONTRAST = 4.5;

    private static final Map<String, Color> TEXT_COLORS = Map.of(
            "ACCENT_TEXT", AppColors.ACCENT_TEXT,
            "SUCCESS_TEXT", AppColors.SUCCESS_TEXT,
            "WARNING_TEXT", AppColors.WARNING_TEXT,
            "ERROR_TEXT", AppColors.ERROR_TEXT,
            "INFO_TEXT", AppColors.INFO_TEXT);

    @Test
    @DisplayName("The fortification tile is about #394449")
    void tile() {
        assertEquals(new Color(0x39, 0x44, 0x49), TILE);
    }

    @Test
    @DisplayName("Every ..._TEXT color has a contrast of at least 4.5 : 1 on the background and on the tile")
    void textColorsAreReadable() {
        TEXT_COLORS.forEach((name, color) -> {
            double onBackground = contrast(color, BACKGROUND);
            double onTile = contrast(color, TILE);
            assertTrue(onBackground >= MIN_TEXT_CONTRAST, name + " on the background: " + onBackground);
            assertTrue(onTile >= MIN_TEXT_CONTRAST, name + " on the tile: " + onTile);
        });
    }

    @Test
    @DisplayName("TEXT_MUTED (blended over the background) has a contrast of at least 4.5 : 1 on the background")
    void mutedTextIsReadable() {
        Color muted = over(AppColors.TEXT_MUTED, BACKGROUND);
        double onBackground = contrast(muted, BACKGROUND);
        assertTrue(onBackground >= MIN_TEXT_CONTRAST, "TEXT_MUTED on the background: " + onBackground);
    }

    @Test
    @DisplayName("tint keeps the RGB value and sets the alpha")
    void tint() {
        Color tinted = AppColors.tint(new Color(10, 20, 30), 60);
        assertEquals(new Color(10, 20, 30, 60), tinted);
        assertEquals(AppColors.TINT, AppColors.tint(AppColors.SUCCESS, AppColors.TINT).getAlpha());
    }

    @Test
    @DisplayName("mix: 0 gives the first color, 1 the second, 0.5 the middle - alpha included")
    void mix() {
        Color a = new Color(0, 100, 200, 255);
        Color b = new Color(200, 100, 0, 55);
        assertEquals(a, AppColors.mix(a, b, 0));
        assertEquals(b, AppColors.mix(a, b, 1));
        assertEquals(new Color(100, 100, 100, 155), AppColors.mix(a, b, 0.5));
        assertEquals(a, AppColors.mix(a, b, -1), "ratio is clamped");
    }

    @Test
    @DisplayName("hex gives #rrggbb in lower case and ignores the alpha")
    void hex() {
        assertEquals("#009688", AppColors.hex(AppColors.ACCENT));
        assertEquals("#ffffff", AppColors.hex(Color.WHITE));
        assertEquals("#0a141e", AppColors.hex(new Color(10, 20, 30, 40)));
    }

    /** {@code color} with its alpha laid over the opaque {@code background}. */
    private static Color over(Color color, Color background) {
        return AppColors.mix(background, AppColors.tint(color, 255), color.getAlpha() / 255.0);
    }

    /** WCAG 2 contrast ratio of two opaque colors. */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    private static double linear(int channel) {
        double s = channel / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
