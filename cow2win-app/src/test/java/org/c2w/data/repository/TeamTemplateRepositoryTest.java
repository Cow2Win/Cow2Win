package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.c2w.data.model.TeamTemplate;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.infra.JsonSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link TeamTemplateRepository} and {@link TeamTemplateFiles} -
 * loading/validating {@code heroTemplates.json}/{@code titanTemplates.json},
 * saving, and creating the titan file from the shipped defaults.
 */
class TeamTemplateRepositoryTest {

    @TempDir
    Path workspace;

    private void write(String fileName, String content) throws IOException {
        Files.writeString(workspace.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private static List<String> ids(TeamTemplateRepository repository, int slot) {
        return repository.template(slot).orElseThrow().memberIds();
    }

    @Nested
    @DisplayName("loading / validation")
    class Loading {

        @Test
        @DisplayName("a missing hero file means no templates and is not created")
        void missingFile() {
            TeamTemplateRepository heroes = TeamTemplateRepository.forHeroes(workspace);

            assertTrue(heroes.templates().isEmpty());
            for (int slot = 1; slot <= 5; slot++) {
                assertTrue(heroes.template(slot).isEmpty());
            }
            assertFalse(Files.exists(heroes.templateFile()));
        }

        @Test
        @DisplayName("a valid file is read with ids in file order; missing slots stay empty")
        void validFile() throws IOException {
            write(TeamTemplateRepository.HERO_FILE_NAME, """
                    [
                      { "slot": 3, "heroIds": ["astaroth", "dorian", "krista", "lars", "orion"] },
                      { "slot": 1, "heroIds": ["sebastian"] }
                    ]
                    """);

            TeamTemplateRepository heroes = TeamTemplateRepository.forHeroes(workspace);

            assertEquals(List.of(1, 3), heroes.templates().stream().map(TeamTemplate::slot).toList());
            assertEquals(List.of("sebastian"), ids(heroes, 1));
            assertEquals(List.of("astaroth", "dorian", "krista", "lars", "orion"), ids(heroes, 3));
            assertTrue(heroes.template(2).isEmpty());
        }

        @Test
        @DisplayName("invalid entries are ignored, valid ones are still loaded; unknown ids are kept")
        void invalidEntries() throws IOException {
            write(TeamTemplateRepository.HERO_FILE_NAME, """
                    [
                      { "slot": 0, "heroIds": ["a"] },
                      { "slot": 6, "heroIds": ["a"] },
                      { "heroIds": ["a"] },
                      { "slot": 1, "heroIds": [] },
                      { "slot": 2, "heroIds": ["a", "b", "c", "d", "e", "f"] },
                      { "slot": 3, "heroIds": ["a", "b", "a"] },
                      { "slot": 4, "heroIds": ["no-longer-in-catalog", "b"] },
                      { "slot": 4, "heroIds": ["later", "duplicate"] },
                      "not an object",
                      { "slot": 5, "heroIds": ["e"] }
                    ]
                    """);

            TeamTemplateRepository heroes = TeamTemplateRepository.forHeroes(workspace);

            assertEquals(List.of(4, 5), heroes.templates().stream().map(TeamTemplate::slot).toList());
            assertEquals(List.of("no-longer-in-catalog", "b"), ids(heroes, 4));
            assertEquals(List.of("e"), ids(heroes, 5));
        }

        @Test
        @DisplayName("an unreadable file means no templates, and is backed up before the first save")
        void unreadableFile() throws IOException {
            write(TeamTemplateRepository.HERO_FILE_NAME, "{ this is not json");

            TeamTemplateRepository heroes = TeamTemplateRepository.forHeroes(workspace);
            assertTrue(heroes.templates().isEmpty());

            heroes.save(1, List.of("sebastian"));
            try (Stream<Path> files = Files.list(workspace)) {
                Path backup = files.filter(p -> p.getFileName().toString()
                                .startsWith(TeamTemplateRepository.HERO_FILE_NAME + ".invalid-"))
                        .findFirst().orElseThrow();
                assertEquals("{ this is not json", Files.readString(backup, StandardCharsets.UTF_8));
            }
            assertEquals(List.of("sebastian"), ids(TeamTemplateRepository.forHeroes(workspace), 1));
        }
    }

    @Nested
    @DisplayName("saving")
    class Saving {

        @Test
        @DisplayName("hero templates: save + reload round trip, other slots untouched, file readable by hand")
        void heroRoundTrip() throws IOException {
            TeamTemplateRepository heroes = TeamTemplateRepository.forHeroes(workspace);
            heroes.save(2, List.of("orion", "sebastian", "nebula"));
            heroes.save(5, List.of("krista"));
            heroes.save(2, List.of("lars", "dorian"));

            TeamTemplateRepository reloaded = TeamTemplateRepository.forHeroes(workspace);
            assertEquals(List.of(2, 5), reloaded.templates().stream().map(TeamTemplate::slot).toList());
            assertEquals(List.of("lars", "dorian"), ids(reloaded, 2));
            assertEquals(List.of("krista"), ids(reloaded, 5));

            String content = Files.readString(heroes.templateFile(), StandardCharsets.UTF_8);
            assertTrue(content.contains("\"heroIds\""), content);
            assertTrue(content.contains("\n  {"), "expected pretty-printed JSON: " + content);
            try (Stream<Path> files = Files.list(workspace)) {
                assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                        "no temporary file must be left behind");
            }
        }

        @Test
        @DisplayName("titan templates: save + reload round trip keeps the other (default) slots")
        void titanRoundTrip() throws IOException {
            TeamTemplateRepository titans = TeamTemplateRepository.forTitans(workspace);
            titans.save(3, List.of("sigurd", "nova", "mairi"));

            TeamTemplateRepository reloaded = TeamTemplateRepository.forTitans(workspace);
            assertEquals(List.of("sigurd", "nova", "mairi"), ids(reloaded, 3));
            assertEquals(5, reloaded.templates().size());
            assertTrue(Files.readString(reloaded.templateFile(), StandardCharsets.UTF_8).contains("\"titanIds\""));
        }

        @Test
        @DisplayName("an invalid template is rejected and nothing is written")
        void invalidTemplateRejected() {
            TeamTemplateRepository heroes = TeamTemplateRepository.forHeroes(workspace);

            assertThrows(IllegalArgumentException.class, () -> heroes.save(6, List.of("a")));
            assertThrows(IllegalArgumentException.class, () -> heroes.save(1, List.of()));
            assertThrows(IllegalArgumentException.class, () -> heroes.save(1, List.of("a", "a")));
            assertFalse(Files.exists(heroes.templateFile()));
            assertTrue(heroes.templates().isEmpty());
        }
    }

    @Nested
    @DisplayName("shipped titan defaults")
    class Defaults {

        @Test
        @DisplayName("a missing titan file is created from the defaults: one template per element")
        void createdWhenMissing() {
            TeamTemplateRepository titans = TeamTemplateRepository.forTitans(workspace);

            assertTrue(Files.isRegularFile(titans.templateFile()));
            assertEquals(5, titans.templates().size());
            assertEquals(List.of("eden", "angus", "silva", "avalon", "verdoc-and-phyto"), ids(titans, 1));
        }

        @Test
        @DisplayName("an existing titan file is never touched, even with changed or missing slots")
        void existingFileUnchanged() throws IOException {
            String own = """
                    [
                      { "slot": 2, "titanIds": ["nova", "ignis"] }
                    ]
                    """;
            write(TeamTemplateRepository.TITAN_FILE_NAME, own);

            TeamTemplateRepository titans = TeamTemplateRepository.forTitans(workspace);

            assertEquals(own, Files.readString(titans.templateFile(), StandardCharsets.UTF_8));
            assertEquals(List.of(2), titans.templates().stream().map(TeamTemplate::slot).toList());
            assertEquals(List.of("nova", "ignis"), ids(titans, 2));
        }

        @Test
        @DisplayName("an existing but empty titan file stays empty")
        void emptyFileStaysEmpty() throws IOException {
            write(TeamTemplateRepository.TITAN_FILE_NAME, "[]");

            TeamTemplateRepository titans = TeamTemplateRepository.forTitans(workspace);

            assertTrue(titans.templates().isEmpty());
            assertEquals("[]", Files.readString(titans.templateFile(), StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("every default template lists all titans of its element from titans.json, in catalog order")
        void defaultsMatchTheTitanCatalog() throws IOException {
            TitanRepository catalog = new TitanRepository(workspace);
            JsonArray titansJson = JsonParser.parseString(
                    JsonSupport.readClasspathResource(TitanRepository.class, "/data/titans.json")).getAsJsonArray();
            TeamTemplateRepository titans = TeamTemplateRepository.forTitans(workspace);
            Map<Integer, TitanElement> elementBySlot = Map.of(1, TitanElement.EARTH, 2, TitanElement.FIRE,
                    3, TitanElement.WATER, 4, TitanElement.LIGHT, 5, TitanElement.DARK);

            for (var entry : elementBySlot.entrySet()) {
                // titans.json order - TitanRepository#findAll sorts by id instead
                List<String> expected = titansJson.asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .filter(titan -> titan.get("element").getAsString().equals(entry.getValue().name()))
                        .map(titan -> titan.get("id").getAsString())
                        .toList();
                List<String> actual = ids(titans, entry.getKey());
                for (String id : actual) {
                    Titan titan = catalog.findById(id).orElseThrow(() -> new AssertionError("unknown titan " + id));
                    assertEquals(entry.getValue(), titan.element(), id);
                }
                assertEquals(expected, actual, "template " + entry.getKey());
            }
        }
    }
}
