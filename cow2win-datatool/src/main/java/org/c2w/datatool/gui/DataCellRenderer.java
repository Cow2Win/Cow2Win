package org.c2w.datatool.gui;

import org.c2w.datatool.data.ColumnSpec;
import org.c2w.datatool.data.ColumnType;
import org.c2w.datatool.data.DataSet;

import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders every cell type: id lists with display names, avatars with a small preview, marks in colour. */
final class DataCellRenderer extends DefaultTableCellRenderer {

    static final int ICON_SIZE = 32;
    private static final Color POSITIVE = new Color(0x4CAF50);
    private static final Color NEGATIVE = new Color(0xE57373);

    private final DataSet data;
    private final Map<Path, ImageIcon> icons = new HashMap<>();

    DataCellRenderer(DataSet data) {
        this.data = data;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        DataTableModel model = (DataTableModel) table.getModel();
        ColumnSpec spec = model.column(table.convertColumnIndexToModel(column));
        super.getTableCellRendererComponent(table, text(spec, value), isSelected, hasFocus, row, column);
        setIcon(null);
        if (spec.type() == ColumnType.IMAGE && value != null) {
            setIcon(icon(data.imageFile(model.kind(), value.toString())));
        }
        if (!isSelected) {
            if (spec.type() == ColumnType.MARK && value != null) {
                setForeground("POSITIVE".equals(value) ? POSITIVE : NEGATIVE);
            } else if (!spec.isEditable(model.row(table.convertRowIndexToModel(row)))) {
                setForeground(UIManager.getColor("Label.disabledForeground"));
            } else {
                setForeground(table.getForeground());
            }
        }
        return this;
    }

    private String text(ColumnSpec spec, Object value) {
        if (value == null) {
            return "";
        }
        if (spec.type() == ColumnType.ID_LIST && value instanceof List<?> ids) {
            return ids.stream().map(id -> spec.ref() == null ? id.toString() : data.displayName(spec.ref(), id.toString()))
                    .collect(Collectors.joining(", "));
        }
        return value.toString();
    }

    private ImageIcon icon(Path file) {
        return icons.computeIfAbsent(file, f -> {
            if (!Files.isRegularFile(f)) {
                return null;
            }
            Image image = new ImageIcon(f.toString()).getImage();
            return new ImageIcon(image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
        });
    }
}
