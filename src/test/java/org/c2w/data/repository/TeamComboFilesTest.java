package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.c2w.data.model.ComboSource;
import org.c2w.data.model.TeamCombo;
import org.c2w.data.model.TeamCombos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link TeamComboFiles} and {@link HeroComboRepository} - loading
 * and validating {@code heroCombos.json} and merging the shipped defaults
 * into the workspace copy.
 */
class TeamComboFilesTest {

    private static final Set<String> HERO_IDS = Set.of("sebastian", "nebula", "krista", "lars", "augustus", "orion",
            "dorian", "astaroth");

    private static JsonArray json(String text) {
        return JsonParser.parseString(text).getAsJsonArray();
    }

    private static TeamCombos combos(JsonArray entries) {
        return TeamComboFiles.toCombos(entries, "heroIds", HERO_IDS, "heroCombos.json");
    }

    private static List<String> ids(TeamCombos combos) {
        return combos.combos().stream().map(TeamCombo::id).toList();
    }

    private static List<String> ids(JsonArray entries) {
        return entries.asList().stream().map(e -> e.getAsJsonObject().get("id").getAsString()).toList();
    }

    @Nested
    @DisplayName("loading / validation")
    class Loading {

        @Test
        @DisplayName("reads all fields; missing source -> USER, missing name -> no custom label")
        void readsFields() {
            TeamCombos result = combos(json("""
                    [
                      {"id": "sebastian-nebula", "name": "Sebastian + Nebula", "heroIds": ["sebastian", "nebula"], "source": "C2W"},
                      {"id": "krista-lars", "heroIds": ["krista", "lars"], "deactivated": "2026-09-30"}
                    ]
                    """));

            assertEquals(List.of("sebastian-nebula", "krista-lars"), ids(result));
            TeamCombo first = result.combos().get(0);
            assertEquals("Sebastian + Nebula", first.name());
            assertEquals(List.of("sebastian", "nebula"), first.memberIds());
            assertEquals(ComboSource.C2W, first.source());
            assertTrue(first.isActive());
            TeamCombo second = result.combos().get(1);
            assertTrue(first.hasCustomName());
            assertNull(second.name());
            assertFalse(second.hasCustomName());
            assertEquals(ComboSource.USER, second.source());
            assertEquals(LocalDate.of(2026, 9, 30), second.deactivated());
            assertFalse(second.isActive());
            assertEquals(List.of(first), result.active());
        }

        @Test
        @DisplayName("invalid combos (1 or 6 heroes, unknown hero id, duplicate id, ...) are ignored, valid ones still loaded")
        void ignoresInvalidCombos() {
            TeamCombos result = combos(json("""
                    [
                      {"id": "one-hero", "heroIds": ["sebastian"]},
                      {"id": "six-heroes", "heroIds": ["sebastian", "nebula", "krista", "lars", "augustus", "orion"]},
                      {"id": "unknown-hero", "heroIds": ["sebastian", "nobody"]},
                      {"id": "valid", "heroIds": ["sebastian", "nebula"]},
                      {"id": "valid", "heroIds": ["krista", "lars"]},
                      {"id": "hero-twice", "heroIds": ["krista", "krista"]},
                      {"id": "bad-date", "heroIds": ["krista", "lars"], "deactivated": "yesterday"},
                      {"id": "no-hero-list", "heroIds": "krista"},
                      {"heroIds": ["krista", "lars"]},
                      "not an object",
                      {"id": "five-heroes", "heroIds": ["sebastian", "nebula", "krista", "lars", "augustus"]}
                    ]
                    """));

            assertEquals(List.of("valid", "five-heroes"), ids(result));
            assertEquals(List.of("sebastian", "nebula"), result.combos().get(0).memberIds(),
                    "the first combo with a duplicate id wins");
        }

        @Test
        @DisplayName("the shipped heroCombos.json is valid against the real hero catalog")
        void shippedDefaultsAreValid(@TempDir Path workspace) throws IOException {
            List<String> heroIds = new HeroRepository(workspace).findAll().stream()
                    .map(org.c2w.data.model.Hero::id).toList();
            HeroComboRepository repository = new HeroComboRepository(workspace, heroIds);

            List<String> shipped = ids(TeamComboFiles.loadDefaults(HeroComboRepository.class, "/data/heroCombos.json"));
            assertFalse(shipped.isEmpty(), "combos are shipped");
            assertEquals(shipped, ids(repository.combos()), "no shipped combo is dropped by the validation");
            assertTrue(repository.combos().combos().stream()
                    .allMatch(c -> c.source() == ComboSource.C2W && c.isActive() && !c.hasCustomName()),
                    "shipped combos are named by their (localized) heroes");
            assertTrue(Files.isRegularFile(repository.comboFile()), "workspace copy created");
            String written = Files.readString(repository.comboFile(), StandardCharsets.UTF_8);
            assertTrue(written.contains("\n    \"id\": \"" + shipped.get(0) + "\""), "pretty-printed: " + written);
        }

        @Test
        @DisplayName("a broken workspace file is left unchanged, the defaults are used for the session")
        void brokenWorkspaceFile(@TempDir Path workspace) throws IOException {
            Path file = workspace.resolve("heroCombos.json");
            Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
            JsonArray defaults = json("""
                    [{"id": "krista-lars", "heroIds": ["krista", "lars"], "source": "C2W"}]
                    """);

            JsonArray result = TeamComboFiles.loadWorkspace(file, defaults);

            assertEquals(List.of("krista-lars"), ids(result));
            assertEquals("{ not json", Files.readString(file, StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("merging the defaults into the workspace")
    class Merge {

        private final JsonArray defaults = json("""
                [
                  {"id": "sebastian-nebula", "name": "Sebastian + Nebula", "heroIds": ["sebastian", "nebula"], "source": "C2W"},
                  {"id": "krista-lars", "name": "Krista + Lars", "heroIds": ["krista", "lars"], "source": "C2W"},
                  {"id": "augustus-orion-dorian", "name": "Augustus + Orion + Dorian", "heroIds": ["augustus", "orion", "dorian"], "source": "C2W"}
                ]
                """);

        @Test
        @DisplayName("no workspace file: created from the defaults, without a backup")
        void workspaceFileMissing(@TempDir Path workspace) throws IOException {
            Path file = workspace.resolve("heroCombos.json");

            JsonArray result = TeamComboFiles.loadWorkspace(file, defaults);

            assertEquals(defaults, result);
            assertEquals(defaults, json(Files.readString(file, StandardCharsets.UTF_8)));
            assertEquals(List.of(file), listFiles(workspace), "no backup for a newly created file");
        }

        @Test
        @DisplayName("C2W combos are replaced by the current default, new ones added, removed ones dropped")
        void c2wReplacedAddedRemoved() {
            JsonArray workspace = json("""
                    [
                      {"id": "krista-lars", "name": "Old name", "heroIds": ["krista", "lars"], "source": "C2W"},
                      {"id": "no-longer-shipped", "heroIds": ["sebastian", "astaroth"], "source": "C2W"}
                    ]
                    """);

            TeamComboFiles.MergeResult result = TeamComboFiles.merge(defaults, workspace);

            assertTrue(result.changed());
            assertEquals(List.of("krista-lars", "sebastian-nebula", "augustus-orion-dorian"), ids(result.entries()));
            assertEquals(defaults.get(1), result.entries().get(0), "replaced by the default version");
        }

        @Test
        @DisplayName("USER combos are never touched, and win over a default with the same id")
        void userCombosUntouched() {
            JsonArray workspace = json("""
                    [
                      {"id": "own", "name": "Own combo", "heroIds": ["astaroth", "orion"], "source": "USER"},
                      {"id": "invalid-own", "heroIds": ["astaroth"], "source": "USER"},
                      {"id": "krista-lars", "name": "Krista + Lars", "heroIds": ["krista", "lars"], "source": "USER", "deactivated": "2026-09-30"}
                    ]
                    """);

            TeamComboFiles.MergeResult result = TeamComboFiles.merge(defaults, workspace);

            assertEquals(List.of("own", "invalid-own", "krista-lars", "sebastian-nebula", "augustus-orion-dorian"),
                    ids(result.entries()));
            for (int i = 0; i < workspace.size(); i++) {
                assertEquals(workspace.get(i), result.entries().get(i), "USER entry " + i + " unchanged");
            }
            TeamCombo kristaLars = combos(result.entries()).combos().stream()
                    .filter(c -> c.id().equals("krista-lars")).findFirst().orElseThrow();
            assertEquals(ComboSource.USER, kristaLars.source());
            assertFalse(kristaLars.isActive());
        }

        @Test
        @DisplayName("USER beats C2W with the same id, even if both are in the workspace")
        void userBeatsC2wInWorkspace() {
            JsonArray workspace = json("""
                    [
                      {"id": "krista-lars", "heroIds": ["krista", "lars"], "source": "C2W"},
                      {"id": "krista-lars", "name": "Mine", "heroIds": ["krista", "lars"], "source": "USER"}
                    ]
                    """);

            JsonArray result = TeamComboFiles.merge(defaults, workspace).entries();

            assertEquals(List.of("krista-lars", "sebastian-nebula", "augustus-orion-dorian"), ids(result));
            assertEquals("Mine", result.get(0).getAsJsonObject().get("name").getAsString());
        }

        @Test
        @DisplayName("nothing changed: the workspace file is not written")
        void noChangeNoWrite(@TempDir Path workspace) throws IOException {
            Path file = workspace.resolve("heroCombos.json");
            String content = """
                    [{"id": "own", "heroIds": ["astaroth", "orion"], "source": "USER"},
                     {"id": "sebastian-nebula", "name": "Sebastian + Nebula", "heroIds": ["sebastian", "nebula"], "source": "C2W"},
                     {"id": "krista-lars", "name": "Krista + Lars", "heroIds": ["krista", "lars"], "source": "C2W"},
                     {"id": "augustus-orion-dorian", "name": "Augustus + Orion + Dorian", "heroIds": ["augustus", "orion", "dorian"], "source": "C2W"}]
                    """;
            Files.writeString(file, content, StandardCharsets.UTF_8);

            assertFalse(TeamComboFiles.merge(defaults, json(content)).changed());
            TeamComboFiles.loadWorkspace(file, defaults);

            assertEquals(content, Files.readString(file, StandardCharsets.UTF_8), "file content unchanged");
            assertEquals(List.of(file), listFiles(workspace), "no backup either");
        }

        @Test
        @DisplayName("a change backs up the previous workspace file before writing it")
        void changeCreatesBackup(@TempDir Path workspace) throws IOException {
            Path file = workspace.resolve("heroCombos.json");
            String content = """
                    [{"id": "krista-lars", "name": "Old name", "heroIds": ["krista", "lars"], "source": "C2W"}]
                    """;
            Files.writeString(file, content, StandardCharsets.UTF_8);

            TeamComboFiles.loadWorkspace(file, defaults);

            Path backup = workspace.resolve("heroCombos.json.before-update-" + LocalDate.now() + ".bak");
            assertEquals(content, Files.readString(backup, StandardCharsets.UTF_8));
            assertEquals(List.of("krista-lars", "sebastian-nebula", "augustus-orion-dorian"),
                    ids(json(Files.readString(file, StandardCharsets.UTF_8))));
        }
    }

    private static List<Path> listFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.toList();
        }
    }
}
