package org.c2w.gui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/** {@link HintStyle}: color and background painting - on an image, no display needed. */
class HintStyleTest {

    @Test
    @DisplayName("The hint color is #4DC0A8")
    void teal() {
        assertEquals(new Color(0x4D, 0xC0, 0xA8), HintStyle.TEAL);
    }

    @Test
    @DisplayName("Tinted background (alpha 45 over the background) inside, the full color on the border")
    void paintsTintAndBorder() {
        int width = 200;
        int height = 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        JPanel component = new JPanel();
        component.setSize(width, height);

        HintStyle.paintHintBackground(g, component, HintStyle.TEAL, 28);
        g.dispose();

        Color inside = new Color(image.getRGB(width / 2, height / 2));
        assertColor(tinted(HintStyle.TEAL), inside, 2);
        Color border = new Color(image.getRGB(width / 2, 0));
        assertColor(HintStyle.TEAL, border, 2);
    }

    /** {@code color} with {@link HintStyle#BACKGROUND_ALPHA} over black. */
    private static Color tinted(Color color) {
        double alpha = HintStyle.BACKGROUND_ALPHA / 255.0;
        return new Color((int) Math.round(color.getRed() * alpha), (int) Math.round(color.getGreen() * alpha),
                (int) Math.round(color.getBlue() * alpha));
    }

    private static void assertColor(Color expected, Color actual, int tolerance) {
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                        && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                        && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance,
                "expected " + expected + " but was " + actual);
    }
}
