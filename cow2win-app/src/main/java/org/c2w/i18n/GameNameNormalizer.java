package org.c2w.i18n;

import java.util.Locale;

/**
 * The one place that defines how in-game names (heroes, titans, pets, totems,
 * fortifications, buff and color texts of a Clash of Worlds battle log) are
 * compared with the names in the language files: non-breaking spaces (U+00A0,
 * used in French logs) count as normal spaces, runs of whitespace as a single
 * space, surrounding whitespace is ignored, the typographic apostrophe
 * ({@code ’}) equals the straight one ({@code '}), and case does not matter.
 *
 * <p><b>Only for comparing.</b> Whatever is stored (battle log records, the
 * journal) always keeps the raw text exactly as it was in the log.
 */
public final class GameNameNormalizer {

    private GameNameNormalizer() {
        // Utility class, no instantiation
    }

    /**
     * {@code text} with non-breaking spaces turned into normal ones, whitespace
     * runs collapsed, stripped and {@code ’} replaced by {@code '} - case is
     * kept. {@code ""} for {@code null}.
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(' ', ' ')
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** The comparison key of {@code text}: {@link #clean} in lower case. {@code ""} for {@code null}. */
    public static String key(String text) {
        return clean(text).toLowerCase(Locale.ROOT);
    }

    /** True if both texts have the same {@link #key} (two blank texts are equal). */
    public static boolean sameName(String a, String b) {
        return key(a).equals(key(b));
    }
}
