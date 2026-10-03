package org.c2w.data.repository;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The optional game guild id of a guild (Weltenschlacht journal) in the guild file. */
class GuildRepositoryGameGuildIdTest {

    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        catalog = new Catalog(workspace);
    }

    @Test
    @DisplayName("gameGuildId survives saving and loading")
    void roundTrip() throws IOException {
        Path file = workspace.resolve("guild.json");
        Guild guild = new Guild("g1", "Guild", List.of(new GuildMember("m1", "Member", List.of(), List.of())), 193861L);

        GuildRepository.save(guild, file);
        Guild loaded = GuildRepository.load(file, catalog);

        assertEquals(193861L, loaded.gameGuildId());
        assertEquals(guild, loaded);
        assertEquals(new GuildRepository.GuildFileSummary("Guild", 193861L), GuildRepository.readSummary(file));
    }

    @Test
    @DisplayName("an old guild file without gameGuildId loads with null, and null is not written")
    void missingFieldIsNull() throws IOException {
        Path file = workspace.resolve("guild.json");
        Files.writeString(file, """
                { "id": "g1", "name": "Guild", "members": [] }
                """, StandardCharsets.UTF_8);

        Guild loaded = GuildRepository.load(file, catalog);
        assertNull(loaded.gameGuildId());
        assertNull(GuildRepository.readSummary(file).gameGuildId());

        GuildRepository.save(loaded, file);
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("gameGuildId"));
    }

    @Test
    @DisplayName("withMembers keeps id, name and gameGuildId")
    void withMembersKeepsGameGuildId() {
        Guild guild = new Guild("g1", "Guild", List.of(), 42L);

        Guild changed = guild.withMembers(List.of(new GuildMember("m1", "Member", List.of(), List.of())));

        assertEquals(42L, changed.gameGuildId());
        assertEquals("Guild", changed.name());
        assertEquals(1, changed.members().size());
        assertEquals(7L, guild.withGameGuildId(7L).gameGuildId());
    }
}
