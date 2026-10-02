package org.c2w.data.repository;

import org.c2w.data.model.Guild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GuildRepository}'s handling of the removed "season"/"seasonStart"
 * fields: older guild files still containing them load without error, and
 * the fields disappear on the next save.
 */
class GuildRepositorySeasonLegacyTest {

    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        catalog = new Catalog(workspace);
    }

    private Path writeGuildFile(String seasonFields) throws IOException {
        String json = """
                {
                  "id": "g1",
                  "name": "Guild",
                  %s
                  "members": [
                    { "id": "m1", "name": "Member", "heroTeams": [], "titanTeams": [] }
                  ]
                }
                """.formatted(seasonFields);
        Path file = workspace.resolve("guild.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("a guild file with season/seasonStart loads, and both fields are gone after saving")
    void legacySeasonFieldsAreDroppedOnSave() throws IOException {
        Path file = writeGuildFile("""
                "season": 3,
                "seasonStart": "2026-01-01",
                """);

        Guild guild = GuildRepository.load(file, catalog);
        assertEquals("g1", guild.id());
        assertEquals(1, guild.members().size());

        GuildRepository.save(guild, file);
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(json.contains("\"season\""), json);
        assertFalse(json.contains("\"seasonStart\""), json);
        assertEquals(guild, GuildRepository.load(file, catalog));
    }

    @Test
    @DisplayName("a guild file without season/seasonStart loads as before")
    void fileWithoutSeasonFields() throws IOException {
        Guild guild = GuildRepository.load(writeGuildFile(""), catalog);
        assertEquals("g1", guild.id());
        assertEquals("Guild", guild.name());
        assertEquals("m1", guild.members().get(0).id());
    }
}
