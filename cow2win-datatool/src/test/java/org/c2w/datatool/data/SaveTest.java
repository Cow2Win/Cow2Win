package org.c2w.datatool.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.datatool.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** New entries, deletions and the catalog version - on a copy of the real resources. */
class SaveTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 2);

    @TempDir
    Path temp;
    Path root;
    DataSet data;

    @BeforeEach
    void load() throws IOException {
        root = TestResources.copyTo(temp);
        data = DataSet.load(root);
    }

    @Test
    void aNewHeroLandsInTheCatalogTheThreeLanguageFilesAndTheImageFolder() throws IOException {
        Path png = Files.write(temp.resolve("Zora.png"), Files.readAllBytes(root.resolve("images/heroes/Adam.png")));
        Map<Language, String> before = languageTexts();

        Row hero = data.addRow(TableKind.HEROES);
        data.setValue(TableKind.HEROES, hero, Language.EN.field(), "Zora the Brave");
        assertEquals("zora-the-brave", hero.getString(Fields.ID), "id suggested from the English name");
        data.setValue(TableKind.HEROES, hero, Language.DE.field(), "Zora die Tapfere");
        data.setValue(TableKind.HEROES, hero, Language.FR.field(), "Zora la Brave");
        data.setValue(TableKind.HEROES, hero, Fields.ROLES, List.of("MAGE", "SUPPORT"));
        data.setImage(TableKind.HEROES, hero, png, true);

        SaveResult result = data.save(new SaveOptions(Set.of(), false, TODAY));

        assertTrue(result.saved(), () -> "errors: " + result.errors());
        assertEquals(List.of("data/heroes.json", "language/deutsch/deutsch.properties",
                "language/english/english.properties", "language/francais/francais.properties",
                "images/heroes/Zora.png"), result.written());

        JsonArray heroes = JsonParser.parseString(read("data/heroes.json")).getAsJsonArray();
        JsonObject last = heroes.get(heroes.size() - 1).getAsJsonObject();
        assertEquals("zora-the-brave", last.get("id").getAsString());
        assertEquals("Zora.png", last.get("image").getAsString());
        assertEquals(List.of("MAGE", "SUPPORT"), last.get("roles").getAsJsonArray().asList().stream()
                .map(e -> e.getAsString()).toList());
        assertTrue(read("data/heroes.json").endsWith("""
                  {
                    "id": "zora-the-brave",
                    "image": "Zora.png",
                    "roles": [
                      "MAGE",
                      "SUPPORT"
                    ]
                  }
                ]
                """));
        assertArrayEquals(Files.readAllBytes(png), Files.readAllBytes(root.resolve("images/heroes/Zora.png")));

        Map<Language, String> names = Map.of(Language.DE, "Zora die Tapfere", Language.EN, "Zora the Brave",
                Language.FR, "Zora la Brave");
        for (Language language : Language.values()) {
            List<String> oldLines = before.get(language).lines().toList();
            List<String> newLines = read(language.relativePath().toString()).lines().toList();
            int lastHero = oldLines.indexOf("ziri=Ziri");
            assertTrue(lastHero > 0, language + ": last hero line");
            assertEquals("zora-the-brave=" + names.get(language), newLines.get(lastHero + 1),
                    language + ": new key right after the last hero");
            assertEquals(oldLines.size() + 1, newLines.size());
            assertEquals(oldLines.subList(0, lastHero + 1), newLines.subList(0, lastHero + 1));
            assertEquals(oldLines.subList(lastHero + 1, oldLines.size()), newLines.subList(lastHero + 2, newLines.size()));
        }
        assertLanguageFilesHaveTheSameKeys();
        assertEquals(read("data/cowScore.json"), Files.readString(TestResources.REAL.resolve("data/cowScore.json")));
    }

    @Test
    void newPetsCanGetACowScoreEntry() throws IOException {
        Row pet = data.addRow(TableKind.PETS);
        for (Language language : Language.values()) {
            data.setValue(TableKind.PETS, pet, language.field(), "Nimbus");
        }
        assertEquals(List.of("nimbus"), data.newEntriesWithoutCowScore(TableKind.PETS));

        assertTrue(data.save(new SaveOptions(Set.of(TableKind.PETS), false, TODAY)).saved());

        assertTrue(read("data/petCowScore.json").endsWith("  {\n    \"id\": \"nimbus\"\n  }\n]\n"));
        assertNotNull(DataSet.load(root).table(TableKind.PET_COWSCORE).findById("nimbus"));
    }

    @Test
    void deletingRemovesTheNamesKeepsTheImageAndReportsRemainingReferences() throws IOException {
        DataTable heroes = data.table(TableKind.HEROES);
        data.deleteRow(TableKind.HEROES, heroes.findById("krista"));

        SaveResult refused = data.save(new SaveOptions(Set.of(), false, TODAY));
        assertFalse(refused.saved());
        assertTrue(refused.errors().stream().anyMatch(p -> p.table() == TableKind.HERO_COMBOS
                && p.field().equals(Fields.HERO_IDS) && p.message().contains("krista")), refused.errors()::toString);
        assertTrue(read("language/english/english.properties").contains("\nkrista=Krista\n"), "nothing written");

        data.deleteRow(TableKind.HERO_COMBOS, data.table(TableKind.HERO_COMBOS).findById("krista-lars"));
        SaveResult saved = data.save(new SaveOptions(Set.of(), false, TODAY));

        assertTrue(saved.saved(), () -> "errors: " + saved.errors());
        for (Language language : Language.values()) {
            Properties properties = load(language);
            assertNull(properties.getProperty("krista"), language.name());
            assertNotNull(properties.getProperty("lars"), language.name());
        }
        assertTrue(Files.exists(root.resolve("images/heroes/Krista.png")), "images are never deleted");
        assertFalse(read("data/heroes.json").contains("\"krista\""));
        assertLanguageFilesHaveTheSameKeys();
    }

    @Test
    void titanCombosAreCheckedAndSavedLikeHeroCombos() throws IOException {
        DataTable combos = data.table(TableKind.TITAN_COMBOS);
        Row shipped = combos.findById("solaris-araji-eden-hyperion-tenebris");
        assertNotNull(shipped);
        assertEquals(List.of("solaris", "araji", "eden", "hyperion", "tenebris"), shipped.getList(Fields.TITAN_IDS));

        data.deleteRow(TableKind.TITANS, data.table(TableKind.TITANS).findById("eden"));
        SaveResult refused = data.save(new SaveOptions(Set.of(), false, TODAY));
        assertFalse(refused.saved());
        assertTrue(refused.errors().stream().anyMatch(p -> p.table() == TableKind.TITAN_COMBOS
                && p.field().equals(Fields.TITAN_IDS) && p.message().contains("eden")), refused.errors()::toString);

        data = DataSet.load(root);
        Row combo = data.addRow(TableKind.TITAN_COMBOS);
        assertEquals("C2W", combo.getString(Fields.SOURCE));
        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.ID, "ignis-araji");
        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.TITAN_IDS, List.of("ignis"));
        assertFalse(data.save(new SaveOptions(Set.of(), true, TODAY)).saved(), "a combo needs 2 titans");

        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.TITAN_IDS, List.of("ignis", "araji"));
        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.DEACTIVATED, "2030-01-01");
        SaveResult saved = data.save(new SaveOptions(Set.of(), true, TODAY));

        assertTrue(saved.saved(), () -> "errors: " + saved.errors());
        assertEquals(List.of("data/titanCombos.json"), saved.written());
        String file = read("data/titanCombos.json");
        assertTrue(file.contains("\"titanIds\": [\"ignis\", \"araji\"]"), file);
        assertEquals("2030-01-01", DataSet.load(root).table(TableKind.TITAN_COMBOS).findById("ignis-araji")
                .getString(Fields.DEACTIVATED));
    }

    @Test
    void theDataVersionIsOnlySetWhenACatalogFileChanges() throws IOException {
        String version = read("data/catalog-version.json");
        Row combo = data.table(TableKind.HERO_COMBOS).findById("krista-lars");
        data.setValue(TableKind.HERO_COMBOS, combo, Fields.NAME, "Ice and fire");
        assertFalse(data.catalogFilesChanged());

        assertEquals(List.of("data/heroCombos.json"), data.save(new SaveOptions(Set.of(), true, TODAY)).written());
        assertEquals(version, read("data/catalog-version.json"));

        data = DataSet.load(root);
        Row titan = data.table(TableKind.TITANS).findById("ignis");
        data.setValue(TableKind.TITANS, titan, Fields.ELEMENT, "WATER");
        assertTrue(data.catalogFilesChanged());

        SaveResult result = data.save(new SaveOptions(Set.of(), true, TODAY));

        assertEquals(List.of("data/titans.json", "data/catalog-version.json"), result.written());
        assertEquals(version.replaceAll("\"dataVersion\": \"[0-9-]+\"", "\"dataVersion\": \"2030-01-02\""),
                read("data/catalog-version.json"));
    }

    @Test
    void superTitanIsOnlyWrittenWhenTrue() throws IOException {
        DataTable titans = data.table(TableKind.TITANS);
        assertTrue(titans.findById("araji").getBoolean(Fields.SUPER_TITAN));
        assertFalse(titans.findById("ignis").getBoolean(Fields.SUPER_TITAN));

        data.setValue(TableKind.TITANS, titans.findById("araji"), Fields.SUPER_TITAN, false);
        data.setValue(TableKind.TITANS, titans.findById("ignis"), Fields.SUPER_TITAN, true);
        data.save(new SaveOptions(Set.of(), false, TODAY));

        JsonArray written = JsonParser.parseString(read("data/titans.json")).getAsJsonArray();
        for (var element : written) {
            JsonObject titan = element.getAsJsonObject();
            String id = titan.get("id").getAsString();
            if (id.equals("ignis")) {
                assertTrue(titan.get("superTitan").getAsBoolean());
            } else if (id.equals("araji")) {
                assertFalse(titan.has("superTitan"), "false is not written");
            }
        }
        assertTrue(read("data/titans.json").contains("""
                    "id": "ignis",
                    "element": "FIRE",
                    "roles": [
                      "SUPPORT"
                    ],
                    "superTitan": true,
                    "image": "Ignis.png"
                """), "field order id, element, roles, superTitan, image");
    }

    @Test
    void togglingABooleanBackIsNoChange() {
        Row ignis = data.table(TableKind.TITANS).findById("ignis");
        data.setValue(TableKind.TITANS, ignis, Fields.SUPER_TITAN, false);
        assertFalse(data.isDirty(), "false equals no value");
    }

    @Test
    void aNameChangeTouchesOnlyThatLine() throws IOException {
        String before = read("language/deutsch/deutsch.properties");
        Row pet = data.table(TableKind.PETS).findById("axel");
        data.setValue(TableKind.PETS, pet, Language.DE.field(), "Axel");

        assertEquals(List.of("language/deutsch/deutsch.properties"),
                data.save(new SaveOptions(Set.of(), true, TODAY)).written());
        assertEquals(before.replace("\naxel=Axell\n", "\naxel=Axel\n"), read("language/deutsch/deutsch.properties"));
    }

    private void assertLanguageFilesHaveTheSameKeys() throws IOException {
        Set<String> reference = load(Language.EN).stringPropertyNames();
        for (Language language : Language.values()) {
            assertEquals(reference, load(language).stringPropertyNames(), language.name());
        }
    }

    private Properties load(Language language) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve(language.relativePath()), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private Map<Language, String> languageTexts() throws IOException {
        Map<Language, String> texts = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            texts.put(language, read(language.relativePath().toString()));
        }
        return texts;
    }

    private String read(String relative) throws IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }
}
