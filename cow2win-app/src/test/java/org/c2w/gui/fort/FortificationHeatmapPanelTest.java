package org.c2w.gui.fort;

import org.c2w.domain.FortificationHeatmap.Level;
import org.c2w.gui.common.AppColors;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationHeatmapPanel#colorOf}: one distinct color of the palette "Glut" per level. */
class FortificationHeatmapPanelTest {

    @Test
    @DisplayName("Every level has its own AppColors heatmap color")
    void colorPerLevel() {
        assertEquals(AppColors.HEATMAP_VERY_HIGH, FortificationHeatmapPanel.colorOf(Level.VERY_HIGH));
        assertEquals(AppColors.HEATMAP_HIGH, FortificationHeatmapPanel.colorOf(Level.HIGH));
        assertEquals(AppColors.HEATMAP_MEDIUM, FortificationHeatmapPanel.colorOf(Level.MEDIUM));
        assertEquals(AppColors.HEATMAP_LOW, FortificationHeatmapPanel.colorOf(Level.LOW));
        assertEquals(FortificationHeatmapPanel.EMPTY, FortificationHeatmapPanel.colorOf(Level.EMPTY));
        assertEquals(FortificationHeatmapPanel.EMPTY, FortificationHeatmapPanel.colorOf(null));
    }

    @Test
    @DisplayName("The colors of all levels differ")
    void colorsDistinct() {
        long distinct = Arrays.stream(Level.values())
                .map(FortificationHeatmapPanel::colorOf)
                .distinct()
                .count();
        assertEquals(Level.values().length, distinct);
    }

    @Test
    @DisplayName("The colors get lighter from strong to weak")
    void lighterFromStrongToWeak() {
        double veryHigh = luminance(AppColors.HEATMAP_VERY_HIGH);
        double high = luminance(AppColors.HEATMAP_HIGH);
        double medium = luminance(AppColors.HEATMAP_MEDIUM);
        double low = luminance(AppColors.HEATMAP_LOW);
        assertTrue(veryHigh < high && high < medium && medium < low,
                "luminance " + veryHigh + " < " + high + " < " + medium + " < " + low);
    }

    @Test
    @DisplayName("The caption fits below the boxes if there is room; otherwise the boxes take precedence")
    void captionLayout() {
        FortificationHeatmapPanel.Layout roomy = FortificationHeatmapPanel.layout(120, 100, 8, 5, 100, 14);
        assertTrue(roomy.showCaption());
        int gridHeight = 8 * roomy.boxHeight() + 7 * 2;
        assertTrue(gridHeight + 2 + 14 <= 100, "boxes and caption fit, grid height " + gridHeight);

        FortificationHeatmapPanel.Layout tooLow = FortificationHeatmapPanel.layout(120, 60, 8, 5, 100, 14);
        assertFalse(tooLow.showCaption(), "boxes would get smaller than the minimum");
        assertEquals((60 - 7 * 2) / 8, tooLow.boxHeight(), "boxes keep the whole height");

        FortificationHeatmapPanel.Layout tooNarrow = FortificationHeatmapPanel.layout(80, 100, 8, 5, 100, 14);
        assertFalse(tooNarrow.showCaption(), "caption wider than the cell");
    }

    @Test
    @DisplayName("The caption names what the heatmap colors by")
    void captionText() {
        assertEquals("Ø Stärke je Platz", LanguageService.textIn("deutsch", "fortificationMap.heatmapCaption"));
        assertEquals(LanguageService.displayName("fortificationMap.valueMode.averagePower"),
                LanguageService.displayName("fortificationMap.heatmapCaption"));
    }

    private static double luminance(Color color) {
        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
    }
}
