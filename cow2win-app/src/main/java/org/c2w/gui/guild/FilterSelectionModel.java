package org.c2w.gui.guild;

import java.util.List;

/**
 * GUI-free part of the fortification filter / search of {@link GuildTeamEntryDialog}: which line
 * is shown and which one is selected after the player changed the filter.
 *
 * <ul>
 *     <li>A line that does not match the filter is hidden - except the line edited in the editor
 *     area since the last filter change ({@link #rowEdited}): it must not vanish in the middle of
 *     the editing. That exception ends with the next filter change or once another line is
 *     selected.</li>
 *     <li>After a filter change the selected line stays selected if it is still shown, else the
 *     first shown line (in the table's view order) is selected, or none for an empty list.</li>
 * </ul>
 *
 * @param <R> the dialog's line type
 */
final class FilterSelectionModel<R> {

    /** The line edited since the last filter change - shown even if it no longer matches; null for none. */
    private R rowKeptVisible;

    /** Whether {@code row} is shown, given whether it matches the current filter. */
    boolean isShown(R row, boolean matchesFilter) {
        return matchesFilter || (row != null && row == rowKeptVisible);
    }

    /** The player changed {@code row} in the editor area (fortification, member, team ...). */
    void rowEdited(R row) {
        rowKeptVisible = row;
    }

    /** {@code row} is now the selected line (null for none) - ends the exception for any other line. */
    void rowSelected(R row) {
        if (row != rowKeptVisible) {
            rowKeptVisible = null;
        }
    }

    /** The player changed the filter - ends the exception; call before the lines are filtered again. */
    void filterChanged() {
        rowKeptVisible = null;
    }

    /** The line kept visible by the exception, or null. */
    R rowKeptVisible() {
        return rowKeptVisible;
    }

    /**
     * The line to select after a filter change.
     *
     * @param selected          the line selected before the change, or null
     * @param shownInViewOrder  the lines shown after the change, in the table's view order
     * @return {@code selected} if still shown, else the first shown line, null if none is shown
     */
    static <R> R selectionAfterFilterChange(R selected, List<R> shownInViewOrder) {
        if (selected != null && shownInViewOrder.contains(selected)) {
            return selected;
        }
        return shownInViewOrder.isEmpty() ? null : shownInViewOrder.get(0);
    }
}
