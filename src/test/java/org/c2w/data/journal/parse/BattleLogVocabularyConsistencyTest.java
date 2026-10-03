package org.c2w.data.journal.parse;

import org.c2w.data.model.BuffEffect;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build-time check of the battle log vocabularies, analogous to
 * {@code LanguageFilesConsistencyTest}: every language folder under
 * {@code src/main/resources/language} has a {@code battleLogVocabulary.json},
 * all of them have the same keys, and the mandatory entries are filled
 * everywhere. Texts no log has shown yet stay empty in every language.
 */
class BattleLogVocabularyConsistencyTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");

    @Test
    void everyLanguageFolderHasAVocabulary() throws IOException {
        Map<String, BattleLogVocabulary> vocabularies = vocabulariesFromSource();

        assertEquals(Set.of("deutsch", "english", "francais"), vocabularies.keySet());
    }

    @Test
    void allVocabulariesHaveTheSameKeys() throws IOException {
        Map<String, BattleLogVocabulary> vocabularies = vocabulariesFromSource();
        Set<String> allKeys = new TreeSet<>();
        vocabularies.values().forEach(v -> allKeys.addAll(v.entries().keySet()));

        Map<String, Set<String>> missing = new TreeMap<>();
        for (BattleLogVocabulary v : vocabularies.values()) {
            Set<String> keys = new TreeSet<>(allKeys);
            keys.removeAll(v.entries().keySet());
            if (!keys.isEmpty()) {
                missing.put(v.language(), keys);
            }
        }
        assertTrue(missing.isEmpty(), "keys missing per language: " + missing);
    }

    @Test
    void containsExactlyTheKnownKeys() throws IOException {
        Set<String> expected = new TreeSet<>(BattleLogVocabulary.requiredKeys());
        expected.add(BattleLogVocabulary.BUFF_PREFIX + BuffEffect.SKILL_COOLDOWN_DECREASE.name());

        for (BattleLogVocabulary v : vocabulariesFromSource().values()) {
            assertEquals(expected, v.entries().keySet(), v.language());
        }
    }

    @Test
    void mandatoryEntriesAreFilledEverywhere() throws IOException {
        for (BattleLogVocabulary v : vocabulariesFromSource().values()) {
            for (String key : BattleLogVocabulary.requiredKeys()) {
                List<String> texts = v.texts(key);
                assertFalse(texts.isEmpty(), v.language() + ": " + key + " is empty");
                assertTrue(texts.stream().noneMatch(String::isBlank), v.language() + ": blank text in " + key);
            }
        }
    }

    @Test
    void undefendedTextsHaveBothPlaceholders() throws IOException {
        for (BattleLogVocabulary v : vocabulariesFromSource().values()) {
            for (String key : List.of(BattleLogVocabulary.UNDEFENDED_SINGULAR, BattleLogVocabulary.UNDEFENDED_PLURAL)) {
                for (String text : v.texts(key)) {
                    assertTrue(text.contains(BattleLogVocabulary.PLACEHOLDER_FREE)
                            && text.contains(BattleLogVocabulary.PLACEHOLDER_TOTAL), v.language() + ": " + text);
                }
            }
        }
    }

    @Test
    void unseenTextsAreNotInvented() throws IOException {
        for (BattleLogVocabulary v : vocabulariesFromSource().values()) {
            for (BuffEffect effect : BattleLogVocabulary.UNSEEN_BUFFS) {
                assertEquals(List.of(), v.texts(BattleLogVocabulary.BUFF_PREFIX + effect.name()), v.language());
            }
        }
    }

    @Test
    void loadAllReadsEveryAvailableLanguageFromTheClasspath() {
        List<String> languages = BattleLogVocabulary.loadAll().stream().map(BattleLogVocabulary::language).toList();

        assertEquals(LanguageService.availableLanguages(), languages);
    }

    @Test
    void undefendedTextsMatchWithTheirNumbers() {
        BattleLogVocabulary fr = BattleLogVocabulary.load("francais").orElseThrow();

        BattleLogVocabulary.UndefendedPositions positions = fr.matchUndefended(
                "2 positions de cette fortification sur 3 sont restées sans défense et ont été capturées sans combat.");

        assertEquals(new BattleLogVocabulary.UndefendedPositions(2, 3), positions);
        assertNull(fr.matchUndefended("2 positions sur 3"));
    }

    @Test
    void parseFlattensNestedObjects() {
        BattleLogVocabulary v = BattleLogVocabulary.parse("test",
                "{\"a\": {\"b\": [\"x\", \"y\"], \"c\": []}, \"d\": [\"z\"]}");

        assertEquals(Map.of("a.b", List.of("x", "y"), "a.c", List.of(), "d", List.of("z")), v.entries());
    }

    private static Map<String, BattleLogVocabulary> vocabulariesFromSource() throws IOException {
        Map<String, BattleLogVocabulary> result = new TreeMap<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(LANGUAGE_DIR, Files::isDirectory)) {
            for (Path dir : dirs) {
                String language = dir.getFileName().toString();
                Path file = dir.resolve(BattleLogVocabulary.FILE_NAME);
                assertTrue(Files.isRegularFile(file), "missing " + file);
                result.put(language, BattleLogVocabulary.parse(language, Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return result;
    }
}
