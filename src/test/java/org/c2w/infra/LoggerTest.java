package org.c2w.infra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Logger}: the technical log file in the workspace - every entry with date
 * and time, rotated at 1 MB into cow2win.log.1. The workspace is pointed at a temp
 * folder, in memory only.
 */
class LoggerTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;

    @BeforeEach
    void redirectWorkspace() {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
    }

    @AfterEach
    void restoreWorkspace() {
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("an entry carries date and time")
    void entryHasDateAndTime() {
        assertEquals("[2026-10-05 21:54:12] hello", Logger.formatEntry(LocalDateTime.of(2026, 10, 5, 21, 54, 12), "hello"));
    }

    @Test
    @DisplayName("log appends a dated line to cow2win.log in the workspace")
    void logWritesDatedLine() throws IOException {
        Logger.log("dated entry");

        List<String> lines = Files.readAllLines(workspace.resolve(Logger.LOG_FILE_NAME), StandardCharsets.UTF_8);
        assertTrue(lines.get(lines.size() - 1).matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}] dated entry"),
                lines.toString());
    }

    @Test
    @DisplayName("above 1 MB the log is rotated into cow2win.log.1")
    void rotatesAtOneMegabyte() throws IOException {
        Path file = workspace.resolve(Logger.LOG_FILE_NAME);
        Files.writeString(file, "x".repeat(1024 * 1024 - 5), StandardCharsets.UTF_8);

        Logger.log("after rotation");

        assertTrue(Files.readString(workspace.resolve(Logger.LOG_FILE_NAME + ".1")).startsWith("x"));
        assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size());
    }
}
