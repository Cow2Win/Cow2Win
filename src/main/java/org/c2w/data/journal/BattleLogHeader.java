package org.c2w.data.journal;

import java.time.LocalDate;

/**
 * The head data of a battle log. The CSV itself only has a column header row,
 * so everything but {@link #language} comes from the exported file name, e.g.
 * {@code 24-09-2026 Deutscher Bund Server 133 (193861) - Das Schwarze Auge Server 79 (114834) Sieg +851 Rangpunkte Angriffs-Log.csv}.
 *
 * <p>If the file name cannot be recognized (e.g. the file was renamed), the
 * content is parsed anyway: then {@code date}, {@code ownGuild},
 * {@code opponent}, {@code rankingPoints}, {@code result} and
 * {@code direction} are {@code null} (see {@link #isComplete()}), and the
 * parse result carries a problem for line 0.
 *
 * @param date          battle day
 * @param ownGuild      the exporting guild - always the first guild in the file name
 * @param opponent      the opponent guild
 * @param rankingPoints ranking points of the own guild from the file name
 * @param result        derived from the ranking points, see {@link BattleResult#fromRankingPoints};
 *                      {@code null} if the value is not a possible result
 * @param direction     attack or defense log
 * @param language      game language of the export as {@code LanguageService} names it
 *                      ({@code deutsch}, {@code english}, {@code francais}) - taken from the
 *                      column header row, never {@code null}
 * @param fileName      original file name (without path), never {@code null}
 */
public record BattleLogHeader(
        LocalDate date,
        GuildRef ownGuild,
        GuildRef opponent,
        Integer rankingPoints,
        BattleResult result,
        LogDirection direction,
        String language,
        String fileName
) {
    public BattleLogHeader {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("BattleLogHeader needs a language");
        }
        fileName = fileName == null ? "" : fileName;
    }

    /** True if the file name was recognized: all file name fields except possibly {@code result} are set. */
    public boolean isComplete() {
        return date != null && ownGuild != null && opponent != null && rankingPoints != null && direction != null;
    }
}
