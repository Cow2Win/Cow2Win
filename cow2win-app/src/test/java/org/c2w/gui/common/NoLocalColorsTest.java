package org.c2w.gui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source scan: outside of {@link AppColors} the main code creates no colors of its own
 * ({@code new Color(}) and uses no colorful AWT constants ({@code Color.RED}, ...) - every
 * color comes from the central palette. Comments are left out.
 */
class NoLocalColorsTest {

    private static final String MODULE = "cow2win-app";

    /** {@code new Color(} and the colorful {@link java.awt.Color} constants, upper and lower case. */
    static final Pattern FORBIDDEN = Pattern.compile("new\\s+Color\\s*\\(|\\bColor\\s*\\.\\s*(RED|GREEN|BLUE|ORANGE"
            + "|YELLOW|PINK|MAGENTA|CYAN|GRAY|LIGHT_GRAY|DARK_GRAY|red|green|blue|orange|yellow|pink|magenta|cyan"
            + "|gray|lightGray|darkGray)\\b");

    @Test
    @DisplayName("No new Color( and no colorful Color constants in the main code outside of AppColors")
    void onlyAppColorsCreatesColors() throws IOException {
        Path sources = mainSources(MODULE);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                if (file.getFileName().toString().equals("AppColors.java")) {
                    continue;
                }
                violations.addAll(violations(sources.relativize(file).toString(),
                        Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(violations.isEmpty(), "Colors outside of AppColors:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("The scan finds code (strings too), but not comments")
    void scanIgnoresComments() {
        String source = """
                /** Uses new Color(1, 2, 3) - only in the comment. */
                class A {
                    Color a = Color.WHITE; // Color.RED in a comment
                    /* new Color(0) */ Color b = new Color(4, 5, 6);
                    Color c = Color.lightGray;
                    char q = '"'; Color d = Color.WHITE; String s = "// Color.RED";
                }
                """;
        List<String> found = violations("A.java", source);
        assertEquals(List.of("A.java:4: /* new Color(0) */ Color b = new Color(4, 5, 6);",
                "A.java:5: Color c = Color.lightGray;",
                "A.java:6: char q = '\"'; Color d = Color.WHITE; String s = \"// Color.RED\";"), found);
    }

    /** "file:line: code" for every line of {@code source} with a forbidden pattern outside of comments. */
    static List<String> violations(String file, String source) {
        String[] code = withoutComments(source).split("\n", -1);
        String[] lines = source.split("\n", -1);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < code.length; i++) {
            if (FORBIDDEN.matcher(code[i]).find()) {
                result.add(file + ":" + (i + 1) + ": " + lines[i].strip());
            }
        }
        return result;
    }

    /** {@code source} with block and line comments replaced by spaces - line breaks are kept. */
    static String withoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        boolean inString = false;
        while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
            if (inString) {
                out.append(c);
                if (c == '\\' && next != 0) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
                i++;
            } else if (c == '\'') {
                // a char literal such as '"' or '\'' - copied as is
                int end = source.indexOf('\'', i + (next == '\\' ? 3 : 2));
                end = end < 0 ? source.length() : end + 1;
                out.append(source, i, end);
                i = end;
            } else if (c == '/' && next == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && next == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? source.length() : end + 2;
                for (; i < end; i++) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** {@code src/main/java} of {@code module} - from the module or from the project directory. */
    static Path mainSources(String module) {
        for (Path candidate : List.of(Path.of("src/main/java"), Path.of(module, "src/main/java"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return fail("src/main/java of " + module + " not found from " + Path.of("").toAbsolutePath());
    }
}
