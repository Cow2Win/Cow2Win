package org.c2w.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every hero, titan, pet, totem and fortification name in the sample battle
 * logs under {@code src/test/resources/battlelog/{de,en,fr,partial}} (Schlachtplan 0.4) is a
 * display name in the language file of that log's language - Schlachtplan
 * 0.5: the language files carry the in-game names and are the only basis for
 * mapping log names back to catalog ids.
 *
 * <p>Deliberately simple CSV handling (no quoting in these exports), not the
 * real parser of the journal. Names are compared via {@link GameNameNormalizer},
 * like the parser does. A new sample log
 * after a patch that fails here means: add or correct the game name in the
 * language file (see PATCH-CHECKLIST.md).
 */
class BattleLogGameNamesTest {

    private static final Path BATTLE_LOG_DIR = Paths.get("src", "test", "resources", "battlelog");
    /** One folder per game language (the same battles in each) plus an earlier partial export. */
    private static final List<String> BATTLE_LOG_FOLDERS = List.of("de", "en", "fr", "partial");
    private static final int SAMPLE_LOG_COUNT = 38;
    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");
    private static final Path DATA_DIR = Paths.get("src", "main", "resources", "data");
    private static final List<String> CATALOGS =
            List.of("heroes.json", "titans.json", "pets.json", "fortifications.json");

    /** Log language, recognized by the result/ranking part of the exported file name. */
    private static final Map<String, String> LANGUAGE_BY_FILE_NAME_MARKER = Map.of(
            "Rangpunkte", "deutsch",
            "ranking pts", "english",
            "pts de classement", "francais");

    /** "Bridge (Position: 1)" (DE/EN) or "Pont (Position : 1)" (FR) - the position is not part of the name. */
    private static final Pattern POSITION_SUFFIX = Pattern.compile("\\s*\\(Position\\s*:\\s*\\d+\\)$");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void sampleLogsCoverAllThreeLanguages() throws IOException {
        Set<String> languages = new TreeSet<>();
        for (Path log : battleLogs()) {
            languages.add(languageOf(log));
        }
        assertEquals(new TreeSet<>(LANGUAGE_BY_FILE_NAME_MARKER.values()), languages);
    }

    @Test
    void everyNameInTheSampleLogsIsKnownInItsLanguage() throws IOException {
        Set<String> catalogIds = catalogIds();

        Map<String, Set<String>> unknownByLog = new TreeMap<>();
        for (Path log : battleLogs()) {
            Set<String> known = knownNames(languageOf(log), catalogIds);
            Set<String> unknown = new TreeSet<>();
            for (String name : namesIn(log)) {
                if (!known.contains(GameNameNormalizer.key(name))) {
                    unknown.add(name);
                }
            }
            if (!unknown.isEmpty()) {
                unknownByLog.put(log.getFileName().toString(), unknown);
            }
        }

        assertTrue(unknownByLog.isEmpty(), "names in battle logs without a matching game name: " + unknownByLog);
    }

    @Test
    void extractsUnitPetAndFortificationNames() throws IOException {
        Path log = battleLogs().stream()
                .filter(p -> p.getFileName().toString().startsWith("24-09-2026") && p.toString().contains("Angriffs"))
                .findFirst().orElseThrow();

        Set<String> names = namesIn(log);

        assertTrue(names.containsAll(Set.of("Tenebris", "Dunkelgeisttotem", "Lara Croft", "Kaskade",
                "Axell", "Biskuit", "Heldenbrücke", "Brücke", "Rathaus")), names.toString());
        assertFalse(names.contains("Befestigung"), "header row must not count as a fortification");
    }

    // --- helpers ---

    private static List<Path> battleLogs() throws IOException {
        List<Path> logs = new ArrayList<>();
        for (String folder : BATTLE_LOG_FOLDERS) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(BATTLE_LOG_DIR.resolve(folder), "*.csv")) {
                stream.forEach(logs::add);
            }
        }
        logs.sort(Comparator.naturalOrder());
        assertEquals(SAMPLE_LOG_COUNT, logs.size(), "expected the " + SAMPLE_LOG_COUNT + " sample battle logs in "
                + BATTLE_LOG_DIR + "/" + BATTLE_LOG_FOLDERS);
        return logs;
    }

    private static String languageOf(Path log) {
        String fileName = log.getFileName().toString();
        return LANGUAGE_BY_FILE_NAME_MARKER.entrySet().stream()
                .filter(e -> fileName.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("unknown log language: " + fileName));
    }

    /**
     * Fortification names (first column of the non-indented rows, minus the
     * header row) and unit names (every cell "Name | ..." - heroes, titans,
     * totems, the pet row and the "Patronat" pet column alike).
     */
    static Set<String> namesIn(Path log) throws IOException {
        Set<String> names = new TreeSet<>();
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            if (!line.startsWith(",")) {
                names.add(GameNameNormalizer.clean(POSITION_SUFFIX.matcher(cells[0]).replaceFirst("")));
            }
            for (String cell : cells) {
                int bar = cell.indexOf(" | ");
                if (bar > 0) {
                    names.add(GameNameNormalizer.clean(cell.substring(0, bar)));
                }
            }
        }
        return names;
    }

    private static Set<String> catalogIds() throws IOException {
        Set<String> ids = new HashSet<>();
        for (String catalog : CATALOGS) {
            Matcher m = ID.matcher(Files.readString(DATA_DIR.resolve(catalog), StandardCharsets.UTF_8));
            while (m.find()) {
                ids.add(m.group(1));
            }
        }
        return ids;
    }

    /** Comparison keys of the display names of all catalog ids plus the totem game names in one language. */
    private static Set<String> knownNames(String language, Set<String> catalogIds) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(
                LANGUAGE_DIR.resolve(language).resolve(language + ".properties"), StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        Set<String> names = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            if (catalogIds.contains(key) || key.startsWith("titanTotem.")) {
                names.add(GameNameNormalizer.key(props.getProperty(key)));
            }
        }
        return names;
    }
}
