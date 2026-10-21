package org.c2w.data.repository;

import com.google.gson.JsonObject;
import org.c2w.i18n.CatalogNamesTest;
import org.c2w.infra.CatalogDataVersion;
import org.c2w.infra.CatalogManifest;
import org.c2w.infra.CatalogSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One test per error rule of {@link CatalogValidator}, plus the repository's catalog and a warning. */
class CatalogValidatorTest {

    @TempDir
    Path temp;

    @Test
    void theRepositoryCatalogHasNoErrors() throws IOException {
        CatalogValidator.Result builtIn = CatalogValidator.validate(CatalogSource.builtIn(),
                CatalogValidator.idsOf(CatalogSource.builtIn()));
        assertEquals(List.of(), builtIn.errors());
        assertEquals(openSpanishNameWarnings(), Set.copyOf(builtIn.warnings()));

        CatalogValidator.Result folder = CatalogValidator.validate(
                CatalogSource.folder(TestCatalogs.REPOSITORY_CATALOG), null);
        assertEquals(openSpanishNameWarnings(), Set.copyOf(folder.findings()));
    }

    /** The only warnings of the shipped catalog: the entries no Spanish battle log has named yet. */
    private static Set<CatalogValidator.Finding> openSpanishNameWarnings() {
        return CatalogNamesTest.OPEN_SPANISH_NAMES.stream()
                .map(id -> new CatalogValidator.Finding(false, "names.json: " + id + " has no espanol name"))
                .collect(Collectors.toSet());
    }

    @Test
    void pathsOutsideTheLayoutAreErrors() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        for (String path : List.of("data/evil.exe", "images/other/x.png", "../x.json", "images/heroes/..png",
                "/data/heroes.json", "data/sub/heroes.json", "images/heroes/a b.png")) {
            CatalogValidator.Result result = validate(folder, entries -> entries.add(new CatalogManifest.FileEntry(path, 1, "00")));
            assertError(result, "Path not allowed in a catalog: " + path);
        }
    }

    @Test
    void aPathListedTwiceIsAnError() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        CatalogValidator.Result result = validate(folder, entries -> entries.add(entries.get(0)));
        assertError(result, "Path listed twice: " + manifestOf(folder).files().get(0).path());
    }

    @Test
    void tooLargeFilesAreErrors() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        CatalogValidator.Result result = validate(folder, entries ->
                entries.add(new CatalogManifest.FileEntry("images/heroes/Big.png", CatalogValidator.MAX_FILE_SIZE + 1, "00")));
        assertError(result, "images/heroes/Big.png is too large");

        result = validate(folder, entries -> {
            for (int i = 0; i < 11; i++) {
                entries.add(new CatalogManifest.FileEntry("images/heroes/Big" + i + ".png", CatalogValidator.MAX_FILE_SIZE, "00"));
            }
        });
        assertError(result, "The catalog is too large");
    }

    @Test
    void aWrongChecksumAndAMissingFileAreErrors() throws IOException {
        Path folder = TestCatalogs.catalog(temp, "2099-01-01", 1);
        Files.writeString(folder.resolve("data/pets.json"), "[]");
        Files.delete(folder.resolve("images/flags/Bastion.png"));

        CatalogValidator.Result result = CatalogValidator.validate(CatalogSource.folder(folder), null);

        assertError(result, "Size or SHA-256 does not match the manifest: data/pets.json");
        assertError(result, "Missing file: images/flags/Bastion.png");
    }

    @Test
    void aMissingDataFileIsAnError() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        Files.delete(folder.resolve("data/warFlags.json"));
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);
        assertError(CatalogValidator.validate(CatalogSource.folder(folder), null), "Missing file: data/warFlags.json");
    }

    @Test
    void anUnreadableDataFileIsAnError() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        Files.writeString(folder.resolve("data/titans.json"), "{\"not\": \"an array\"}");
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);
        assertError(CatalogValidator.validate(CatalogSource.folder(folder), null), "data/titans.json cannot be read");
    }

    @Test
    void aDuplicateIdIsAnError() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        TestCatalogs.editArray(folder, "data/pets.json", pets -> pets.add(pets.get(0).deepCopy()));
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);
        String id = pets(folder).get(0);
        assertError(CatalogValidator.validate(CatalogSource.folder(folder), null),
                "data/pets.json: id " + id + " is used more than once");
    }

    @Test
    void aDroppedIdIsAnErrorOnlyWithAComparison() throws IOException {
        Map<String, Set<String>> previous = CatalogValidator.idsOf(CatalogSource.builtIn());
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        TestCatalogs.editArray(folder, "data/heroes.json", heroes -> {
            for (int i = 0; i < heroes.size(); i++) {
                if (heroes.get(i).getAsJsonObject().get("id").getAsString().equals("krista")) {
                    heroes.remove(i);
                    return;
                }
            }
        });
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);

        assertError(CatalogValidator.validate(CatalogSource.folder(folder), previous),
                "data/heroes.json: id krista is missing");
        assertTrue(CatalogValidator.validate(CatalogSource.folder(folder), null).isValid(), "no comparison");
    }

    @Test
    void anImageNotInTheCatalogIsAnError() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        TestCatalogs.editArray(folder, "data/titans.json",
                titans -> titans.get(0).getAsJsonObject().addProperty("image", "Nowhere.png"));
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);
        assertError(CatalogValidator.validate(CatalogSource.folder(folder), null),
                "image Nowhere.png of " + titans(folder).get(0) + " is not in the catalog");
    }

    @Test
    void anUnknownPrerequisiteIsAnError() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        TestCatalogs.editArray(folder, "data/fortifications.json", forts -> forts.get(0).getAsJsonObject()
                .getAsJsonArray("prerequisites").add("no-such-fortification"));
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);
        assertError(CatalogValidator.validate(CatalogSource.folder(folder), null),
                "has the unknown prerequisite no-such-fortification");
    }

    @Test
    void unknownRolesMissingNamesAndUnusedImagesAreOnlyWarnings() throws IOException {
        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp);
        TestCatalogs.editArray(folder, "data/heroes.json",
                heroes -> heroes.get(0).getAsJsonObject().getAsJsonArray("roles").add("JUGGLER"));
        String names = Files.readString(folder.resolve("names.json"), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("names.json"),
                names.replace("\"albus\": {\"deutsch\": \"Albus\", ", "\"albus\": {"), StandardCharsets.UTF_8);
        Files.copy(folder.resolve("images/heroes/Adam.png"), folder.resolve("images/heroes/Unused.png"));
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);

        CatalogValidator.Result result = CatalogValidator.validate(CatalogSource.folder(folder), null);

        assertTrue(result.isValid(), result::toString);
        assertWarning(result, "unknown role 'JUGGLER'");
        assertWarning(result, "names.json: albus has no deutsch name");
        assertWarning(result, "Image not referred to by any entry: images/heroes/Unused.png");
    }

    @Test
    void anUnsupportedFormatIsAnError() throws IOException {
        Path folder = TestCatalogs.catalog(temp, "2099-01-01", 2);
        assertError(CatalogValidator.validate(CatalogSource.folder(folder), null), "formatVersion 2 is not supported");
    }

    // --- helpers ---

    /** Validates {@code folder} with a manifest of its files, changed by {@code edit}. */
    private static CatalogValidator.Result validate(Path folder, java.util.function.Consumer<List<CatalogManifest.FileEntry>> edit)
            throws IOException {
        List<CatalogManifest.FileEntry> entries = new ArrayList<>(CatalogManifest.scan(folder));
        edit.accept(entries);
        CatalogManifest manifest = new CatalogManifest(1, CatalogDataVersion.parse("2099-01-01"), "", entries);
        Files.writeString(folder.resolve(CatalogManifest.FILE_NAME), manifest.toJson(), StandardCharsets.UTF_8);
        return CatalogValidator.validate(CatalogSource.folder(folder), null);
    }

    private static CatalogManifest manifestOf(Path folder) throws IOException {
        return CatalogManifest.parse(Files.readString(folder.resolve(CatalogManifest.FILE_NAME)));
    }

    private static List<String> pets(Path folder) throws IOException {
        return ids(folder, "data/pets.json");
    }

    private static List<String> titans(Path folder) throws IOException {
        return ids(folder, "data/titans.json");
    }

    private static List<String> ids(Path folder, String file) throws IOException {
        List<String> ids = new ArrayList<>();
        for (var element : com.google.gson.JsonParser.parseString(Files.readString(folder.resolve(file))).getAsJsonArray()) {
            JsonObject object = element.getAsJsonObject();
            ids.add(object.get("id").getAsString());
        }
        return ids;
    }

    private static void assertError(CatalogValidator.Result result, String text) {
        assertTrue(result.errors().stream().anyMatch(error -> error.message().contains(text)),
                () -> "expected an error containing '" + text + "', got " + result.findings());
    }

    private static void assertWarning(CatalogValidator.Result result, String text) {
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.message().contains(text)),
                () -> "expected a warning containing '" + text + "', got " + result.findings());
    }
}
