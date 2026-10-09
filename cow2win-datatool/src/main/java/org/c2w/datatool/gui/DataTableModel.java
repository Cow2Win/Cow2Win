package org.c2w.datatool.gui;

import org.c2w.datatool.data.ColumnSpec;
import org.c2w.datatool.data.ColumnType;
import org.c2w.datatool.data.DataSet;
import org.c2w.datatool.data.Fields;
import org.c2w.datatool.data.Row;
import org.c2w.datatool.data.TableKind;

import javax.swing.table.AbstractTableModel;
import java.awt.Toolkit;
import java.util.List;
import java.util.Set;

/** The generic table model behind every tab: rows of one {@link TableKind}, columns from its {@link ColumnSpec}s. */
final class DataTableModel extends AbstractTableModel {

    /** Edited in the cell itself; id lists and avatars are edited in a dialog instead. */
    private static final Set<ColumnType> CELL_EDITED = Set.of(ColumnType.TEXT, ColumnType.INTEGER, ColumnType.ENUM,
            ColumnType.NAME, ColumnType.ID_REF, ColumnType.MARK);

    private final DataSet data;
    private final TableKind kind;
    private final List<ColumnSpec> columns;
    private final Runnable onChange;

    DataTableModel(DataSet data, TableKind kind, List<ColumnSpec> columns, Runnable onChange) {
        this.data = data;
        this.kind = kind;
        this.columns = columns;
        this.onChange = onChange;
    }

    TableKind kind() {
        return kind;
    }

    ColumnSpec column(int index) {
        return columns.get(index);
    }

    int columnOf(String field) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).field().equals(field)) {
                return i;
            }
        }
        return -1;
    }

    Row row(int index) {
        return data.table(kind).rows().get(index);
    }

    @Override
    public int getRowCount() {
        return data.table(kind).rows().size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).header();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ColumnSpec column = columns.get(columnIndex);
        Row row = row(rowIndex);
        if (column.type() == ColumnType.DISPLAY_NAME) {
            String id = row.getString(Fields.ID);
            return id == null ? null : data.displayName(column.ref(), id);
        }
        return row.get(column.field());
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        ColumnSpec column = columns.get(columnIndex);
        return CELL_EDITED.contains(column.type()) && column.isEditable(row(rowIndex));
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        ColumnSpec column = columns.get(columnIndex);
        Object converted = value;
        if (column.type() == ColumnType.INTEGER && value != null && !(value instanceof Integer)) {
            String text = value.toString().strip();
            if (text.isEmpty()) {
                converted = null;
            } else {
                try {
                    converted = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }
            }
        }
        update(rowIndex, column.field(), converted);
    }

    /** Sets a field through the data set and refreshes the row (other cells may depend on it). */
    void update(int rowIndex, String field, Object value) {
        data.setValue(kind, row(rowIndex), field, value);
        fireTableRowsUpdated(rowIndex, rowIndex);
        onChange.run();
    }
}
