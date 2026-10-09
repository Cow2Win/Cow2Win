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

/** The guild master flag of a guild in the guild file - "guildMaster": true, only written if set. */
class GuildRepositoryGuildMasterTest {

    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        catalog = new Catalog(workspace);
    }

    @Test
    @DisplayName("true is written as \"guildMaster\": true and survives saving and loading")
    void trueRoundTrip() throws IOException {
        Path file = workspace.resolve("guild.json");
        Guild guild = new Guild("g1", "Guild", List.of(new GuildMember("m1", "Member", List.of(), List.of())),
                193861L, true);

        GuildRepository.save(guild, file);

        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"guildMaster\": true"));
        Guild loaded = GuildRepository.load(file, catalog);
        assertTrue(loaded.guildMaster());
        assertEquals(guild, loaded);
    }

    @Test
    @DisplayName("false is not written, and loads as false")
    void falseNotWritten() throws IOException {
        Path file = workspace.resolve("guild.json");

        GuildRepository.save(new Guild("g1", "Guild", List.of()), file);

        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("guildMaster"));
        assertFalse(GuildRepository.load(file, catalog).guildMaster());
    }

    @Test
    @DisplayName("an old guild file without the field loads as false; a name longer than 20 characters still loads")
    void oldFile() throws IOException {
        Path file = workspace.resolve("guild.json");
        Files.writeString(file, """
                { "id": "g1", "name": "A guild name longer than twenty characters", "members": [] }
                """, StandardCharsets.UTF_8);

        Guild loaded = GuildRepository.load(file, catalog);

        assertFalse(loaded.guildMaster());
        assertEquals("A guild name longer than twenty characters", loaded.name());
    }
}
