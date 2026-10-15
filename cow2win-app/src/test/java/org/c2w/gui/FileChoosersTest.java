package org.c2w.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FileChoosers}: one uniform size for every file chooser of app and DataTool - headless, never shown. */
class FileChoosersTest {

    /** The sources of both modules - the app's tests run in {@code cow2win-app}. */
    private static final List<Path> SOURCES = List.of(
            Path.of("src", "main", "java"),
            Path.of("..", "cow2win-datatool", "src", "main", "java"));

    @TempDir
    Path folder;

    @Test
    @DisplayName("A chooser is SIZE_FACTOR times as wide and high as the default and opens in the start directory")
    void uniformSize() throws IOException {
        Dimension standard = new JFileChooser().getPreferredSize();

        JFileChooser chooser = FileChoosers.create(folder.toFile());

        assertEquals(1.5, FileChoosers.SIZE_FACTOR);
        assertEquals(Math.round(standard.width * FileChoosers.SIZE_FACTOR), chooser.getPreferredSize().width);
        assertEquals(Math.round(standard.height * FileChoosers.SIZE_FACTOR), chooser.getPreferredSize().height);
        // Real paths: on Windows the temp dir may come as an 8.3 short name (RUNNER~1),
        // which JFileChooser expands to the long form.
        assertEquals(folder.toRealPath(), chooser.getCurrentDirectory().toPath().toRealPath());
        assertEquals(chooser.getPreferredSize(), FileChoosers.create(null).getPreferredSize());
    }

    @Test
    @DisplayName("The directory chooser of the settings has the uniform size")
    void settingsChooserSameSize() {
        assertEquals(FileChoosers.create(null).getPreferredSize(),
                SettingsDialog.createDirectoryChooser("title").getPreferredSize());
    }

    @Test
    @DisplayName("No class of app or DataTool creates a JFileChooser itself - only FileChoosers does")
    void onlyFileChoosersCreatesChoosers() throws IOException {
        for (Path root : SOURCES) {
            assertTrue(Files.isDirectory(root), root.toString());
            try (Stream<Path> files = Files.walk(root)) {
                List<Path> offenders = files
                        .filter(file -> file.toString().endsWith(".java"))
                        .filter(file -> !file.getFileName().toString().equals("FileChoosers.java"))
                        .filter(file -> {
                            try {
                                return Files.readString(file).contains("new JFileChooser");
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .toList();
                assertEquals(List.of(), offenders);
            }
        }
    }
}
