package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class GridPanel extends JPanel {

    private final int rows;
    private final int columns;
    private final int hGap;
    private final int vGap;
    private final Map<Point, JComponent> componentsByCell = new HashMap<>();

    public GridPanel(int rows, int columns) {
        this(rows, columns, 4, 4);
    }

    public GridPanel(int rows, int columns, int hGap, int vGap) {
        super(new GridBagLayout());
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("rows/columns must be positive");
        }
        this.rows = rows;
        this.columns = columns;
        this.hGap = hGap;
        this.vGap = vGap;
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    /**
     * Sets component at position (row, column) in the grid - any component
     * already set there will be removed first. row/column are
     * 0-based (0 <= row < rows(), 0 <= column < columns()).
     */
    public void setComponentAt(int row, int column, JComponent component) {
        requireValidCell(row, column);
        clearCellAt(row, column);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column;
        gbc.gridy = row;
        gbc.insets = new Insets(vGap / 2, hGap / 2, vGap / 2, hGap / 2);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        add(component, gbc);
        componentsByCell.put(new Point(column, row), component);
        revalidate();
        repaint();
    }

    /** Removes the component at (row, column), if one is set there. Otherwise has no effect. */
    public void clearCellAt(int row, int column) {
        requireValidCell(row, column);
        JComponent existing = componentsByCell.remove(new Point(column, row));
        if (existing != null) {
            remove(existing);
        }
    }

    /** Returns the component set at (row, column), or null if the cell is empty. */
    public JComponent componentAt(int row, int column) {
        requireValidCell(row, column);
        return componentsByCell.get(new Point(column, row));
    }

    private void requireValidCell(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IndexOutOfBoundsException(
                    "Cell (" + row + ", " + column + ") is outside the " + rows + "x" + columns + " grid");
        }
    }
}
