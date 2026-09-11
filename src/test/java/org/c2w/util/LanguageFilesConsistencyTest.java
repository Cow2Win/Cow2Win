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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * key is missing from which file, whenever the language files' key sets diverge - whether
 * a key was added to only one file or a whole file lags behind after a patch (new heroes,
 * titans or fortifications - see PATCH-CHECKLIST.md's "all three language files" step).
 *
 * <p>Deliberately does not pick one file as "the" reference: a key present in any one file
 * but missing from another is reported as missing from that other file, whichever direction
 * the divergence went (new key never propagated, or a stray key only removed from some
 * files) - see {@link #detectsAnExtraKeyInOneFileAsMissingInTheOthers()} below.
 */
class LanguageFilesConsistencyTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");

    @Test
    @DisplayName("deutsch.txt, english.txt and francais.txt define exactly the same set of keys")
    void allLanguageFilesHaveTheSameKeys() throws IOException {
        Map<String, Set<String>> keysByFile = collectKeysPerFile(LANGUAGE_DIR);

        assertTrue(keysByFile.size() >= 2, "expected at least two language files under " + LANGUAGE_DIR
                + ", found: " + keysByFile.keySet());

        Map<String, Set<String>> missingByFile = findMissingKeys(keysByFile);

        assertTrue(missingByFile.isEmpty(), "language files have diverged - " + describeMismatches(missingByFile));
    }

    @Test
    @DisplayName("A key present in only one (synthetic) language file is reported as missing from the others")
    void detectsAnExtraKeyInOneFileAsMissingInTheOthers() throws IOException {
        Path tempDir = Files.createTempDirectory("language-consistency-test");
        try {
            writeProperties(tempDir.resolve("english.txt"), "toolbar.guild=Guild:\ncommon.none=(none)\nnew.key=New");
            writeProperties(tempDir.resolve("deutsch.txt"), "toolbar.guild=Gilde:\ncommon.none=(keine)");
            writeProperties(tempDir.resolve("francais.txt"), "toolbar.guild=Guilde :\ncommon.none=(aucun)");

            Map<String, Set<String>> keysByFile = collectKeysPerFile(tempDir);
            Map<String, Set<String>> missingByFile = findMissingKeys(keysByFile);

            assertEquals(Set.of("deutsch.txt", "francais.txt"), missingByFile.keySet(),
                    "only the two files that don't have 'new.key' should be reported");
            assertEquals(Set.of("new.key"), missingByFile.get("deutsch.txt"));
            assertEquals(Set.of("new.key"), missingByFile.get("francais.txt"));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    @DisplayName("Identical key sets across (synthetic) files produce no mismatches")
    void identicalKeySetsProduceNoMismatches() throws IOException {
        Path tempDir = Files.createTempDirectory("language-consistency-test");
        try {
            writeProperties(tempDir.resolve("english.txt"), "a=1\nb=2");
            writeProperties(tempDir.resolve("deutsch.txt"), "a=eins\nb=zwei");

            Map<String, Set<String>> keysByFile = collectKeysPerFile(tempDir);
            assertTrue(findMissingKeys(keysByFile).isEmpty());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // --- the actual check, package-visible so both the real-data test above and the
    //     synthetic tests exercise the exact same logic ---

    /** Loads every {@code *.txt} file directly under {@code languageDir} as a {@link Properties} file (UTF-8, same as {@link LanguageService}) and returns each file's key set, keyed by file name. */
    static Map<String, Set<String>> collectKeysPerFile(Path languageDir) throws IOException {
        Map<String, Set<String>> result = new TreeMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(languageDir, "*.txt")) {
            for (Path file : files) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                result.put(file.getFileName().toString(), properties.stringPropertyNames());
            }
        }
        return result;
    }

    /**
     * For every key that appears in at least one file, checks it exists in all of them.
     * Returns a map from file name to the set of keys that file is missing - empty if
     * every file has exactly the same keys. Uses the UNION of all files' keys as the
     * reference, not any single file, so a key added to only one file shows up as
     * "missing" from every other file rather than needing a designated master file.
     */
    static Map<String, Set<String>> findMissingKeys(Map<String, Set<String>> keysByFile) {
        Set<String> allKeys = new TreeSet<>();
        keysByFile.values().forEach(allKeys::addAll);

        Map<String, Set<String>> missingByFile = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : keysByFile.entrySet()) {
            Set<String> missing = new TreeSet<>(allKeys);
            missing.removeAll(entry.getValue());
            if (!missing.isEmpty()) {
                missingByFile.put(entry.getKey(), missing);
            }
        }
        return missingByFile;
    }

    private static String describeMismatches(Map<String, Set<String>> missingByFile) {
        return missingByFile.entrySet().stream()
                .map(e -> e.getKey() + " is missing: " + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private static void writeProperties(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
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
