package org.c2w.data.journal.parse;

import org.c2w.data.journal.BattleLogParseResult;
import org.c2w.data.journal.LogDirection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * The sample battle logs under {@code src/test/resources/battlelog}: the same 8
 * battles in the folders {@code de}, {@code en}, {@code fr} (one per game
 * language) - 6 of season 16 and the first 2 of season 17 (12.10.2026, the first
 * log with the lighthouse buff, and 15.10.2026) - 5 of them in {@code es} (see
 * {@link #daysOf}), plus {@code partial} (an earlier German export
 * of the running battle of 01.10.2026), and a shared parser for the tests.
 */
public final class BattleLogTestFiles {

    public static final Path ROOT = Paths.get("src", "test", "resources", "battlelog");

    /** Language folder -> language name as {@code LanguageService} uses it. */
    public static final Map<String, String> LANGUAGE_BY_FOLDER =
            Map.of("de", "deutsch", "en", "english", "es", "espanol", "fr", "francais");

    /** The language folders, sorted. */
    public static final List<String> LANGUAGE_FOLDERS = List.of("de", "en", "es", "fr");

    /** Battle days of the 6 sample battles of season 16, as they start the file names. */
    public static final List<String> SEASON_16_DAYS =
            List.of("14-09-2026", "17-09-2026", "21-09-2026", "24-09-2026", "28-09-2026", "01-10-2026");

    /** Battle days of the 2 sample battles of season 17, as they start the file names. */
    public static final List<String> SEASON_17_DAYS = List.of("12-10-2026", "15-10-2026");

    /** Battle day of the first sample battle with the lighthouse buff (season 17). */
    public static final String LIGHTHOUSE_DAY = "12-10-2026";

    /** Battle days of all 8 sample battles, as they start the file names. */
    public static final List<String> BATTLE_DAYS = concat(SEASON_16_DAYS, SEASON_17_DAYS);

    /**
     * Battle days of the Spanish samples: 14.09. and 17.09. can no longer be exported in the game, and the
     * Spanish export of 01.10. is the final state while the other languages hold the running one.
     */
    public static final List<String> SPANISH_DAYS =
            List.of("21-09-2026", "24-09-2026", "28-09-2026", "12-10-2026", "15-10-2026");

    /** The battle days of language folder {@code folder} ({@code de}, {@code en}, {@code es}, {@code fr}). */
    public static List<String> daysOf(String folder) {
        return folder.equals("es") ? SPANISH_DAYS : BATTLE_DAYS;
    }

    /** The language folders that hold battle day {@code day}. */
    public static List<String> foldersWith(String day) {
        return LANGUAGE_FOLDERS.stream().filter(folder -> daysOf(folder).contains(day)).toList();
    }

    private static BattleLogParser parser;
    private static final Map<Path, BattleLogParseResult> CACHE = new HashMap<>();

    private BattleLogTestFiles() {
    }

    public static synchronized BattleLogParser parser() {
        if (parser == null) {
            parser = BattleLogParser.createDefault();
        }
        return parser;
    }

    /** All CSV files of a folder ({@code de}, {@code en}, {@code fr}, {@code partial}), sorted. */
    public static List<Path> files(String folder) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ROOT.resolve(folder), "*.csv")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    /**
     * The files of {@code folder} of the 6 season-16 battles only - for the import tests built
     * on them (one season, team builder against the guild of 04.10.2026).
     */
    public static List<Path> season16Files(String folder) {
        return files(folder).stream()
                .filter(p -> SEASON_16_DAYS.stream().anyMatch(day -> p.getFileName().toString().startsWith(day)))
                .toList();
    }

    /** All 60 sample files (3 × 16 in de/en/fr + 10 in es + 2 partial). */
    public static List<Path> allFiles() {
        List<Path> files = new ArrayList<>();
        for (String folder : List.of("de", "en", "es", "fr", "partial")) {
            files.addAll(files(folder));
        }
        return files;
    }

    /** The file of {@code folder} for battle day {@code day} ({@code DD-MM-YYYY}) and direction. */
    public static Path file(String folder, String day, LogDirection direction) {
        return files(folder).stream()
                .filter(p -> p.getFileName().toString().startsWith(day))
                .filter(p -> isAttack(p) == (direction == LogDirection.ATTACK))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + direction + " log for " + day + " in " + folder));
    }

    /** Parses {@code file} with the shared parser (cached - records are immutable). */
    public static synchronized BattleLogParseResult parse(Path file) {
        return CACHE.computeIfAbsent(file, f -> {
            try {
                return parser().parse(f);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static BattleLogParseResult parse(String folder, String day, LogDirection direction) {
        return parse(file(folder, day, direction));
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    private static boolean isAttack(Path file) {
        String name = file.getFileName().toString();
        return name.contains("Angriffs-Log") || name.contains("Attack Log") || name.contains("Journal d'attaque")
                || name.contains("Registro de ataques");
    }
}
