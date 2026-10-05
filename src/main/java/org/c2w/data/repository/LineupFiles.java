package org.c2w.data.repository;

import org.c2w.eval.LineupAlgorithm;
import org.c2w.i18n.LanguageService;

import java.nio.file.Path;

/**
 * Small helper around the reserved "Original" lineup file that every guild
 * folder can hold. The Original lineup is a fixed record
 * of the guild's ACTUAL in-game deployment (the teams as they are really set
 * up on the Hero Wars side), kept separate from the optimized lineups the
 * {@link LineupAlgorithm}s produce, so the real deployment is
 * never lost when the player experiments with optimizations - it stays
 * available for comparing the two and working out which concrete changes to
 * make in the game.
 *
 * <p>The Original lineup is maintained EXCLUSIVELY through the two guild
 * team-entry dialogs ({@code GuildHeroEntryDialog}/{@code GuildTitanEntryDialog}).
 * Everywhere else it is treated as read-only: running an algorithm, saving,
 * clearing, removing or editing a single fortification is refused while it is
 * the active lineup (see the callers of {@link #isOriginal}). It can still be
 * selected in the toolbar's lineup combo box for viewing/comparison.
 *
 * <p>The file name is a fixed, non-localized identifier ({@value #ORIGINAL_FILE_NAME})
 * so the Original lineup stays recognizable regardless of the display
 * language. In the UI it is called "Live" (see {@link #displayName(String)}) - only the
 * file name and the internal identifiers keep "Original".
 */
public final class LineupFiles {

    /** File name suffix shared by every ".lineup" file (see {@link #displayName(String)}). */
    public static final String SUFFIX = ".lineup";

    /** Fixed file name of the per-guild "Original" baseline lineup - shown as "Live" in the UI. */
    public static final String ORIGINAL_FILE_NAME = "Original" + SUFFIX;

    /** File name a new lineup must not get either: it would look like the Original, which is shown as "Live". */
    private static final String LIVE_FILE_NAME = "Live" + SUFFIX;

    /** Language file key of the Original lineup's display name ("Live"). */
    private static final String KEY_LIVE = "lineup.live";

    private LineupFiles() {
    }

    /** True if the given path points at a guild's Original lineup file (matched by file name, case-insensitively). */
    public static boolean isOriginal(Path lineupPath) {
        return lineupPath != null && lineupPath.getFileName() != null
                && isOriginalFileName(lineupPath.getFileName().toString());
    }

    /** True if the given bare file name is the reserved Original lineup file name (case-insensitive). */
    public static boolean isOriginalFileName(String fileName) {
        return fileName != null && fileName.equalsIgnoreCase(ORIGINAL_FILE_NAME);
    }

    /** The Original lineup file inside the given guild folder. */
    public static Path originalPathFor(Path guildDir) {
        if (guildDir == null) {
            throw new IllegalArgumentException("guildDir must not be null");
        }
        return guildDir.resolve(ORIGINAL_FILE_NAME);
    }

    /**
     * True if {@code fileName} is reserved and must not be used for a new lineup: the Original
     * file name and "Live.lineup" (the Original's display name), both case-insensitive.
     */
    public static boolean isReservedFileName(String fileName) {
        return isOriginalFileName(fileName) || (fileName != null && fileName.equalsIgnoreCase(LIVE_FILE_NAME));
    }

    /**
     * The name of a lineup file as shown in the UI: "Live" (in the configured language) for the
     * Original lineup, otherwise the file name without the {@value #SUFFIX} suffix.
     */
    public static String displayName(String fileName) {
        if (fileName == null) {
            return "";
        }
        if (isOriginalFileName(fileName)) {
            return LanguageService.displayName(KEY_LIVE);
        }
        return fileName.endsWith(SUFFIX) ? fileName.substring(0, fileName.length() - SUFFIX.length()) : fileName;
    }
}
