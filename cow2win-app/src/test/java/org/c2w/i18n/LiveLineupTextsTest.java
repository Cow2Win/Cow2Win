package org.c2w.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step 5.0: the UI calls the Original lineup "Live" - no language file value names it "Original" any more. */
class LiveLineupTextsTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");

    /**
     * Keys that may still say "original": the original CSV file of a battle log (not the lineup),
     * and the reserved-name message, which names both "Live" and "Original" on purpose.
     */
    private static final Set<String> EXCEPTIONS = Set.of(
            "journal.action.saveCsv", "journal.saveCsv.title", "mainFrame.newLineup.reservedName");

    @Test
    @DisplayName("No language file value calls the lineup \"Original\"")
    void noOriginalLineupTexts() throws IOException {
        List<String> found = new ArrayList<>();
        try (DirectoryStream<Path> languages = Files.newDirectoryStream(LANGUAGE_DIR, Files::isDirectory)) {
            for (Path language : languages) {
                Path file = language.resolve(language.getFileName() + ".properties");
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                for (String key : properties.stringPropertyNames()) {
                    if (!EXCEPTIONS.contains(key) && properties.getProperty(key).toLowerCase().contains("original")) {
                        found.add(language.getFileName() + ": " + key + "=" + properties.getProperty(key));
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "still \"Original\": " + found);
    }
}
