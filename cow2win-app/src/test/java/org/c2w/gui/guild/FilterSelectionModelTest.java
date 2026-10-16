package org.c2w.gui.guild;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Which line the guild entry dialog shows and selects after a filter change - no display needed. */
class FilterSelectionModelTest {

    private static final String A1 = "a1";
    private static final String A2 = "a2";
    private static final String B1 = "b1";

    @Test
    @DisplayName("The selected line still matches the filter: it stays selected")
    void matchingSelectionStays() {
        assertSame(A2, FilterSelectionModel.selectionAfterFilterChange(A2, List.of(A1, A2)));
    }

    @Test
    @DisplayName("The selected line no longer matches: the first shown line is selected")
    void otherwiseFirstShownLine() {
        assertSame(A1, FilterSelectionModel.selectionAfterFilterChange(B1, List.of(A1, A2)));
        assertSame(A1, FilterSelectionModel.selectionAfterFilterChange(null, List.of(A1, A2)));
    }

    @Test
    @DisplayName("Empty list: nothing is selected")
    void emptyListSelectsNothing() {
        assertNull(FilterSelectionModel.selectionAfterFilterChange(B1, List.of()));
        assertNull(FilterSelectionModel.selectionAfterFilterChange(null, List.of()));
    }

    @Test
    @DisplayName("A line edited in the editor stays shown although it no longer matches - until the next filter change")
    void editedLineKeptUntilFilterChange() {
        FilterSelectionModel<String> model = new FilterSelectionModel<>();
        model.rowSelected(A1);
        assertFalse(model.isShown(A1, false), "not edited yet - hidden like any other line");

        model.rowEdited(A1);
        assertTrue(model.isShown(A1, false));
        assertTrue(model.isShown(A1, false), "re-sorting without a filter change keeps it shown");
        assertFalse(model.isShown(B1, false), "only the edited line is kept");
        assertTrue(model.isShown(B1, true));

        model.filterChanged();
        assertFalse(model.isShown(A1, false));
        assertNull(model.rowKeptVisible());
    }

    @Test
    @DisplayName("Selecting another line ends the exception; re-selecting the edited line keeps it")
    void selectingAnotherLineEndsException() {
        FilterSelectionModel<String> model = new FilterSelectionModel<>();
        model.rowSelected(A1);
        model.rowEdited(A1);

        model.rowSelected(A1);
        assertTrue(model.isShown(A1, false));

        model.rowSelected(B1);
        assertFalse(model.isShown(A1, false));

        model.rowEdited(B1);
        model.rowSelected(null);
        assertFalse(model.isShown(B1, false));
    }
}
