package org.c2w.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Build-time consistency check for the language files under
 * {@code src/main/resources/language} (see cow2win-verbesserungsvorschlaege.md, section
 * "UX / GUI": "Konsistenzprüfung der drei Sprachdateien"). {@link LanguageService} looks
 * every UI string up by key at runtime and just falls back to the raw id if a key is
 * missing (see its javadoc) - a silent, easy-to-miss gap rather than a crash. This test
 * makes that gap loud at build time (`mvn test`) instead: it fails, listing exactly which
 * key is missing from which language, whenever the language files' key sets diverge -
 * whether a key was added to only one language or a whole language lags behind after a
 * patch (new heroes, titans or fortifications - see PATCH-CHECKLIST.md's "all three
 * language files" step).
 *
 * <p>Since the 2026-09-16 restructuring (see {@link LanguageService}), each language lives
 * in its own subdirectory of {@code language/} as {@code <name>/<name>.properties} (e.g.
 * {@code language/deutsch/deutsch.properties}) rather than a flat {@code language/deutsch.txt}
 * - {@link #collectKeysPerFile} follows that same layout, keyed by language (directory)
 * name rather than by file name.
 *
 * <p>Deliberately does not pick one language as "the" reference: a key present in any one
 * language but missing from another is reported as missing from that other language,
 * whichever direction the divergence went (new key never propagated, or a stray key only
 * removed from some languages) - see {@link #detectsAnExtraKeyInOneFileAsMissingInTheOthers()}
 * below.
 */
class LanguageFilesConsistencyTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");

    @Test
    @DisplayName("deutsch, english and francais define exactly the same set of keys")
    void allLanguageFilesHaveTheSameKeys() throws IOException {
        Map<String, Set<String>> keysByLanguage = collectKeysPerFile(LANGUAGE_DIR);

        assertTrue(keysByLanguage.size() >= 2, "expected at least two languages under " + LANGUAGE_DIR
                + ", found: " + keysByLanguage.keySet());

        Map<String, Set<String>> missingByLanguage = findMissingKeys(keysByLanguage);

        assertTrue(missingByLanguage.isEmpty(), "language files have diverged - " + describeMismatches(missingByLanguage));
    }

    @Test
    @DisplayName("A key present in only one (synthetic) language is reported as missing from the others")
    void detectsAnExtraKeyInOneFileAsMissingInTheOthers() throws IOException {
        Path tempDir = Files.createTempDirectory("language-consistency-test");
        try {
            writeLanguage(tempDir, "english", "toolbar.guild=Guild:\ncommon.none=(none)\nnew.key=New");
            writeLanguage(tempDir, "deutsch", "toolbar.guild=Gilde:\ncommon.none=(keine)");
            writeLanguage(tempDir, "francais", "toolbar.guild=Guilde :\ncommon.none=(aucun)");

            Map<String, Set<String>> keysByLanguage = collectKeysPerFile(tempDir);
            Map<String, Set<String>> missingByLanguage = findMissingKeys(keysByLanguage);

            assertEquals(Set.of("deutsch", "francais"), missingByLanguage.keySet(),
                    "only the two languages that don't have 'new.key' should be reported");
            assertEquals(Set.of("new.key"), missingByLanguage.get("deutsch"));
            assertEquals(Set.of("new.key"), missingByLanguage.get("francais"));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    @DisplayName("Identical key sets across (synthetic) languages produce no mismatches")
    void identicalKeySetsProduceNoMismatches() throws IOException {
        Path tempDir = Files.createTempDirectory("language-consistency-test");
        try {
            writeLanguage(tempDir, "english", "a=1\nb=2");
            writeLanguage(tempDir, "deutsch", "a=eins\nb=zwei");

            Map<String, Set<String>> keysByLanguage = collectKeysPerFile(tempDir);
            assertTrue(findMissingKeys(keysByLanguage).isEmpty());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // --- the actual check, package-visible so both the real-data test above and the
    //     synthetic tests exercise the exact same logic ---

    /**
     * Loads every {@code <name>/<name>.properties} file directly under {@code languageDir}
     * (UTF-8, same as {@link LanguageService}) and returns each language's key set, keyed
     * by its directory name. A subdirectory without a matching {@code <name>.properties}
     * file inside it is skipped (nothing to check its keys against).
     */
    static Map<String, Set<String>> collectKeysPerFile(Path languageDir) throws IOException {
        Map<String, Set<String>> result = new TreeMap<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(languageDir, Files::isDirectory)) {
            for (Path dir : dirs) {
                String name = dir.getFileName().toString();
                Path propertiesFile = dir.resolve(name + ".properties");
                if (!Files.isRegularFile(propertiesFile)) {
                    continue;
                }
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                result.put(name, properties.stringPropertyNames());
            }
        }
        return result;
    }

    /**
     * For every key that appears in at least one language, checks it exists in all of them.
     * Returns a map from language name to the set of keys that language is missing - empty
     * if every language has exactly the same keys. Uses the UNION of all languages' keys as
     * the reference, not any single language, so a key added to only one language shows up
     * as "missing" from every other language rather than needing a designated master file.
     */
    static Map<String, Set<String>> findMissingKeys(Map<String, Set<String>> keysByLanguage) {
        Set<String> allKeys = new TreeSet<>();
        keysByLanguage.values().forEach(allKeys::addAll);

        Map<String, Set<String>> missingByLanguage = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : keysByLanguage.entrySet()) {
            Set<String> missing = new TreeSet<>(allKeys);
            missing.removeAll(entry.getValue());
            if (!missing.isEmpty()) {
                missingByLanguage.put(entry.getKey(), missing);
            }
        }
        return missingByLanguage;
    }

    private static String describeMismatches(Map<String, Set<String>> missingByLanguage) {
        return missingByLanguage.entrySet().stream()
                .map(e -> e.getKey() + " is missing: " + e.getValue())
                .collect(Collectors.joining("; "));
    }

    /** Writes {@code content} as {@code <languagesDir>/<name>/<name>.properties}, creating the subdirectory as needed. */
    private static void writeLanguage(Path languagesDir, String name, String content) throws IOException {
        Path dir = languagesDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".properties"), content, StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // Best-effort cleanup of a temp dir - not worth failing the test over.
                }
            });
        }
    }
}
