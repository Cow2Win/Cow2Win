package org.c2w.gui.journal;

import org.c2w.data.journal.parse.BattleLogCheck.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link OverviewStepPanel}: the "check" column of the import overview - no empty box for a missing glyph. */
class OverviewStepPanelTest {

    @Test
    @DisplayName("The verdict symbol only if the font can display it, otherwise the text alone")
    void verdictTextFollowsFont() {
        for (Verdict verdict : Verdict.values()) {
            String text = JournalTexts.of("verdict", verdict);
            String withSymbol = OverviewStepPanel.verdictText(verdict, c -> true);
            assertTrue(withSymbol.endsWith(" " + text), withSymbol);
            assertNotEquals(text, withSymbol);
        }
        // Like the look and feel's Noto Sans Display: no "✔", "✖", "⏳".
        assertEquals(JournalTexts.of("verdict", Verdict.MATCHES),
                OverviewStepPanel.verdictText(Verdict.MATCHES, c -> c < 0x2000));
        assertEquals(JournalTexts.of("verdict", Verdict.MISMATCH),
                OverviewStepPanel.verdictText(Verdict.MISMATCH, c -> c < 0x2000));
        assertEquals("? " + JournalTexts.of("verdict", Verdict.NOT_CHECKABLE),
                OverviewStepPanel.verdictText(Verdict.NOT_CHECKABLE, c -> c < 0x2000));
    }
}
