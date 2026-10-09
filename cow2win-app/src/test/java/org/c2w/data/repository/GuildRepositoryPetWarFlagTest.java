package org.c2w.data.repository;

import org.c2w.data.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GuildRepository}'s handling of a hero team's optional pet
 * ("petId") and war flag ("warFlagId"): round trip, guild files saved
 * before both existed, unknown ids and a pet/war flag used twice by the
 * same member.
 */
class GuildRepositoryPetWarFlagTest {

    /** The catalogs seed their CowScore workspace files here instead of the real workspace. */
    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        catalog = new Catalog(workspace);
    }

    private static Path writeGuildFile(Path dir, String heroTeamsJson) throws IOException {
        String json = """
                {
                  "id": "g1",
                  "name": "Guild",
                  "season": 1,
                  "seasonStart": "2026-01-01",
                  "members": [
                    { "id": "m1", "name": "Member", "heroTeams": %s, "titanTeams": [] }
                  ]
                }
                """.formatted(heroTeamsJson);
        Path file = dir.resolve("guild.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("pet and war flag survive a save/load round trip, a team without them stays without")
    void roundTrip() throws IOException {
        Hero galahad = catalog.heroes().findById("galahad").orElseThrow();
        Pet albus = catalog.pets().findById("albus").orElseThrow();
        WarFlag frost = catalog.warFlags().findById("flag-frost").orElseThrow();
        GuildMember member = new GuildMember("m1", "Member", List.of(
                new HeroTeam("m1", 0, List.of(galahad), albus, frost, 1000, LocalDate.of(2026, 9, 29)),
                new HeroTeam("m1", 1, List.of(), null, null, 500, null)), List.of());
        Guild guild = new Guild("g1", "Guild", List.of(member));

        Path file = workspace.resolve("guild.json");
        GuildRepository.save(guild, file);
        Guild loaded = GuildRepository.load(file, catalog);

        HeroTeam withExtras = loaded.members().get(0).heroTeams().get(0);
        assertEquals("albus", withExtras.pet().id());
        assertEquals("flag-frost", withExtras.warFlag().id());
        HeroTeam withoutExtras = loaded.members().get(0).heroTeams().get(1);
        assertNull(withoutExtras.pet());
        assertNull(withoutExtras.warFlag());
    }

    @Test
    @DisplayName("a guild file saved before pets/war flags existed still loads, without either")
    void legacyFileWithoutFields() throws IOException {
        Path file = writeGuildFile(workspace,
                "[ { \"heroIds\": [\"galahad\"], \"totalPower\": 1000, \"lastModified\": null } ]");
        HeroTeam team = GuildRepository.load(file, catalog).members().get(0).heroTeams().get(0);
        assertNull(team.pet());
        assertNull(team.warFlag());
        assertEquals(1000, team.totalPower());
        assertEquals(1, team.heroes().size());
    }

    @Test
    @DisplayName("unknown pet/war flag ids are skipped, the rest of the team still loads")
    void unknownIdsAreSkipped() throws IOException {
        Path file = writeGuildFile(workspace,
                "[ { \"heroIds\": [\"galahad\"], \"petId\": \"no-such-pet\", \"warFlagId\": \"no-such-flag\","
                        + " \"totalPower\": 1000 } ]");
        HeroTeam team = GuildRepository.load(file, catalog).members().get(0).heroTeams().get(0);
        assertNull(team.pet());
        assertNull(team.warFlag());
        assertEquals(1, team.heroes().size());
    }

    @Test
    @DisplayName("a pet/war flag used twice by one member is kept in the first team and dropped from the second")
    void duplicatesAreDroppedFromLaterTeam() throws IOException {
        Path file = writeGuildFile(workspace, """
                [
                  { "heroIds": [], "petId": "albus", "warFlagId": "flag-frost", "totalPower": 1000 },
                  { "heroIds": [], "petId": "albus", "warFlagId": "flag-frost", "totalPower": 900 }
                ]
                """);
        List<HeroTeam> teams = GuildRepository.load(file, catalog).members().get(0).heroTeams();
        assertEquals("albus", teams.get(0).pet().id());
        assertEquals("flag-frost", teams.get(0).warFlag().id());
        assertNull(teams.get(1).pet());
        assertNull(teams.get(1).warFlag());
    }
}
