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

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Logger} writes its log file as UTF-8, independent of the platform
 * default charset - player names in battle logs may contain e.g. Cyrillic.
 * The workspace (and with it the log file) is pointed at a temp folder, in
 * memory only - config.properties is never written.
 */
class LoggerCharsetTest {

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
    @DisplayName("umlauts and Cyrillic end up byte-exact as UTF-8 in the log file")
    void writesUtf8() throws IOException {
        String message = "Prüfung Союз";

        Logger.log(message);

        byte[] fileBytes = Files.readAllBytes(workspace.resolve("cow2win.log"));
        byte[] expected = (message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        assertTrue(endsWith(fileBytes, expected),
                "log file does not end with the UTF-8 bytes of the entry: " + new String(fileBytes, StandardCharsets.UTF_8));
    }

    private static boolean endsWith(byte[] bytes, byte[] suffix) {
        if (bytes.length < suffix.length) {
            return false;
        }
        int offset = bytes.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (bytes[offset + i] != suffix[i]) {
                return false;
            }
        }
        return true;
    }
}
