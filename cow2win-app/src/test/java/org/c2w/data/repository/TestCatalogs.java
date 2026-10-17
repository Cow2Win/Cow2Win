package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.infra.CatalogDataVersion;
import org.c2w.infra.CatalogManifest;
import org.c2w.infra.JsonSupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Test catalogs on disk: copies of the repository's {@code catalog/} folder, changed as a test needs. */
final class TestCatalogs {

    /** The repository's catalog folder - Surefire runs in the app module's folder. */
    static final Path REPOSITORY_CATALOG = Path.of("..", "catalog");

    private TestCatalogs() {
    }

    /** Copies the repository's catalog to {@code target} (manifest included, unchanged) and returns {@code target}. */
    static Path copyOfRepositoryCatalog(Path target) throws IOException {
        try (Stream<Path> files = Files.walk(REPOSITORY_CATALOG)) {
            files.forEach(source -> {
                Path destination = target.resolve(REPOSITORY_CATALOG.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return target;
    }

    /** A copy of the repository's catalog with a correct manifest of the given data and format version. */
    static Path catalog(Path target, String dataVersion, int formatVersion) throws IOException {
        copyOfRepositoryCatalog(target);
        writeManifest(target, dataVersion, formatVersion);
        return target;
    }

    /** Rewrites the manifest of {@code folder} to match its files. */
    static void writeManifest(Path folder, String dataVersion, int formatVersion) throws IOException {
        CatalogManifest manifest = new CatalogManifest(formatVersion, CatalogDataVersion.parse(dataVersion),
                "test catalog", CatalogManifest.scan(folder));
        Files.writeString(folder.resolve(CatalogManifest.FILE_NAME), manifest.toJson(), StandardCharsets.UTF_8);
    }

    /**
     * Adds the hero {@code testy} (role MAGE, image {@code Testy.png}, a copy of Adam's) with the
     * names "Testy DE/EN/FR" to the catalog in {@code folder} - the manifest is not touched.
     */
    static void addTestHero(Path folder) throws IOException {
        Path heroesFile = folder.resolve("data/heroes.json");
        JsonArray heroes = JsonParser.parseString(Files.readString(heroesFile)).getAsJsonArray();
        JsonObject testy = new JsonObject();
        testy.addProperty("id", "testy");
        testy.addProperty("image", "Testy.png");
        JsonArray roles = new JsonArray();
        roles.add("MAGE");
        testy.add("roles", roles);
        heroes.add(testy);
        JsonSupport.writeJsonFile(heroes, heroesFile);
        Files.copy(folder.resolve("images/heroes/Adam.png"), folder.resolve("images/heroes/Testy.png"));

        Path namesFile = folder.resolve("names.json");
        JsonObject names = JsonParser.parseString(Files.readString(namesFile)).getAsJsonObject();
        JsonObject testyNames = new JsonObject();
        testyNames.addProperty("deutsch", "Testy DE");
        testyNames.addProperty("english", "Testy EN");
        testyNames.addProperty("francais", "Testy FR");
        names.add("testy", testyNames);
        JsonSupport.writeJsonFile(names, namesFile);
    }

    /** Changes one JSON data file of {@code folder} with {@code edit} - the manifest is not touched. */
    static void editArray(Path folder, String file, java.util.function.Consumer<JsonArray> edit) throws IOException {
        Path path = folder.resolve(file);
        JsonArray array = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
        edit.accept(array);
        JsonSupport.writeJsonFile(array, path);
    }
}
