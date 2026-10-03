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
 * The sample battle logs under {@code src/test/resources/battlelog}: the same 6
 * battles in the folders {@code de}, {@code en}, {@code fr} (one per game
 * language) plus {@code partial} (an earlier German export of the running
 * battle of 01.10.2026), and a shared parser for the tests.
 */
final class BattleLogTestFiles {

    static final Path ROOT = Paths.get("src", "test", "resources", "battlelog");

    /** Language folder -> language name as {@code LanguageService} uses it. */
    static final Map<String, String> LANGUAGE_BY_FOLDER = Map.of("de", "deutsch", "en", "english", "fr", "francais");

    /** Battle days of the 6 sample battles, as they start the file names. */
    static final List<String> BATTLE_DAYS =
            List.of("14-09-2026", "17-09-2026", "21-09-2026", "24-09-2026", "28-09-2026", "01-10-2026");

    private static BattleLogParser parser;
    private static final Map<Path, BattleLogParseResult> CACHE = new HashMap<>();

    private BattleLogTestFiles() {
    }

    static synchronized BattleLogParser parser() {
        if (parser == null) {
            parser = BattleLogParser.createDefault();
        }
        return parser;
    }

    /** All CSV files of a folder ({@code de}, {@code en}, {@code fr}, {@code partial}), sorted. */
    static List<Path> files(String folder) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ROOT.resolve(folder), "*.csv")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    /** All 38 sample files (36 in the language folders + 2 partial). */
    static List<Path> allFiles() {
        List<Path> files = new ArrayList<>();
        for (String folder : List.of("de", "en", "fr", "partial")) {
            files.addAll(files(folder));
        }
        return files;
    }

    /** The file of {@code folder} for battle day {@code day} ({@code DD-MM-YYYY}) and direction. */
    static Path file(String folder, String day, LogDirection direction) {
        return files(folder).stream()
                .filter(p -> p.getFileName().toString().startsWith(day))
                .filter(p -> isAttack(p) == (direction == LogDirection.ATTACK))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + direction + " log for " + day + " in " + folder));
    }

    /** Parses {@code file} with the shared parser (cached - records are immutable). */
    static synchronized BattleLogParseResult parse(Path file) {
        return CACHE.computeIfAbsent(file, f -> {
            try {
                return parser().parse(f);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static BattleLogParseResult parse(String folder, String day, LogDirection direction) {
        return parse(file(folder, day, direction));
    }

    private static boolean isAttack(Path file) {
        String name = file.getFileName().toString();
        return name.contains("Angriffs-Log") || name.contains("Attack Log") || name.contains("Journal d'attaque");
    }
}
