package org.c2w.gui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link DateValue}: chronological order with an unknown date last, empty text for it. */
class DateValueTest {

    @Test
    @DisplayName("Sorts chronologically, an unknown date after every known one")
    void sortsUnknownLast() {
        DateValue early = new DateValue(LocalDate.of(2026, 1, 2));
        DateValue late = new DateValue(LocalDate.of(2026, 10, 24));
        List<DateValue> values = new ArrayList<>(List.of(DateValue.NONE, late, early));

        Collections.sort(values);

        assertEquals(List.of(early, late, DateValue.NONE), values);
        assertEquals(0, DateValue.NONE.compareTo(new DateValue(null)));
    }

    @Test
    @DisplayName("Shown in the short date format, empty for an unknown date")
    void text() {
        LocalDate date = LocalDate.of(2026, 10, 24);
        assertEquals(DateValue.shortDate(date), new DateValue(date).toString());
        assertFalse(new DateValue(date).toString().isEmpty());
        assertEquals("", DateValue.NONE.toString());
    }
}
