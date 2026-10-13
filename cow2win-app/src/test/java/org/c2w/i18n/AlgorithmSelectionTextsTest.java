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
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** The texts of the concept stage's "algorithm" combo box and of "Run assignment" for one fortification type. */
class AlgorithmSelectionTextsTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");

    private static Map<String, Properties> languages() throws IOException {
        Map<String, Properties> result = new TreeMap<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(LANGUAGE_DIR, Files::isDirectory)) {
            for (Path dir : dirs) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(dir.resolve(dir.getFileName() + ".properties"),
                        StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                result.put(dir.getFileName().toString(), properties);
            }
        }
        return result;
    }

    @Test
    @DisplayName("Heading, confirmation and guild log entry exist in every language and format with their arguments")
    void textsFormat() throws IOException {
        for (Map.Entry<String, Properties> language : languages().entrySet()) {
            Properties texts = language.getValue();
            String name = language.getKey();
            assertNotNull(texts.getProperty("conceptStage.algorithm"), name);

            String confirm = MessageFormat.format(texts.getProperty("toolbar.runAlgorithm.confirmMode"), "Algo", 3L, "Typ");
            assertTrue(confirm.contains("Algo") && confirm.contains("3") && confirm.contains("Typ"), name + ": " + confirm);
            assertFalse(confirm.contains("{") || confirm.contains("''"), name + ": " + confirm);

            for (int removed : List.of(0, 1, 5)) {
                String log = MessageFormat.format(texts.getProperty("guildLog.algorithmRun"), "Typ", "Algo", removed, 2);
                assertTrue(log.contains("Typ") && log.contains("Algo"), name + ": " + log);
                assertFalse(log.contains("{"), name + ": " + log);
            }
        }
    }

    @Test
    @DisplayName("\"Best possible lineup\" no longer claims to keep existing assignments")
    void bestPossibleDescriptionWithoutKeepSentence() throws IOException {
        for (Map.Entry<String, Properties> language : languages().entrySet()) {
            String description = language.getValue().getProperty("algorithm.bestPossibleLineup.description");
            for (String sentence : List.of("Eine bereits vorhandene Zuweisung wird nie verändert",
                    "Never touches an assignment that is already there", "Ne modifie jamais une attribution déjà existante")) {
                assertFalse(description.contains(sentence), language.getKey());
            }
        }
    }
}
