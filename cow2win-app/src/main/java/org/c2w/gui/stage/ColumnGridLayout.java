package org.c2w.gui.stage;

import java.awt.*;

/**
 * Lays out the components of a container in columns of one fixed width, left-aligned and read
 * row by row (first row: component 1, 2, 3 …) - for the entries of one fortification in the
 * change plan checklist. The column width and the number of columns are set from outside
 * ({@link #setCellWidth}, {@link #setColumns}; see {@link #columns(int, int, int)}), so that
 * several containers with the same values line their columns up. A row is as high as its
 * highest component.
 */
public class ColumnGridLayout implements LayoutManager {

    private final int gap;
    private int cellWidth;
    private int columns = 1;

    /** @param gap horizontal space between two columns in pixels */
    public ColumnGridLayout(int gap) {
        this.gap = Math.max(0, gap);
    }

    /**
     * How many columns of {@code cellWidth} with {@code gap} between them fit into
     * {@code availableWidth} - at least 1, even if not one fits.
     */
    public static int columns(int availableWidth, int cellWidth, int gap) {
        int step = Math.max(1, cellWidth + Math.max(0, gap));
        return Math.max(1, (availableWidth + Math.max(0, gap)) / step);
    }

    public int gap() {
        return gap;
    }

    public int cellWidth() {
        return cellWidth;
    }

    public void setCellWidth(int cellWidth) {
        this.cellWidth = Math.max(0, cellWidth);
    }

    public int columns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = Math.max(1, columns);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int count = parent.getComponentCount();
            int usedColumns = Math.max(1, Math.min(columns, count));
            int height = 0;
            for (int row = 0; row * columns < count; row++) {
                height += rowHeight(parent, row);
            }
            int width = usedColumns * cellWidth + (usedColumns - 1) * gap;
            return new Dimension(insets.left + width + insets.right, insets.top + height + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int count = parent.getComponentCount();
            int y = insets.top;
            for (int row = 0; row * columns < count; row++) {
                int height = rowHeight(parent, row);
                for (int column = 0; column < columns && row * columns + column < count; column++) {
                    Component component = parent.getComponent(row * columns + column);
                    component.setBounds(insets.left + column * (cellWidth + gap), y, cellWidth, height);
                }
                y += height;
            }
        }
    }

    private int rowHeight(Container parent, int row) {
        int height = 0;
        int count = parent.getComponentCount();
        for (int index = row * columns; index < Math.min(count, (row + 1) * columns); index++) {
            height = Math.max(height, parent.getComponent(index).getPreferredSize().height);
        }
        return height;
    }
}
