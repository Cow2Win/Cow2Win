package org.c2w.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogDataVersionTest {

    @Test
    void parsesWithAndWithoutSequence() {
        assertEquals(new CatalogDataVersion(LocalDate.of(2026, 10, 17), 0), CatalogDataVersion.parse("2026-10-17"));
        assertEquals(new CatalogDataVersion(LocalDate.of(2026, 11, 2), 2), CatalogDataVersion.parse("2026-11-02.2"));
        assertEquals("2026-10-17", CatalogDataVersion.parse("2026-10-17").toString());
        assertEquals("2026-11-02.2", CatalogDataVersion.parse("2026-11-02.2").toString());
    }

    @Test
    void ordersByDateThenSequenceNotAsAString() {
        List<CatalogDataVersion> versions = new ArrayList<>(List.of(
                CatalogDataVersion.parse("2026-11-02.10"),
                CatalogDataVersion.parse("2026-12-01"),
                CatalogDataVersion.parse("2026-11-02.9"),
                CatalogDataVersion.parse("2026-11-02"),
                CatalogDataVersion.parse("2026-11-02.1")));
        versions.sort(null);
        assertEquals(List.of("2026-11-02", "2026-11-02.1", "2026-11-02.9", "2026-11-02.10", "2026-12-01"),
                versions.stream().map(CatalogDataVersion::toString).toList());
        assertTrue(CatalogDataVersion.parse("2026-11-02").compareTo(CatalogDataVersion.parse("2026-11-02.1")) < 0,
                "no .n counts as 0");
        assertEquals(0, CatalogDataVersion.parse("2026-11-02").compareTo(CatalogDataVersion.parse("2026-11-02")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "2026-10", "2026-13-01", "2026-02-30", "17.10.2026", "2026-10-17.", "2026-10-17.0",
            "2026-10-17.x", "2026-10-17.-1", "v2026-10-17", "2026-10-17.1.2"})
    void rejectsInvalidValues(String text) {
        assertThrows(IllegalArgumentException.class, () -> CatalogDataVersion.parse(text));
    }

    @Test
    void nextIsTodayOrTheNextSequenceOfTheSameDay() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        assertEquals("2026-10-17", CatalogDataVersion.parse("2026-10-09").next(today).toString());
        assertEquals("2026-10-17", CatalogDataVersion.parse("2026-10-09.3").next(today).toString());
        assertEquals("2026-10-17.1", CatalogDataVersion.parse("2026-10-17").next(today).toString());
        assertEquals("2026-10-17.3", CatalogDataVersion.parse("2026-10-17.2").next(today).toString());
        // A version "from the future" (wrong clock) still grows.
        assertEquals("2026-10-20.1", CatalogDataVersion.parse("2026-10-20").next(today).toString());
    }
}
