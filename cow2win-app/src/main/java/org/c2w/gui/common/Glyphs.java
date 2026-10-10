package org.c2w.gui.common;

import javax.swing.*;
import java.awt.Font;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Special characters in texts shown by Swing: the font of the look and feel (Material, "Noto
 * Sans Display") has no glyph for many symbols (e.g. "→", "−", "≤", "✔", "⚔", "▸") and would
 * show an empty box. {@link #displayable(String)} replaces such a character by a similar one
 * the font can show, {@link #prefix(String)} drops a merely decorative symbol.
 */
public final class Glyphs {

    /** Replacements the font can display, for characters it cannot. */
    private static final Map<Integer, String> FALLBACKS = Map.of(
            (int) '→', "->",
            (int) '←', "<-",
            (int) '−', "-",
            (int) '≤', "<=",
            (int) '≈', "~",
            (int) '▸', "›",
            (int) '▾', "˅",
            (int) '⚔', "×");

    private Glyphs() {
    }

    /** {@code text} with every character the UI font cannot display replaced (see {@link #FALLBACKS}). */
    public static String displayable(String text) {
        return displayable(text, uiFontCanDisplay());
    }

    /** {@code symbol} followed by a space if the UI font can display it, else {@code ""} - for decorative symbols. */
    public static String prefix(String symbol) {
        return prefix(symbol, uiFontCanDisplay());
    }

    /** The marker in front of a collapsible section's title: "▾ " open, "▸ " closed (or what the font can display). */
    public static String expander(boolean expanded) {
        return displayable(expanded ? "▾ " : "▸ ");
    }

    static String displayable(String text, IntPredicate canDisplay) {
        if (text == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(c -> {
            String fallback = FALLBACKS.get(c);
            if (fallback != null && !canDisplay.test(c)) {
                result.append(fallback);
            } else {
                result.appendCodePoint(c);
            }
        });
        return result.toString();
    }

    static String prefix(String symbol, IntPredicate canDisplay) {
        return symbol.codePoints().allMatch(canDisplay) ? symbol + " " : "";
    }

    /** What the look and feel's label font (the same family as tables, buttons, lists) can display. */
    private static IntPredicate uiFontCanDisplay() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? c -> true : font::canDisplay;
    }
}
