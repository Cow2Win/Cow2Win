package org.c2w.data.repository;

import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LineupFiles}: the Original lineup is shown as "Live", and both names are reserved. */
class LineupFilesTest {

    @Test
    @DisplayName("The Original lineup is shown as \"Live\", every other lineup by its name without suffix")
    void displayName() {
        assertEquals(LanguageService.displayName("lineup.live"), LineupFiles.displayName("Original.lineup"));
        assertEquals(LanguageService.displayName("lineup.live"), LineupFiles.displayName("original.lineup"));
        assertEquals("Ziel KW41", LineupFiles.displayName("Ziel KW41.lineup"));
        assertEquals("", LineupFiles.displayName(null));
    }

    @Test
    @DisplayName("\"Live\" and \"Original\" are reserved lineup names, in any case")
    void reservedNames() {
        assertTrue(LineupFiles.isReservedFileName("Live.lineup"));
        assertTrue(LineupFiles.isReservedFileName("live.lineup"));
        assertTrue(LineupFiles.isReservedFileName("Original.lineup"));
        assertTrue(LineupFiles.isReservedFileName("ORIGINAL.lineup"));
        assertFalse(LineupFiles.isReservedFileName("Live KW41.lineup"));
        assertFalse(LineupFiles.isReservedFileName(null));
    }

    @Test
    @DisplayName("The file name stays Original.lineup")
    void fileNameUnchanged() {
        assertEquals("Original.lineup", LineupFiles.ORIGINAL_FILE_NAME);
    }
}
