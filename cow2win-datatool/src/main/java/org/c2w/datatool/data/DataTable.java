package org.c2w.datatool.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The rows of one data file plus what is needed to write it back unchanged. */
public final class DataTable {

    private final TableKind kind;
    private final List<Row> rows;
    private final String fileText;
    private final boolean trailingNewline;
    private final Set<String> deletedIds = new LinkedHashSet<>();
    private boolean dirty;

    DataTable(TableKind kind, List<Row> rows, String fileText) {
        this.kind = kind;
        this.rows = new ArrayList<>(rows);
        this.fileText = fileText;
        this.trailingNewline = fileText.endsWith("\n");
    }

    public TableKind kind() {
        return kind;
    }

    /** Live list - change it through {@link DataSet} so the table is marked as changed. */
    public List<Row> rows() {
        return rows;
    }

    public boolean isDirty() {
        return dirty;
    }

    void markDirty() {
        dirty = true;
    }

    /** File content as loaded. */
    String fileText() {
        return fileText;
    }

    boolean trailingNewline() {
        return trailingNewline;
    }

    /** Ids of saved entries deleted in this session. */
    Set<String> deletedIds() {
        return deletedIds;
    }

    public Row findById(String id) {
        if (id == null) {
            return null;
        }
        for (Row row : rows) {
            if (id.equals(row.getString(Fields.ID))) {
                return row;
            }
        }
        return null;
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Row row : rows) {
            if (row.getString(Fields.ID) != null) {
                ids.add(row.getString(Fields.ID));
            }
        }
        return ids;
    }
}
