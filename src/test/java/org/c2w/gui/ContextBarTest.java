package org.c2w.gui;

import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** The GUI-free parts of {@link ContextBar}: "Original" lineup status and "unsaved" tooltip. */
class ContextBarTest {

    @TempDir
    Path guildDir;

    @Test
    @DisplayName("An existing Original lineup is shown with its last-modified date in the short format")
    void originalStatusWithDate() throws Exception {
        Path original = Files.writeString(guildDir.resolve("Original.lineup"), "{}");
        LocalDate date = LocalDate.of(2026, 9, 28);
        Files.setLastModifiedTime(original, FileTime.from(date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));

        String expectedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.GERMANY).format(date);
        assertEquals(LanguageService.displayName("context.original", expectedDate),
                ContextBar.originalStatusText(original, Locale.GERMANY));
        assertTrue(expectedDate.contains("28"), expectedDate);
    }

    @Test
    @DisplayName("Without an Original lineup the status says so")
    void originalStatusMissing() {
        String missing = LanguageService.displayName("context.originalMissing");
        assertEquals(missing, ContextBar.originalStatusText(guildDir.resolve("Original.lineup"), Locale.UK));
        assertEquals(missing, ContextBar.originalStatusText(null, Locale.UK));
    }

    @Test
    @DisplayName("The unsaved hint's tooltip names the guild, the lineup or both - none if nothing is unsaved")
    void unsavedTooltipKey() {
        assertNull(ContextBar.unsavedTooltipKey(false, false));
        assertEquals("context.unsavedTooltip.guild", ContextBar.unsavedTooltipKey(true, false));
        assertEquals("context.unsavedTooltip.lineup", ContextBar.unsavedTooltipKey(false, true));
        assertEquals("context.unsavedTooltip.both", ContextBar.unsavedTooltipKey(true, true));
    }
}
