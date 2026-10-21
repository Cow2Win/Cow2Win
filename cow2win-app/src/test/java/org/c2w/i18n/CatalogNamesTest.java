package org.c2w.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display names of the catalog entries in {@code catalog/names.json}: complete for every
 * catalog id and every language, nothing else in there, and no catalog id left in the language
 * files - plus how {@link LanguageService} looks them up.
 */
public class CatalogNamesTest {

    private static final Path LANGUAGE_DIR = Paths.get("src", "main", "resources", "language");
    private static final List<String> CATALOG_FILES = List.of("data/fortifications.json", "data/heroes.json",
            "data/titans.json", "data/pets.json", "data/warFlags.json");
    /**
     * Catalog ids without a Spanish name yet, e.g. a new entry whose in-game name no Spanish battle log or
     * game screen has shown (the app shows the English one). Empty since every entry has its Spanish name;
     * remove an id once its name is added.
     */
    public static final Set<String> OPEN_SPANISH_NAMES = Set.of();

    @TempDir
    Path temp;

    @AfterEach
    void backToTheBuiltInCatalog() {
        CatalogStore.useBuiltIn();
        LanguageService.resetCache();
    }

    @Test
    @DisplayName("names.json has a non-blank name for every catalog id in every language, and no unknown id")
    void everyCatalogIdHasANameInEveryLanguage() throws IOException {
        Set<String> ids = catalogIds();
        JsonObject names = names();

        List<String> problems = new ArrayList<>();
        for (String id : ids) {
            JsonObject byLanguage = names.has(id) ? names.getAsJsonObject(id) : new JsonObject();
            for (String language : languageFolders()) {
                JsonElement name = byLanguage.get(language);
                boolean missing = name == null || name.isJsonNull() || name.getAsString().isBlank();
                boolean open = language.equals("espanol") && OPEN_SPANISH_NAMES.contains(id);
                if (missing && !open) {
                    problems.add(id + " has no " + language + " name");
                } else if (!missing && open) {
                    problems.add(id + " has an espanol name now - remove it from OPEN_SPANISH_NAMES");
                }
            }
        }
        for (String id : names.keySet()) {
            if (!ids.contains(id)) {
                problems.add("unknown id " + id);
            }
        }
        assertTrue(problems.isEmpty(), "catalog/names.json: " + problems);
        assertEquals(new ArrayList<>(new TreeSet<>(names.keySet())), new ArrayList<>(names.keySet()), "sorted by id");
    }

    @Test
    @DisplayName("The language files contain no catalog id as a key any more")
    void languageFilesHoldNoCatalogIds() throws IOException {
        Set<String> ids = catalogIds();
        for (String language : languageFolders()) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(LANGUAGE_DIR.resolve(language).resolve(language + ".properties"),
                    StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Set<String> left = new TreeSet<>(properties.stringPropertyNames());
            left.retainAll(ids);
            assertTrue(left.isEmpty(), language + ".properties still holds catalog ids: " + left);
        }
    }

    @Test
    @DisplayName("Samples (umlaut, apostrophe, 'and', war flag) read the same as from the former language files")
    void samplesAreUnchangedByTheMove() {
        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("heros-bridge", List.of("Heldenbrücke", "Heroes' Bridge", "Pont des héros", "Puente de los Héroes")),
                Map.entry("foundry", List.of("Gießerei", "Foundry", "Fonderie", "Forja")),
                Map.entry("ether-prism", List.of("Ätherprisma", "Ether Prism", "Prisme d'éther", "Prisma Etéreo")),
                Map.entry("karkh", List.of("K'arkh", "K'arkh", "K'arkh", "K'arkh")),
                Map.entry("astrid-and-lucas", List.of("Astrid und Lucas", "Astrid and Lucas", "Astrid et Lucas", "Astrid y Lucas")),
                Map.entry("mushy-and-shroom", List.of("Champi und Gnon", "Mushy and Shroom", "Champi et Gnon", "Mushy y Shroom")),
                Map.entry("cleaver", List.of("Hackebeil", "Cleaver", "Coupe-chou", "Tajador")),
                Map.entry("tidus-and-gelo", List.of("Tidus und Gelo", "Tidus and Gelo", "Tidus et Gelo", "Tidus y Gelo")),
                Map.entry("axel", List.of("Axell", "Axel", "Axel", "Axel")),
                Map.entry("merlin", List.of("Merlin", "Merlin", "Merlin", "Merlín")),
                Map.entry("flag-swift-warriors", List.of("Kriegsflagge der flinken Krieger",
                        "War Flag of Swift Warriors", "Drapeau de guerre : Guerriers agiles",
                        "Bandera de guerra de guerreros rápidos")));
        List<String> languages = List.of("deutsch", "english", "francais", "espanol");
        String previous = Config.getLanguage();
        try {
            expected.forEach((id, names) -> {
                for (int i = 0; i < languages.size(); i++) {
                    String language = languages.get(i);
                    // An empty expectation: no name in this language (OPEN_SPANISH_NAMES) - English is shown.
                    boolean open = names.get(i).isEmpty();
                    assertEquals(open ? null : names.get(i), LanguageService.textIn(language, id), id + " " + language);
                    Config.setLanguage(language);
                    assertEquals(open ? names.get(1) : names.get(i), LanguageService.displayName(id), id + " " + language);
                }
            });
        } finally {
            Config.setLanguage(previous);
        }
    }

    @Test
    @DisplayName("A missing name falls back to English, then to the id; UI keys still come from the language file")
    void fallbackToEnglishThenToTheId() throws IOException {
        Files.writeString(temp.resolve("names.json"), """
                {
                  "only-english": {"english": "Only English"},
                  "no-name": {}
                }
                """, StandardCharsets.UTF_8);
        CatalogStore.useFolder(temp);
        LanguageService.resetCache();
        String previous = Config.getLanguage();
        try {
            Config.setLanguage("deutsch");
            assertEquals("Only English", LanguageService.displayName("only-english"));
            assertEquals("no-name", LanguageService.displayName("no-name"));
            assertEquals("Gilde:", LanguageService.displayName("toolbar.guild"), "UI texts unchanged");
            assertEquals("unknown-key", LanguageService.displayName("unknown-key"));

            assertNull(LanguageService.textIn("deutsch", "only-english"), "textIn has no English fallback");
            assertEquals("Only English", LanguageService.textIn("english", "only-english"));
        } finally {
            Config.setLanguage(previous);
        }
    }

    // --- helpers ---

    private static Set<String> catalogIds() throws IOException {
        Set<String> ids = new TreeSet<>();
        for (String file : CATALOG_FILES) {
            for (JsonElement element : JsonParser.parseString(CatalogStore.readText(file)).getAsJsonArray()) {
                ids.add(element.getAsJsonObject().get("id").getAsString());
            }
        }
        return ids;
    }

    private static JsonObject names() throws IOException {
        return JsonParser.parseString(CatalogStore.readText("names.json")).getAsJsonObject();
    }

    /** Every language folder that has a {@code <name>.properties} - each of them needs every catalog name. */
    private static List<String> languageFolders() throws IOException {
        List<String> languages = new ArrayList<>();
        try (var dirs = Files.newDirectoryStream(LANGUAGE_DIR, Files::isDirectory)) {
            for (Path dir : dirs) {
                String name = dir.getFileName().toString();
                if (Files.isRegularFile(dir.resolve(name + ".properties"))) {
                    languages.add(name);
                }
            }
        }
        assertTrue(languages.size() >= 3, "languages: " + languages);
        return languages;
    }
}
