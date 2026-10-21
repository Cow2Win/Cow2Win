package org.c2w.datatool.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.datatool.TestResources;
import org.c2w.infra.CatalogManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** New entries, deletions, names and the catalog manifest - on a copy of the real repository files. */
class SaveTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 2);
    /** The app resources, relative to the repository root. */
    private static final String APP = "cow2win-app/src/main/resources/";
    private static final String MANIFEST = "catalog/manifest.json";
    private static final String NAMES = "catalog/names.json";

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
    void aNewHeroLandsInTheCatalogTheNamesAndTheImageFolder() throws IOException {
        Path png = Files.write(temp.resolve("Nyx.png"), Files.readAllBytes(root.resolve("catalog/images/heroes/Adam.png")));
        List<String> before = read(NAMES).lines().toList();
        Map<Language, String> languageFiles = languageTexts();

        Row hero = data.addRow(TableKind.HEROES);
        data.setValue(TableKind.HEROES, hero, Language.EN.field(), "Nyx the Brave");
        assertEquals("nyx-the-brave", hero.getString(Fields.ID), "id suggested from the English name");
        data.setValue(TableKind.HEROES, hero, Language.DE.field(), "Nyx die Tapfere");
        data.setValue(TableKind.HEROES, hero, Language.ES.field(), "Nyx la Valiente");
        data.setValue(TableKind.HEROES, hero, Language.FR.field(), "Nyx la Brave");
        data.setValue(TableKind.HEROES, hero, Fields.ROLES, List.of("MAGE", "SUPPORT"));
        data.setImage(TableKind.HEROES, hero, png, true);

        SaveResult result = data.save(new SaveOptions(Set.of(), TODAY));

        assertTrue(result.saved(), () -> "errors: " + result.errors());
        assertEquals(List.of("catalog/data/heroes.json", NAMES, "catalog/images/heroes/Nyx.png", MANIFEST),
                result.written());
        assertManifestMatchesTheCatalog();

        JsonArray heroes = JsonParser.parseString(read("catalog/data/heroes.json")).getAsJsonArray();
        JsonObject last = heroes.get(heroes.size() - 1).getAsJsonObject();
        assertEquals("nyx-the-brave", last.get("id").getAsString());
        assertEquals("Nyx.png", last.get("image").getAsString());
        assertEquals(List.of("MAGE", "SUPPORT"), last.get("roles").getAsJsonArray().asList().stream()
                .map(e -> e.getAsString()).toList());
        assertTrue(read("catalog/data/heroes.json").endsWith("""
                  {
                    "id": "nyx-the-brave",
                    "image": "Nyx.png",
                    "roles": [
                      "MAGE",
                      "SUPPORT"
                    ]
                  }
                ]
                """));
        assertArrayEquals(Files.readAllBytes(png), Files.readAllBytes(root.resolve("catalog/images/heroes/Nyx.png")));

        // One new line, at its place in id order; every other line unchanged.
        List<String> after = read(NAMES).lines().toList();
        int nyx = after.indexOf("  \"nyx-the-brave\": {\"deutsch\": \"Nyx die Tapfere\", \"english\": \"Nyx the Brave\","
                + " \"espanol\": \"Nyx la Valiente\","
                + " \"francais\": \"Nyx la Brave\"},");
        assertTrue(nyx > 0, () -> "new line in names.json: " + after);
        assertTrue(after.get(nyx - 1).startsWith("  \"nova\": "), "sorted by id");
        List<String> withoutNyx = new java.util.ArrayList<>(after);
        withoutNyx.remove(nyx);
        assertEquals(before, withoutNyx);
        assertNamesComplete();
        assertEquals(languageFiles, languageTexts(), "the language files are not touched");
        assertEquals(read(APP + "data/cowScore.json"), Files.readString(TestResources.REPOSITORY.resolve(APP + "data/cowScore.json")));
    }

    @Test
    void newPetsCanGetACowScoreEntry() throws IOException {
        Row pet = data.addRow(TableKind.PETS);
        for (Language language : Language.values()) {
            data.setValue(TableKind.PETS, pet, language.field(), "Nimbus");
        }
        assertEquals(List.of("nimbus"), data.newEntriesWithoutCowScore(TableKind.PETS));

        assertTrue(data.save(new SaveOptions(Set.of(TableKind.PETS), TODAY)).saved());

        assertTrue(read(APP + "data/petCowScore.json").endsWith("  {\n    \"id\": \"nimbus\"\n  }\n]\n"));
        assertNotNull(DataSet.load(root).table(TableKind.PET_COWSCORE).findById("nimbus"));
    }

    @Test
    void deletingASavedEntryIsRefusedByTheCatalogCheck() throws IOException {
        Map<Path, byte[]> before = RoundTripTest.snapshot(root);
        DataTable heroes = data.table(TableKind.HEROES);
        data.deleteRow(TableKind.HEROES, heroes.findById("krista"));

        SaveResult refused = data.save(new SaveOptions(Set.of(), TODAY));
        assertFalse(refused.saved());
        assertTrue(refused.errors().stream().anyMatch(p -> p.table() == TableKind.HERO_COMBOS
                && p.field().equals(Fields.HERO_IDS) && p.message().contains("krista")), refused.errors()::toString);

        // Without the combo the data check passes - the catalog check still refuses: ids are never removed.
        data.deleteRow(TableKind.HERO_COMBOS, data.table(TableKind.HERO_COMBOS).findById("krista-lars"));
        SaveResult stillRefused = data.save(new SaveOptions(Set.of(), TODAY));

        assertFalse(stillRefused.saved());
        assertTrue(stillRefused.errors().stream().anyMatch(p -> p.table() == TableKind.HEROES
                && p.message().contains("id krista is missing")), stillRefused.errors()::toString);
        Map<Path, byte[]> after = RoundTripTest.snapshot(root);
        assertEquals(before.keySet(), after.keySet(), "nothing written");
        before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), path.toString()));
    }

    @Test
    void aNewEntryCanBeDeletedAgainBeforeSaving() throws IOException {
        Row hero = data.addRow(TableKind.HEROES);
        data.setValue(TableKind.HEROES, hero, Language.EN.field(), "Nyx");
        data.deleteRow(TableKind.HEROES, hero);
        assertTrue(data.save(new SaveOptions(Set.of(), TODAY)).saved());
        assertNamesComplete();
    }

    @Test
    void anImageMissingInTheCatalogIsRefused() throws IOException {
        Row adam = data.table(TableKind.HEROES).findById("adam");
        data.setValue(TableKind.HEROES, adam, Fields.IMAGE, "NoSuchImage.png");

        SaveResult refused = data.save(new SaveOptions(Set.of(), TODAY));

        assertFalse(refused.saved());
        assertTrue(refused.errors().stream().anyMatch(p -> p.table() == TableKind.HEROES
                && p.message().contains("NoSuchImage.png")), refused.errors()::toString);
    }

    @Test
    void titanCombosAreCheckedAndSavedLikeHeroCombos() throws IOException {
        DataTable combos = data.table(TableKind.TITAN_COMBOS);
        Row shipped = combos.findById("solaris-araji-eden-hyperion-tenebris");
        assertNotNull(shipped);
        assertEquals(List.of("solaris", "araji", "eden", "hyperion", "tenebris"), shipped.getList(Fields.TITAN_IDS));

        data.deleteRow(TableKind.TITANS, data.table(TableKind.TITANS).findById("eden"));
        SaveResult refused = data.save(new SaveOptions(Set.of(), TODAY));
        assertFalse(refused.saved());
        assertTrue(refused.errors().stream().anyMatch(p -> p.table() == TableKind.TITAN_COMBOS
                && p.field().equals(Fields.TITAN_IDS) && p.message().contains("eden")), refused.errors()::toString);

        data = DataSet.load(root);
        Row combo = data.addRow(TableKind.TITAN_COMBOS);
        assertEquals("C2W", combo.getString(Fields.SOURCE));
        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.ID, "ignis-araji");
        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.TITAN_IDS, List.of("ignis"));
        assertFalse(data.save(new SaveOptions(Set.of(), TODAY)).saved(), "a combo needs 2 titans");

        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.TITAN_IDS, List.of("ignis", "araji"));
        data.setValue(TableKind.TITAN_COMBOS, combo, Fields.DEACTIVATED, "2030-01-01");
        SaveResult saved = data.save(new SaveOptions(Set.of(), TODAY));

        assertTrue(saved.saved(), () -> "errors: " + saved.errors());
        assertEquals(List.of(APP + "data/titanCombos.json"), saved.written());
        String file = read(APP + "data/titanCombos.json");
        assertTrue(file.contains("\"titanIds\": [\"ignis\", \"araji\"]"), file);
        assertEquals("2030-01-01", DataSet.load(root).table(TableKind.TITAN_COMBOS).findById("ignis-araji")
                .getString(Fields.DEACTIVATED));
    }

    @Test
    void theManifestIsOnlyRewrittenWhenTheCatalogChanges() throws IOException {
        String manifestBefore = read(MANIFEST);
        CatalogManifest before = CatalogManifest.parse(manifestBefore);
        Row combo = data.table(TableKind.HERO_COMBOS).findById("krista-lars");
        data.setValue(TableKind.HERO_COMBOS, combo, Fields.NAME, "Ice and fire");

        assertEquals(List.of(APP + "data/heroCombos.json"), data.save(new SaveOptions(Set.of(), TODAY)).written());
        assertEquals(manifestBefore, read(MANIFEST), "a CowScore/combo change does not touch the catalog");

        data = DataSet.load(root);
        Row titan = data.table(TableKind.TITANS).findById("ignis");
        data.setValue(TableKind.TITANS, titan, Fields.ELEMENT, "WATER");

        SaveResult result = data.save(new SaveOptions(Set.of(), TODAY));

        assertEquals(List.of("catalog/data/titans.json", MANIFEST), result.written());
        CatalogManifest after = CatalogManifest.parse(read(MANIFEST));
        assertEquals("2030-01-02", after.dataVersion().toString());
        assertEquals(before.formatVersion(), after.formatVersion());
        assertEquals(before.note(), after.note());
        assertManifestMatchesTheCatalog();

        data = DataSet.load(root);
        data.setValue(TableKind.TITANS, data.table(TableKind.TITANS).findById("ignis"), Fields.ELEMENT, "FIRE");
        data.save(new SaveOptions(Set.of(), TODAY));
        assertEquals("2030-01-02.1", CatalogManifest.parse(read(MANIFEST)).dataVersion().toString(),
                "a second catalog state on the same day");
        assertManifestMatchesTheCatalog();
    }

    @Test
    void superTitanIsOnlyWrittenWhenTrue() throws IOException {
        DataTable titans = data.table(TableKind.TITANS);
        assertTrue(titans.findById("araji").getBoolean(Fields.SUPER_TITAN));
        assertFalse(titans.findById("ignis").getBoolean(Fields.SUPER_TITAN));

        data.setValue(TableKind.TITANS, titans.findById("araji"), Fields.SUPER_TITAN, false);
        data.setValue(TableKind.TITANS, titans.findById("ignis"), Fields.SUPER_TITAN, true);
        data.save(new SaveOptions(Set.of(), TODAY));

        JsonArray written = JsonParser.parseString(read("catalog/data/titans.json")).getAsJsonArray();
        for (var element : written) {
            JsonObject titan = element.getAsJsonObject();
            String id = titan.get("id").getAsString();
            if (id.equals("ignis")) {
                assertTrue(titan.get("superTitan").getAsBoolean());
            } else if (id.equals("araji")) {
                assertFalse(titan.has("superTitan"), "false is not written");
            }
        }
        assertTrue(read("catalog/data/titans.json").contains("""
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
        String before = read(NAMES);
        Row pet = data.table(TableKind.PETS).findById("axel");
        data.setValue(TableKind.PETS, pet, Language.DE.field(), "Axel");

        assertEquals(List.of(NAMES, MANIFEST), data.save(new SaveOptions(Set.of(), TODAY)).written());
        assertEquals(before.replace("\n  \"axel\": {\"deutsch\": \"Axell\",", "\n  \"axel\": {\"deutsch\": \"Axel\","),
                read(NAMES));
        assertManifestMatchesTheCatalog();
    }

    /** The written manifest passes the app's consistency check: exactly the catalog's files, sizes and hashes. */
    private void assertManifestMatchesTheCatalog() throws IOException {
        assertEquals(CatalogManifest.scan(root.resolve("catalog")), CatalogManifest.parse(read(MANIFEST)).files());
    }

    /** names.json holds exactly the catalog ids, each with a name in every required language. */
    private void assertNamesComplete() throws IOException {
        NamesFile names = new NamesFile(read(NAMES));
        DataSet saved = DataSet.load(root);
        Set<String> ids = new java.util.TreeSet<>();
        for (TableKind kind : TableKind.CATALOGS) {
            ids.addAll(saved.table(kind).ids());
        }
        assertEquals(ids, names.ids());
        for (String id : ids) {
            for (Language language : Language.values()) {
                if (language.nameRequired()) {
                    assertNotNull(names.get(id, language), id + " " + language);
                }
            }
        }
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
