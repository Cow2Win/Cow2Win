package org.c2w.gui.fort;

import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.c2w.gui.fort.FortificationPanel.FillLevel.*;
import static org.junit.jupiter.api.Assertions.*;

/** The GUI-free parts of the {@link FortificationPanel} tile. */
class FortificationPanelTest {

    @Test
    @DisplayName("The stripe is red for an empty, gold for a partly and green for a fully filled fortification")
    void fillLevel() {
        assertEquals(EMPTY, FortificationPanel.fillLevel(0, 5));
        assertEquals(PARTIAL, FortificationPanel.fillLevel(1, 5));
        assertEquals(PARTIAL, FortificationPanel.fillLevel(4, 5));
        assertEquals(FULL, FortificationPanel.fillLevel(5, 5));
    }

    @Test
    @DisplayName("Power from one million on is shown in millions with two decimals, below that in full")
    void compactPower() {
        String german = LanguageService.textIn("deutsch", "fortification.powerMillions");
        assertEquals("1,05 Mio", FortificationPanel.compactPower(1_050_000, Locale.GERMANY, german));
        assertEquals("812.345", FortificationPanel.compactPower(812_345, Locale.GERMANY, german));
        assertEquals("0", FortificationPanel.compactPower(0, Locale.GERMANY, german));

        String english = LanguageService.textIn("english", "fortification.powerMillions");
        assertEquals("1.05 M", FortificationPanel.compactPower(1_050_000, Locale.UK, english));
    }

    @Test
    @DisplayName("The buff percentage gets a sign when positive")
    void formatPercent() {
        assertEquals("+8 %", FortificationPanel.formatPercent(8));
        assertEquals("0 %", FortificationPanel.formatPercent(0));
        assertEquals("-5 %", FortificationPanel.formatPercent(-5));
    }
}
