package org.c2w.datatool.data;

/**
 * One finding of the check.
 *
 * @param severity errors prevent saving, warnings don't
 * @param table    tab of the finding
 * @param row      row index in that table's model, or -1 if not tied to a row
 * @param field    field of the cell, or null
 * @param message  text for the problem list
 */
public record Problem(Severity severity, TableKind table, int row, String field, String message) {

    public enum Severity { ERROR, WARNING }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + "  " + table.title() + (row >= 0 ? ", row " + (row + 1) : "") + ": " + message;
    }
}
