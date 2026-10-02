package org.c2w.data.repository;

import org.c2w.data.model.*;
import org.c2w.infra.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.c2w.data.model.TitanElement.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GuildRepository}'s handling of a titan team's optional "totems":
 * round trip with 0/1/2 totems, guild files saved before totems existed,
 * and the clean-up of broken entries (each logged, never failing the load).
 */
class GuildRepositoryTotemTest {

    /** The catalogs seed their CowScore workspace files here instead of the real workspace. */
    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        catalog = new Catalog(workspace);
    }

    private Titan titan(String id) {
        return catalog.titans().findById(id).orElseThrow();
    }

    /** ignis + vulcan (fire), nova + sigurd (water), eden (earth) - allows the fire and the water totem. */
    private List<String> titanIds() {
        return List.of("ignis", "vulcan", "nova", "sigurd", "eden");
    }

    private Path writeGuildFile(String titanTeamJson) throws IOException {
        String json = """
                {
                  "id": "g1",
                  "name": "Guild",
                  "season": 1,
                  "seasonStart": "2026-01-01",
                  "members": [
                    { "id": "m1", "name": "Member", "heroTeams": [], "titanTeams": [ %s ] }
                  ]
                }
                """.formatted(titanTeamJson);
        Path file = workspace.resolve("guild.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> captureLogSince() {
        List<String> captured = new ArrayList<>();
        Logger.addListener(captured::add);
        captured.clear(); // drop the immediate replay of everything logged before this test
        return captured;
    }

    private static boolean logged(List<String> log, String... fragments) {
        return log.stream().anyMatch(entry -> {
            for (String fragment : fragments) {
                if (!entry.contains(fragment)) {
                    return false;
                }
            }
            return true;
        });
    }

    @Test
    @DisplayName("0, 1 and 2 totems survive a save/load round trip; no totems = no field")
    void roundTrip() throws IOException {
        List<Titan> titans = titanIds().stream().map(this::titan).toList();
        GuildMember member = new GuildMember("m1", "Member", List.of(), List.of(
                new TitanTeam("m1", 0, titans, 1000, LocalDate.of(2026, 9, 29), Set.of(WATER, FIRE)),
                new TitanTeam("m1", 1, titans, 900, null, Set.of(WATER))));
        GuildMember noTotems = new GuildMember("m2", "Other", List.of(), List.of(
                new TitanTeam("m2", 0, titans, 800, null)));
        Guild guild = new Guild("g1", "Guild", List.of(member, noTotems));

        Path file = workspace.resolve("guild.json");
        GuildRepository.save(guild, file);
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Guild loaded = GuildRepository.load(file, catalog);

        assertEquals(List.of(FIRE, WATER), new ArrayList<>(loaded.members().get(0).titanTeams().get(0).totems()));
        assertEquals(Set.of(WATER), loaded.members().get(0).titanTeams().get(1).totems());
        assertEquals(Set.of(), loaded.members().get(1).titanTeams().get(0).totems());
        assertEquals(2, json.split("\"totems\"", -1).length - 1, "only the two teams with totems get the field: " + json);
        assertTrue(json.indexOf("\"FIRE\"") < json.indexOf("\"WATER\""), "stored in element order");
    }

    @Test
    @DisplayName("a guild file saved before totems existed still loads, without totems")
    void legacyFileWithoutField() throws IOException {
        Path file = writeGuildFile("""
                { "titanIds": ["ignis", "vulcan"], "totalPower": 1000 }
                """);
        TitanTeam team = GuildRepository.load(file, catalog).members().get(0).titanTeams().get(0);
        assertEquals(Set.of(), team.totems());
        assertEquals(2, team.titans().size());
    }

    @Test
    @DisplayName("unknown and duplicate names, totems without 2 titans and more than 2 are logged and cleaned up")
    void brokenEntriesAreCleanedUp() throws IOException {
        Path file = writeGuildFile("""
                { "titanIds": ["ignis", "vulcan", "nova", "sigurd", "eden", "unknown-titan"],
                  "totems": ["WIND", "WATER", "WATER", "LIGHT", "FIRE", "EARTH"],
                  "totalPower": 1000 }
                """);
        List<String> log = captureLogSince();

        TitanTeam team = GuildRepository.load(file, catalog).members().get(0).titanTeams().get(0);

        assertEquals(List.of(FIRE, WATER), new ArrayList<>(team.totems()));
        assertTrue(logged(log, "Unknown totem", "WIND"), log.toString());
        assertTrue(logged(log, "WATER", "more than once"), log.toString());
        assertTrue(logged(log, "LIGHT", "requires at least"), log.toString());
        assertTrue(logged(log, "EARTH", "requires at least"), log.toString());
    }

    @Test
    @DisplayName("more than 2 valid totems: only the first 2 are kept, the rest is logged")
    void moreThanTwoValidTotems() throws IOException {
        Path file = writeGuildFile("""
                { "titanIds": ["ignis", "vulcan", "nova", "sigurd", "eden", "angus"],
                  "totems": ["EARTH", "WATER", "FIRE"],
                  "totalPower": 1000 }
                """);
        List<String> log = captureLogSince();

        TitanTeam team = GuildRepository.load(file, catalog).members().get(0).titanTeams().get(0);

        assertEquals(Set.of(EARTH, WATER), team.totems());
        assertTrue(logged(log, "More than 2 totems", "FIRE"), log.toString());
    }

    @Test
    @DisplayName("a totem whose titans are no longer in the catalog is dropped")
    void totemLosesItsTitans() throws IOException {
        Path file = writeGuildFile("""
                { "titanIds": ["ignis", "no-longer-in-catalog"], "totems": ["FIRE"], "totalPower": 1000 }
                """);
        List<String> log = captureLogSince();

        TitanTeam team = GuildRepository.load(file, catalog).members().get(0).titanTeams().get(0);

        assertEquals(Set.of(), team.totems());
        assertTrue(logged(log, "FIRE", "requires at least"), log.toString());
    }
}
