package org.c2w.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The application name appears only in the main window's title and on the splash screen - never
 * in a dialog or message title. Checks the sources, so a new dialog cannot bring it back.
 */
class DialogTitleTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    @Test
    @DisplayName("BASE_TITLE is used only in C2WApp, Cow2Frame and SplashWindow; there is no displayTitle any more")
    void appNameOnlyInMainWindowAndSplash() throws IOException {
        Set<String> usingBaseTitle = new TreeSet<>();
        Set<String> usingDisplayTitle = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String name = file.getFileName().toString();
                if (source.contains("BASE_TITLE")) {
                    usingBaseTitle.add(name);
                }
                if (source.contains("displayTitle(")) {
                    usingDisplayTitle.add(name);
                }
            }
        }

        assertEquals(Set.of("C2WApp.java", "Cow2Frame.java", "SplashWindow.java"), usingBaseTitle);
        assertEquals(Set.of(), usingDisplayTitle);
    }
}
