package org.c2w.data.journal.parse;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.LogDirection;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The head data carried by the file name of an exported battle log:
 * <pre>
 * DE: 24-09-2026 Deutscher Bund Server 133 (193861) - Das Schwarze Auge Server 79 (114834) Sieg +851 Rangpunkte Angriffs-Log.csv
 * EN: 24-09-2026 Deutscher Bund Server 133 (193861) - Das Schwarze Auge Server 79 (114834) Victory +851 ranking pts. Attack Log.csv
 * FR: 24-09-2026 Deutscher Bund Serveur 133 (193861) - Das Schwarze Auge Serveur 79 (114834) Victoire +851 pts de classement Journal d'attaque.csv
 * </pre>
 * The words come from the {@link BattleLogVocabulary} of each language. Guild
 * names may contain spaces, digits and Cyrillic, so they are anchored on the
 * game guild id in parentheses. A browser download suffix like {@code " (1)"}
 * before the extension is ignored. The first guild is always the exporting one.
 *
 * <p>The result is derived from the ranking points (see
 * {@link BattleResult#fromRankingPoints}); the result word is only a cross-check,
 * see {@link #problems()}.
 *
 * @param date          battle day ({@code DD-MM-YYYY} in every language)
 * @param ownGuild      first guild = exporting guild
 * @param opponent      second guild
 * @param resultWord    the result word exactly as in the file name
 * @param rankingPoints ranking points (may be negative)
 * @param result        derived from {@code rankingPoints}, {@code null} if not a possible value
 * @param direction     attack or defense log
 * @param language      language whose words matched
 * @param problems      inconsistencies between result word and ranking points (e.g. "Sieg" with
 *                      negative points, or points that are no possible result) - empty if all fits
 */
public record BattleLogFileName(
        LocalDate date,
        GuildRef ownGuild,
        GuildRef opponent,
        String resultWord,
        int rankingPoints,
        BattleResult result,
        LogDirection direction,
        String language,
        List<String> problems
) {

    public BattleLogFileName {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    private static final Pattern DOWNLOAD_SUFFIX = Pattern.compile(" \\(\\d+\\)$");
    private static final Pattern CSV_EXTENSION = Pattern.compile("\\.csv$", Pattern.CASE_INSENSITIVE);

    /**
     * Parses {@code fileName} (a path is stripped) against the vocabularies of all
     * languages; empty if it matches none (e.g. a renamed file or an invalid date).
     */
    public static Optional<BattleLogFileName> parse(String fileName, Collection<BattleLogVocabulary> vocabularies) {
        if (fileName == null) {
            return Optional.empty();
        }
        String name = stripPathAndExtension(fileName);
        for (BattleLogVocabulary vocabulary : vocabularies) {
            Optional<BattleLogFileName> parsed = parse(name, vocabulary);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    // --- private ---

    private static String stripPathAndExtension(String fileName) {
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = CSV_EXTENSION.matcher(name).replaceFirst("");
        return DOWNLOAD_SUFFIX.matcher(name).replaceFirst("");
    }

    private static Optional<BattleLogFileName> parse(String name, BattleLogVocabulary vocabulary) {
        Pattern pattern = pattern(vocabulary);
        if (pattern == null) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(name);
        if (!m.matches()) {
            return Optional.empty();
        }
        LocalDate date;
        try {
            date = LocalDate.of(Integer.parseInt(m.group("year")), Integer.parseInt(m.group("month")),
                    Integer.parseInt(m.group("day")));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
        GuildRef own = new GuildRef(m.group("ownName"), Integer.parseInt(m.group("ownServer")),
                Long.parseLong(m.group("ownId")));
        GuildRef opponent = new GuildRef(m.group("oppName"), Integer.parseInt(m.group("oppServer")),
                Long.parseLong(m.group("oppId")));
        int rankingPoints = Integer.parseInt(m.group("points"));
        LogDirection direction = vocabulary.matches(BattleLogVocabulary.FILE_NAME_ATTACK_LOG, m.group("direction"))
                ? LogDirection.ATTACK : LogDirection.DEFENSE;
        String resultWord = m.group("result");
        BattleResult result = BattleResult.fromRankingPoints(rankingPoints);
        return Optional.of(new BattleLogFileName(date, own, opponent, resultWord, rankingPoints, result, direction,
                vocabulary.language(), resultProblems(resultWord, rankingPoints, result, vocabulary)));
    }

    private static List<String> resultProblems(String resultWord, int rankingPoints, BattleResult result,
                                               BattleLogVocabulary vocabulary) {
        if (result == null) {
            return List.of("ranking points " + rankingPoints + " are no possible result"
                    + " (0 = running, 375 = draw, >= 750 = win, < 0 = loss)");
        }
        String expectedKey = switch (result) {
            case WIN -> BattleLogVocabulary.FILE_NAME_WIN;
            case LOSS -> BattleLogVocabulary.FILE_NAME_LOSS;
            case DRAW, RUNNING -> BattleLogVocabulary.FILE_NAME_DRAW;
        };
        if (!vocabulary.matches(expectedKey, resultWord)) {
            return List.of("result word '" + resultWord + "' does not fit " + rankingPoints
                    + " ranking points (" + result + ")");
        }
        return List.of();
    }

    /** The file name regex for one language, {@code null} if a required word is missing. */
    private static Pattern pattern(BattleLogVocabulary v) {
        String server = alternatives(v.texts(BattleLogVocabulary.FILE_NAME_SERVER));
        List<String> resultWords = new ArrayList<>();
        resultWords.addAll(v.texts(BattleLogVocabulary.FILE_NAME_WIN));
        resultWords.addAll(v.texts(BattleLogVocabulary.FILE_NAME_LOSS));
        resultWords.addAll(v.texts(BattleLogVocabulary.FILE_NAME_DRAW));
        String results = alternatives(resultWords);
        String rankingPoints = alternatives(v.texts(BattleLogVocabulary.FILE_NAME_RANKING_POINTS));
        List<String> directionWords = new ArrayList<>(v.texts(BattleLogVocabulary.FILE_NAME_ATTACK_LOG));
        directionWords.addAll(v.texts(BattleLogVocabulary.FILE_NAME_DEFENSE_LOG));
        String directions = alternatives(directionWords);
        if (server == null || results == null || rankingPoints == null || directions == null) {
            return null;
        }
        String regex = "(?<day>\\d{2})-(?<month>\\d{2})-(?<year>\\d{4}) "
                + "(?<ownName>.+?) " + server + " (?<ownServer>\\d+) \\((?<ownId>\\d+)\\)"
                + " - "
                + "(?<oppName>.+?) " + server + " (?<oppServer>\\d+) \\((?<oppId>\\d+)\\)"
                + " (?<result>" + results + ") (?<points>[+-]?\\d+) " + rankingPoints
                + " (?<direction>" + directions + ")";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** A non-capturing regex group of the quoted words (straight and typographic apostrophe alike), {@code null} for none. */
    private static String alternatives(List<String> words) {
        String joined = words.stream()
                .filter(w -> w != null && !w.isBlank())
                .map(w -> Pattern.quote(w.strip()).replace("'", "\\E['’]\\Q"))
                .collect(Collectors.joining("|"));
        return joined.isEmpty() ? null : "(?:" + joined + ")";
    }
}
