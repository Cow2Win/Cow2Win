package org.c2w.eval;

import org.c2w.util.LanguageService;

import java.util.Map;

/**
 * Short, human-readable prose explaining how each {@link LineupAlgorithm} in
 * {@link LineupAlgorithms#HERO}/{@link LineupAlgorithms#TITAN} actually works - localized through {@link
 * LanguageService}, exactly like every other display string in the app
 * (fortification/hero/titan names, toolbar labels, ...): the actual text
 * lives as {@code algorithm.<key>.description} in every
 * {@code language/<name>/<name>.properties} file, one entry per algorithm.
 *
 * <p>This originally lived in its own English-only sidecar JSON under
 * {@code src/main/resources/data}, on the assumption that the report (the
 * one place this text is meant for) is English-only throughout - that
 * assumption was wrong: {@code ReportGenerator} already looks up
 * fortification/hero/titan names via {@link LanguageService#displayName},
 * so a report generated in German already shows German names there. This
 * class was converted (2026-09-16) to follow that same per-language
 * properties pattern instead of a separate, non-localized file.
 *
 * <p>{@link LineupAlgorithm#displayName()} (e.g. "Best possible lineup") is
 * not itself a usable properties key (spaces, not namespaced under
 * {@code algorithm.*}) - {@link #KEY_BY_DISPLAY_NAME} maps each known
 * algorithm's display name to its dotted key suffix. An algorithm missing
 * from that map (e.g. a new one just added to {@link LineupAlgorithms}
 * without a matching entry here and in the three language files) falls
 * back to an empty description rather than throwing or leaking a raw
 * properties key into the UI/report.
 *
 * <p>The hero and the titan variant of a strategy share the same display
 * name and therefore the same description (since 2026-09-24).
 */
public final class AlgorithmDescriptions {

    private static final Map<String, String> KEY_BY_DISPLAY_NAME = Map.of(
            "Best possible lineup", "bestPossibleLineup",
            "Balanced defense", "balancedDefense",
            "CowScore maximizer", "cowScoreMaximizer",
            "Consensus picks", "consensusPicks",
            ManualLineupAlgorithm.DISPLAY_NAME, "manual"
    );

    private AlgorithmDescriptions() {
    }

    /** Short prose explaining how {@code algorithm} works, in the currently configured language, or {@code ""} if none is on file for it. */
    public static String forAlgorithm(LineupAlgorithm algorithm) {
        return forDisplayName(algorithm.displayName());
    }

    /**
     * Same as {@link #forAlgorithm}, keyed directly by {@link LineupAlgorithm#displayName()} -
     * for callers that only have the name (e.g. a saved {@code Config} value), not a
     * {@link LineupAlgorithm} instance.
     */
    public static String forDisplayName(String displayName) {
        String key = KEY_BY_DISPLAY_NAME.get(displayName);
        if (key == null) {
            return "";
        }
        String languageKey = "algorithm." + key + ".description";
        String value = LanguageService.displayName(languageKey);
        // LanguageService#displayName falls back to the raw id itself when a key is
        // missing from the current language file - treat that the same way this
        // class always has: an empty description, not the internal properties key.
        return value.equals(languageKey) ? "" : value;
    }
}
