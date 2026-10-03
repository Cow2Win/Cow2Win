package org.c2w.data.journal.db;

import org.c2w.data.journal.BattleLogParseResult;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The most important journal database test: every sample log, saved and loaded
 * again, equals the parser result - same records in the same order, raw texts
 * (Cyrillic guild names, "Vale " with its trailing space, non-breaking spaces in
 * colors), problems - and the original bytes come back unchanged.
 */
class JournalRoundTripTest {

    @TempDir
    Path tempDir;

    /**
     * One database per folder: de, en and fr each hold the 6 battles with both
     * directions; partial gets its own (it is the same battle as de 01.10.).
     */
    @Test
    void everySampleLogComesBackExactlyAsParsed() throws Exception {
        int checked = 0;
        for (String folder : List.of("de", "en", "fr", "partial")) {
            Path guildDir = Files.createDirectories(tempDir.resolve(folder));
            try (JournalDatabase db = JournalDatabase.open(guildDir, true).orElseThrow()) {
                JournalRepository repository = new JournalRepository(db);
                List<Path> files = BattleLogTestFiles.files(folder);
                List<Integer> battleIds = new ArrayList<>();
                for (Path file : files) {
                    SaveResult result = repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null);
                    assertEquals(SaveResult.Outcome.CREATED, result.outcome(), file.toString());
                    battleIds.add(result.battleId());
                }
                for (int i = 0; i < files.size(); i++) {
                    Path file = files.get(i);
                    BattleLogParseResult parsed = BattleLogTestFiles.parse(file);
                    LogDirection direction = parsed.log().header().direction();

                    BattleLogParseResult loaded = repository.loadLog(battleIds.get(i), direction).orElseThrow();

                    assertEquals(parsed, loaded, folder + "/" + file.getFileName());
                    assertArrayEquals(Files.readAllBytes(file),
                            repository.loadRawCsv(battleIds.get(i), direction).orElseThrow(), file.toString());
                    checked++;
                }
            }
            try (Stream<Path> dbFiles = Files.list(guildDir)) {
                System.out.printf(Locale.ROOT, "JOURNAL-SIZE %s: %d logs, database %d KB%n", folder,
                        BattleLogTestFiles.files(folder).size(), dbFiles.mapToLong(p -> p.toFile().length()).sum() / 1024);
            }
        }
        assertEquals(38, checked);
    }

    @Test
    void unicodeAndSpacesSurviveExactly() throws Exception {
        try (JournalDatabase db = JournalDatabase.open(tempDir, true).orElseThrow()) {
            JournalRepository repository = new JournalRepository(db);
            Path file = BattleLogTestFiles.file("de", "01-10-2026", LogDirection.DEFENSE);
            int battleId = repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null).battleId();

            BattleLogParseResult loaded = repository.loadLog(battleId, LogDirection.DEFENSE).orElseThrow();

            assertEquals("Союз", loaded.log().header().opponent().name());
            assertTrue(loaded.log().fights().stream().anyMatch(f -> f.defender().playerName().equals("Vale ")));
            assertTrue(repository.listPlayers(false).stream().anyMatch(p -> p.name().equals("Дядюшка Ау")));
        }
    }

    /**
     * All 38 files into ONE database (overlapping exports replace each other) - prints
     * duration (including the compaction on close) and size. Thanks to {@code SHUTDOWN COMPACT}
     * the file is about as small as a fresh database with the 12 German logs only.
     */
    @Test
    void allSampleFilesIntoOneDatabase() throws Exception {
        List<Path> files = BattleLogTestFiles.allFiles();
        files.forEach(BattleLogTestFiles::parse); // parse first: only the database work is timed
        Path guildDir = Files.createDirectories(tempDir.resolve("all"));
        long bytes = 0;
        int replaced = 0;
        long start = System.nanoTime();
        try (JournalDatabase db = JournalDatabase.open(guildDir, true).orElseThrow()) {
            JournalRepository repository = new JournalRepository(db);
            for (Path file : files) {
                byte[] raw = Files.readAllBytes(file);
                bytes += raw.length;
                if (repository.saveLog(BattleLogTestFiles.parse(file), raw, null).outcome()
                        == SaveResult.Outcome.REPLACED) {
                    replaced++;
                }
            }
            assertEquals(6, repository.listBattles(null).size());
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        long size = size(guildDir);

        Path referenceDir = Files.createDirectories(tempDir.resolve("reference"));
        try (JournalDatabase db = JournalDatabase.open(referenceDir, true).orElseThrow()) {
            JournalRepository repository = new JournalRepository(db);
            for (Path file : BattleLogTestFiles.files("de")) {
                repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null);
            }
        }
        long reference = size(referenceDir);

        System.out.printf(Locale.ROOT, "JOURNAL-VOLUME: %d files (%d KB CSV), %d replaced, %d ms, database %d KB"
                + " (fresh database with the 12 German logs: %d KB)%n",
                files.size(), bytes / 1024, replaced, millis, size / 1024, reference / 1024);
        assertEquals(26, replaced, "de first, then en and fr replace all 12 logs each, partial replaces 2");
        assertTrue(size < reference * 5 / 4, "compacted on close: " + size + " vs. " + reference + " bytes");
    }

    private static long size(Path dir) throws java.io.IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.mapToLong(p -> p.toFile().length()).sum();
        }
    }
}
