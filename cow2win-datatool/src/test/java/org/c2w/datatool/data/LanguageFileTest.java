package org.c2w.datatool.data;

import org.c2w.datatool.TestResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageFileTest {

    private static final String SAMPLE = """
            #Festungen
            citadel=Festung
            barracks=Kaserne

            # Helden
            adam=Adam
            aidan=Aidan

            # Titanen
            ignis=Ignis

            # Werkzeugleiste
            toolbar.guild=Gilde:
            toolbar.text=Zeile eins\\nZeile zwei \\
              mit Fortsetzung
            """;

    @Test
    void changingAValueChangesOnlyItsLine() {
        LanguageFile file = new LanguageFile(SAMPLE);
        file.set("aidan", "Aidan der Weise");

        List<String> before = SAMPLE.lines().toList();
        List<String> after = file.text().lines().toList();
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            String expected = before.get(i).equals("aidan=Aidan") ? "aidan=Aidan der Weise" : before.get(i);
            assertEquals(expected, after.get(i), "line " + (i + 1));
        }
        assertEquals("Aidan der Weise", file.get("aidan"));
        assertTrue(file.isModified());
    }

    @Test
    void newKeysGoToTheEndOfTheirSectionAndKeysCanBeRemoved() {
        LanguageFile file = new LanguageFile(SAMPLE);
        file.add("zora", "Zora", List.of("adam", "aidan", "zora"), List.of("Helden"), false);
        file.add("moon-temple", "Mondtempel", List.of("citadel", "barracks"), List.of("Festungen"), true);
        file.remove("ignis");

        List<String> expected = new ArrayList<>(SAMPLE.lines().toList());
        expected.add(expected.indexOf("aidan=Aidan") + 1, "zora=Zora");
        expected.add(expected.indexOf("barracks=Kaserne") + 1, "moon-temple=Mondtempel");
        expected.remove("ignis=Ignis");
        assertEquals(expected, file.text().lines().toList());
        assertTrue(file.text().endsWith("mit Fortsetzung\n"), "the final line break stays");
        assertFalse(file.containsKey("ignis"));
        assertEquals("Zeile eins\nZeile zwei mit Fortsetzung", file.get("toolbar.text"));
    }

    @Test
    void aKeyOfAnEmptySectionGoesBelowItsHeader() {
        LanguageFile file = new LanguageFile(SAMPLE);
        file.add("albus", "Albus", List.of("albus"), List.of("Titanen"), false);
        List<String> lines = file.text().lines().toList();
        assertEquals("albus=Albus", lines.get(lines.indexOf("# Titanen") + 1));
    }

    @Test
    void valuesAreEscapedOnlyWhereTheFormatNeedsIt() throws IOException {
        LanguageFile file = new LanguageFile(SAMPLE);
        String name = " Ätherprisma: K'arkh = \\ #1";
        file.set("adam", name);
        assertTrue(file.text().contains("adam=\\ Ätherprisma: K'arkh = \\\\ #1\n"), file.text());

        Properties properties = new Properties();
        properties.load(new StringReader(file.text()));
        assertEquals(name, properties.getProperty("adam"));
        assertEquals(name, file.get("adam"));
    }

    @Test
    void readsTheRealFilesLikeProperties() throws IOException {
        for (Language language : Language.values()) {
            String text = Files.readString(TestResources.REAL.resolve(language.relativePath()), StandardCharsets.UTF_8);
            Properties properties = new Properties();
            properties.load(new StringReader(text));
            LanguageFile file = new LanguageFile(text);
            assertEquals(properties.stringPropertyNames(), file.keys(), language.name());
            for (String key : properties.stringPropertyNames()) {
                assertEquals(properties.getProperty(key), file.get(key), language + " " + key);
            }
        }
    }
}
