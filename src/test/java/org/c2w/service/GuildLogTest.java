package org.c2w.service;

import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GuildLog}: one dated line per event in the guild folder's guild.log (UTF-8),
 * rotation at 1 MB, and writing never throws. The workspace (and with it the
 * technical log) is pointed at a temp folder, in memory only.
 */
class GuildLogTest {

    private static final String LINE_PATTERN = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}  ";

    @TempDir
    Path workspace;

    private Path guildDir;
    private String previousWorkspace;
    private final List<BiConsumer<Path, String>> registered = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        guildDir = Files.createDirectories(workspace.resolve("Alpha"));
    }

    @AfterEach
    void tearDown() {
        registered.forEach(GuildLog::removeListener);
        Config.setWorkspacePath(previousWorkspace);
    }

    private void listen(BiConsumer<Path, String> listener) {
        registered.add(listener);
        GuildLog.addListener(listener);
    }

    @Test
    @DisplayName("an event lands in the guild folder as 'date time  text' in the UI language")
    void writesDatedLineIntoGuildFolder() {
        GuildLog.event(guildDir, "guildLog.guildSaved");

        List<String> lines = GuildLog.read(guildDir);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches(LINE_PATTERN + "\\Q" + LanguageService.displayName("guildLog.guildSaved") + "\\E"),
                lines.get(0));
        assertTrue(Files.isRegularFile(guildDir.resolve(GuildLog.FILE_NAME)));
    }

    @Test
    @DisplayName("arguments are formatted into the text")
    void formatsArguments() {
        GuildLog.event(guildDir, "guildLog.lineupCreated", "Plan A");

        assertTrue(GuildLog.read(guildDir).get(0).contains("Plan A"));
    }

    @Test
    @DisplayName("umlauts, accents and Cyrillic end up byte-exact as UTF-8")
    void writesUtf8() throws IOException {
        String text = "Prüfung é Союз";

        GuildLog.write(guildDir, text);

        byte[] bytes = Files.readAllBytes(guildDir.resolve(GuildLog.FILE_NAME));
        String expectedEnd = text + System.lineSeparator();
        assertTrue(new String(bytes, StandardCharsets.UTF_8).endsWith(expectedEnd));
        assertEquals(List.of(text), GuildLog.read(guildDir).stream().map(l -> l.substring(21)).toList());
    }

    @Test
    @DisplayName("read on a guild without guild.log is empty and creates no file")
    void readWithoutFileIsEmpty() {
        assertEquals(List.of(), GuildLog.read(guildDir));
        assertEquals(List.of(), GuildLog.read(null));
        assertFalse(Files.exists(guildDir.resolve(GuildLog.FILE_NAME)));
    }

    @Test
    @DisplayName("above 1 MB the log is rotated into guild.log.1 - never more than two files")
    void rotatesAtOneMegabyte() throws IOException {
        Path file = guildDir.resolve(GuildLog.FILE_NAME);
        Files.writeString(file, "x".repeat((int) GuildLog.MAX_FILE_SIZE_BYTES - 5), StandardCharsets.UTF_8);

        GuildLog.write(guildDir, "first after rotation");
        assertTrue(Files.isRegularFile(guildDir.resolve(GuildLog.FILE_NAME + ".1")));
        assertEquals(1, GuildLog.read(guildDir).size());

        Files.writeString(file, "y".repeat((int) GuildLog.MAX_FILE_SIZE_BYTES - 5), StandardCharsets.UTF_8);
        GuildLog.write(guildDir, "second rotation");
        try (var files = Files.list(guildDir)) {
            assertEquals(2, files.filter(p -> p.getFileName().toString().startsWith(GuildLog.FILE_NAME)).count());
        }
        assertTrue(Files.readString(guildDir.resolve(GuildLog.FILE_NAME + ".1")).startsWith("y"));
    }

    @Test
    @DisplayName("a missing guild folder is neither created nor fatal - noted in the technical log")
    void missingFolderDoesNotThrow() {
        Path missing = workspace.resolve("Gone");
        LogCapture technical = LogCapture.start();

        assertDoesNotThrow(() -> GuildLog.event(missing, "guildLog.guildSaved"));
        assertDoesNotThrow(() -> GuildLog.event(null, "guildLog.guildSaved"));

        assertFalse(Files.exists(missing));
        assertTrue(technical.stream().anyMatch(l -> l.contains("Gone")), technical.toString());
    }

    @Test
    @DisplayName("a guild.log that cannot be written is not fatal - noted in the technical log, no listener call")
    void unwritableFileDoesNotThrow() throws IOException {
        Files.createDirectories(guildDir.resolve(GuildLog.FILE_NAME)); // a folder where the file should be
        List<String> heard = new ArrayList<>();
        listen((dir, line) -> heard.add(line));
        LogCapture technical = LogCapture.start();

        assertDoesNotThrow(() -> GuildLog.event(guildDir, "guildLog.guildSaved"));

        assertTrue(heard.isEmpty());
        assertTrue(technical.stream().anyMatch(l -> l.contains("Could not write")), technical.toString());
    }

    @Test
    @DisplayName("listeners get the guild folder together with the written line")
    void listenerGetsGuildFolder() {
        List<Path> dirs = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        listen((dir, line) -> {
            dirs.add(dir);
            lines.add(line);
        });

        GuildLog.event(guildDir, "guildLog.guildOpened");

        assertEquals(List.of(guildDir), dirs);
        assertEquals(GuildLog.read(guildDir), lines);
    }
}
