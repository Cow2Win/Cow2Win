package org.c2w.gui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** {@link Glyphs}: no empty boxes for characters the UI font cannot display. */
class GlyphsTest {

    /** Like the look and feel's Noto Sans Display: Latin-1, "–", "…", "•", "›", "˅", but no arrows or symbols. */
    private static final IntPredicate NOTO = c -> c < 0x2000 || c == '–' || c == '…' || c == '•' || c == '›'
            || c == '„' || c == '“';

    @Test
    @DisplayName("Characters the font cannot display are replaced by similar ones, all others stay")
    void displayable() {
        assertEquals("1.000 -> 1.200 (-5, <= 3 %, ~ 2)", Glyphs.displayable("1.000 → 1.200 (−5, ≤ 3 %, ≈ 2)", NOTO));
        assertEquals("› Title, ˅ open, a × b, x <- y", Glyphs.displayable("▸ Title, ▾ open, a ⚔ b, x ← y", NOTO));
        assertEquals("„Vale“ – Größe … •", Glyphs.displayable("„Vale“ – Größe … •", NOTO));
        assertNull(Glyphs.displayable(null, NOTO));
    }

    @Test
    @DisplayName("A font that can display everything keeps the text unchanged")
    void displayableFontKeepsText() {
        String text = "1.000 → 1.200 (−5, ≤ 3 %) ▸ ⚔";
        assertEquals(text, Glyphs.displayable(text, c -> true));
    }

    @Test
    @DisplayName("A decorative symbol only if the font can display it - also outside the BMP")
    void prefix() {
        assertEquals("", Glyphs.prefix("🛡", NOTO));
        assertEquals("", Glyphs.prefix("✔", NOTO));
        assertEquals("? ", Glyphs.prefix("?", NOTO));
        assertEquals("🛡 ", Glyphs.prefix("🛡", c -> true));
    }
}
