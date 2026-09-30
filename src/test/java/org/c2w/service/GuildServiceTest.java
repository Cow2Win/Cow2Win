package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.GuildRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GuildService}: guild folders on disk, the open guild/lineup and the dirty state stay consistent. */
class GuildServiceTest extends ServiceTestSupport {

    @Test
    @DisplayName("createGuild writes guild.json plus a default lineup without opening it")
    void createGuildWritesFiles() {
        guildService.createGuild("Beta");

        Path guildDir = workspace.resolve("Beta");
        assertTrue(Files.isRegularFile(guildDir.resolve(GuildService.GUILD_FILE_NAME)));
        assertTrue(Files.isRegularFile(guildDir.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME)));
        assertTrue(guildService.guildExists("Beta"));
        assertEquals("Alpha", guildService.currentGuildFolderName());
        assertEquals(List.of("Alpha", "Beta"), guildService.listGuildFolderNames());
    }

    @Test
    @DisplayName("switchToGuild opens guild and first lineup, clears both dirty flags and remembers both files")
    void switchToGuildOpensCleanState() throws Exception {
        guildService.createGuild("Beta");
        guildService.markGuildEdited();
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, FortificationRepository.findAll().get(0));

        guildService.switchToGuild("Beta");

        Path guildFile = workspace.resolve("Beta").resolve(GuildService.GUILD_FILE_NAME);
        Path lineupFile = workspace.resolve("Beta").resolve(LineupService.DEFAULT_LINEUP_FILE_NAME);
        assertEquals("Beta", context.guild().name());
        assertEquals(guildFile, context.guildFilePath());
        assertEquals(lineupFile, context.lineupFilePath());
        assertFalse(context.hasUnsavedChanges());
        assertEquals(List.of(guildFile), recentFiles.guilds);
        assertEquals(List.of(lineupFile), recentFiles.lineups);
    }

    @Test
    @DisplayName("switchToGuild creates a default lineup for a guild folder that has none")
    void switchToGuildCreatesMissingLineup() throws Exception {
        Path guildDir = workspace.resolve("Gamma");
        GuildService.createInitialGuildFile("Gamma", guildDir);

        guildService.switchToGuild("Gamma");

        assertEquals(guildDir.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME), context.lineupFilePath());
    }

    @Test
    @DisplayName("an unreadable lineup is reported as LineupLoadException and leaves the open guild untouched")
    void switchToGuildReportsBrokenLineup() throws Exception {
        guildService.createGuild("Beta");
        Files.writeString(workspace.resolve("Beta").resolve(LineupService.DEFAULT_LINEUP_FILE_NAME), "{ not json");
        Path previousGuildFile = context.guildFilePath();

        assertThrows(GuildService.LineupLoadException.class, () -> guildService.switchToGuild("Beta"));
        assertEquals(previousGuildFile, context.guildFilePath());
    }

    @Test
    @DisplayName("deleteGuild removes the folder and guildAfterRemoval picks the neighbour")
    void deleteGuildPicksNeighbour() throws Exception {
        guildService.createGuild("Beta");
        guildService.createGuild("Gamma");
        int index = guildService.listGuildFolderNames().indexOf("Beta");

        guildService.deleteGuild("Beta");

        assertFalse(guildService.guildExists("Beta"));
        assertEquals("Gamma", guildService.guildAfterRemoval(index));
    }

    @Test
    @DisplayName("saveGuild(updated) writes the file, opens the new guild and clears the dirty flag")
    void saveUpdatedGuild() throws Exception {
        guildService.markGuildEdited();
        Guild renamed = new Guild(context.guild().id(), "Alpha renamed", List.of());

        guildService.saveGuild(renamed);

        assertSame(renamed, context.guild());
        assertFalse(context.isGuildDirty());
        assertEquals("Alpha renamed", GuildRepository.load(context.guildFilePath(), context.catalog()).name());
    }

    @Test
    @DisplayName("slugify builds a filesystem-friendly id")
    void slugify() {
        assertEquals("my-guild-1", GuildService.slugify("  My Guild #1 "));
        assertEquals("guild", GuildService.slugify("###"));
    }
}
