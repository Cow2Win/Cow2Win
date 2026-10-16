package org.c2w.gui.stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ColumnGridLayout}: how many columns fit, and the row-wise placement. */
class ColumnGridLayoutTest {

    @Test
    @DisplayName("Columns of width 100 with a gap of 10: 1, 2, 3 by width - at least 1 even if none fits")
    void columns() {
        assertEquals(1, ColumnGridLayout.columns(100, 100, 10));
        assertEquals(1, ColumnGridLayout.columns(209, 100, 10));
        assertEquals(2, ColumnGridLayout.columns(210, 100, 10));
        assertEquals(2, ColumnGridLayout.columns(319, 100, 10));
        assertEquals(3, ColumnGridLayout.columns(320, 100, 10));
        assertEquals(1, ColumnGridLayout.columns(40, 100, 10), "very narrow");
        assertEquals(1, ColumnGridLayout.columns(0, 100, 10));
        assertEquals(1, ColumnGridLayout.columns(-50, 100, 10));
    }

    @Test
    @DisplayName("Read row by row: 5 components in 2 columns - 1 2 / 3 4 / 5, left-aligned in fixed widths")
    void placesRowByRow() {
        ColumnGridLayout layout = new ColumnGridLayout(10);
        layout.setCellWidth(100);
        layout.setColumns(2);
        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createEmptyBorder());
        for (int i = 0; i < 5; i++) {
            JComponent box = new JPanel();
            box.setPreferredSize(new Dimension(80, 20));
            panel.add(box);
        }

        assertEquals(new Dimension(210, 60), panel.getPreferredSize());
        panel.setSize(500, 60);
        panel.doLayout();
        assertEquals(new Rectangle(0, 0, 100, 20), panel.getComponent(0).getBounds());
        assertEquals(new Rectangle(110, 0, 100, 20), panel.getComponent(1).getBounds());
        assertEquals(new Rectangle(0, 20, 100, 20), panel.getComponent(2).getBounds());
        assertEquals(new Rectangle(0, 40, 100, 20), panel.getComponent(4).getBounds());

        layout.setColumns(9);
        assertEquals(new Dimension(5 * 100 + 4 * 10, 20), layout.preferredLayoutSize(panel), "never wider than the components");
    }
}
