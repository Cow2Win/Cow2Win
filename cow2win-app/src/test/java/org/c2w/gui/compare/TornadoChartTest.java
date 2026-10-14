package org.c2w.gui.compare;

import org.c2w.gui.common.IconLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link TornadoChart}: shared scale, bar lengths and the color of the difference - headless. */
class TornadoChartTest {

    private static TornadoChart chart() {
        return new TornadoChart("Power", Integer::toString, d -> (d > 0 ? "+" : "") + d);
    }

    @Test
    @DisplayName("Both sides share one scale: the maximum over all enabled left and right values")
    void sharedMaximum() {
        TornadoChart chart = chart();
        chart.setRows(List.of(
                new TornadoChart.Row("A", 100, 300, null, true),
                new TornadoChart.Row("B", 500, 200, null, true),
                new TornadoChart.Row("C", 900, 900, null, false)), true);

        assertEquals(500, chart.scaleMax());
        assertEquals(600, chart.totalLeft());
        assertEquals(500, chart.totalRight());
        assertEquals(50, TornadoChart.barLength(250, chart.scaleMax(), 100));
        assertEquals(100, TornadoChart.barLength(500, chart.scaleMax(), 100));
    }

    @Test
    @DisplayName("Without a right side, only the left values count")
    void withoutRightSide() {
        TornadoChart chart = chart();
        chart.setRows(List.of(new TornadoChart.Row("A", 100, 300, null, true)), false);

        assertEquals(100, chart.scaleMax());
        assertEquals(0, chart.totalRight());
    }

    @Test
    @DisplayName("All values 0: no bars, no error, also when painted")
    void allZero() {
        TornadoChart chart = chart();
        chart.setTotalLabel("Total");
        chart.setRows(List.of(new TornadoChart.Row("A", 0, 0, null, true),
                new TornadoChart.Row("B", 0, 0, null, false)), true);

        assertEquals(0, chart.scaleMax());
        assertEquals(0, TornadoChart.barLength(0, 0, 200));
        assertEquals(0, TornadoChart.barLength(10, 0, 200));

        chart.setSize(chart.getPreferredSize());
        BufferedImage image = new BufferedImage(chart.getWidth(), chart.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        assertDoesNotThrow(() -> chart.paint(g));
        g.dispose();
    }

    @Test
    @DisplayName("The difference is green if the right side has more, red if less, neutral otherwise")
    void diffColor() {
        assertEquals(IconLoader.GREEN, TornadoChart.diffColor(5, Color.GRAY));
        assertEquals(IconLoader.RED, TornadoChart.diffColor(-5, Color.GRAY));
        assertEquals(Color.GRAY, TornadoChart.diffColor(0, Color.GRAY));
    }

    @Test
    @DisplayName("The tooltip belongs to the row under the mouse")
    void rowAt() {
        TornadoChart chart = chart();
        chart.setRows(List.of(new TornadoChart.Row("A", 1, 2, "tip A", true),
                new TornadoChart.Row("B", 1, 2, "tip B", true)), true);

        assertEquals(-1, chart.rowAt(0), "the title is no row");
        int firstRow = chart.getPreferredSize().height - chart.getInsets().bottom - 2 * 25;
        assertEquals(0, chart.rowAt(firstRow + 1));
        assertEquals(1, chart.rowAt(firstRow + 26));
        assertEquals(-1, chart.rowAt(firstRow + 60));
    }
}
