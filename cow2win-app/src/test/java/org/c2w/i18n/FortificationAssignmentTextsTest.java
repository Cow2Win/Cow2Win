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

/** The texts of the fortification assignment (info panel, dialog) and of "Run assignment" with "Only add". */
class FortificationAssignmentTextsTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");

    /** Keys without arguments - shown as they are, so no doubled apostrophes. */
    private static final List<String> PLAIN_KEYS = List.of(
            "stageInfo.fortification.noTeams", "stageInfo.fortification.doubleClickHint",
            "fortificationAssignment.free", "fortificationAssignment.assign", "fortificationAssignment.move",
            "fortificationAssignment.remove", "fortificationAssignment.chooseTeam",
            "fortificationAssignment.assignedElsewhere", "fortificationAssignment.noCandidates",
            "fortificationAssignment.chooseTarget", "fortificationAssignment.apply",
            "toolbar.runAlgorithm.replace", "toolbar.runAlgorithm.complete", "common.cancel", "common.close");

    /** Keys with one argument {0}. */
    private static final List<String> ONE_ARGUMENT_KEYS = List.of(
            "fortificationAssignment.title", "fortificationAssignment.slot",
            "fortificationAssignment.currentlyIn", "fortificationAssignment.swapWith");

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
    @DisplayName("Every new text exists in every language; texts with arguments format, plain ones have no doubled apostrophes")
    void textsExistAndFormat() throws IOException {
        for (Map.Entry<String, Properties> language : languages().entrySet()) {
            String name = language.getKey();
            Properties texts = language.getValue();
            for (String key : PLAIN_KEYS) {
                String text = texts.getProperty(key);
                assertNotNull(text, name + ": " + key);
                assertFalse(text.isBlank() || text.contains("''"), name + ": " + key + " = " + text);
            }
            for (String key : ONE_ARGUMENT_KEYS) {
                String formatted = MessageFormat.format(texts.getProperty(key), "Mine");
                assertTrue(formatted.contains("Mine"), name + ": " + formatted);
                assertFalse(formatted.contains("{") || formatted.contains("''"), name + ": " + formatted);
            }
            String confirm = MessageFormat.format(texts.getProperty("toolbar.runAlgorithm.confirmMode"), "Algo", 3L, "Typ");
            assertTrue(confirm.contains("Algo") && confirm.contains("3") && confirm.contains("Typ"), name + ": " + confirm);
            assertTrue(confirm.contains(texts.getProperty("toolbar.runAlgorithm.replace"))
                    && confirm.contains(texts.getProperty("toolbar.runAlgorithm.complete")), name + ": " + confirm);
            assertFalse(confirm.contains("{") || confirm.contains("''"), name + ": " + confirm);
        }
    }

    @Test
    @DisplayName("The old confirmation and the texts of the removed fortification entry dialog are gone")
    void oldTextsRemoved() throws IOException {
        for (Map.Entry<String, Properties> language : languages().entrySet()) {
            Properties texts = language.getValue();
            assertNull(texts.getProperty("toolbar.runAlgorithm.confirmClear"), language.getKey());
            assertTrue(texts.stringPropertyNames().stream().noneMatch(key -> key.startsWith("fortificationEntry.")),
                    language.getKey());
        }
    }
}
