package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.GuildRepository;
import org.c2w.i18n.LanguageService;
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
        guildService.createGuild("Beta", false);

        Path guildDir = workspace.resolve("Beta");
        assertTrue(Files.isRegularFile(guildDir.resolve(GuildService.GUILD_FILE_NAME)));
        assertTrue(Files.isRegularFile(guildDir.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME)));
        assertTrue(guildService.guildExists("Beta"));
        assertEquals("Alpha", guildService.currentGuildFolderName());
        assertEquals(List.of("Alpha", "Beta"), guildService.listGuildFolderNames());
    }

    @Test
    @DisplayName("createGuild stores the guild master flag in guild.json, logs it, and the opened guild reports it")
    void createGuildStoresGuildMaster() throws Exception {
        guildService.createGuild("Master", true);
        guildService.createGuild("Member", false);

        assertTrue(GuildRepository.load(workspace.resolve("Master").resolve(GuildService.GUILD_FILE_NAME),
                context.catalog()).guildMaster());
        assertFalse(GuildRepository.load(workspace.resolve("Member").resolve(GuildService.GUILD_FILE_NAME),
                context.catalog()).guildMaster());
        assertEquals(LanguageService.displayName("guildLog.guildCreated", "Master", 1),
                GuildLog.read(workspace.resolve("Master")).get(0).substring(21));

        assertFalse(context.isGuildMaster());
        guildService.switchToGuild("Master");
        assertTrue(context.isGuildMaster());
        guildService.saveGuild();
        assertTrue(GuildRepository.load(context.guildFilePath(), context.catalog()).guildMaster(), "kept on saving");
    }

    @Test
    @DisplayName("switchToGuild opens guild and first lineup, clears both dirty flags and remembers both files")
    void switchToGuildOpensCleanState() throws Exception {
        guildService.createGuild("Beta", false);
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
        GuildService.createInitialGuildFile("Gamma", guildDir, false);

        guildService.switchToGuild("Gamma");

        assertEquals(guildDir.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME), context.lineupFilePath());
    }

    @Test
    @DisplayName("an unreadable lineup is reported as LineupLoadException and leaves the open guild untouched")
    void switchToGuildReportsBrokenLineup() throws Exception {
        guildService.createGuild("Beta", false);
        Files.writeString(workspace.resolve("Beta").resolve(LineupService.DEFAULT_LINEUP_FILE_NAME), "{ not json");
        Path previousGuildFile = context.guildFilePath();

        assertThrows(GuildService.LineupLoadException.class, () -> guildService.switchToGuild("Beta"));
        assertEquals(previousGuildFile, context.guildFilePath());
    }

    @Test
    @DisplayName("deleteGuild removes the folder and guildAfterRemoval picks the neighbour")
    void deleteGuildPicksNeighbour() throws Exception {
        guildService.createGuild("Beta", false);
        guildService.createGuild("Gamma", false);
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

    // --- guild log ---

    /** The guild log lines of a guild folder without the date/time prefix. */
    private List<String> guildLogTexts(String folder) {
        return GuildLog.read(workspace.resolve(folder)).stream().map(line -> line.substring(21)).toList();
    }

    @Test
    @DisplayName("creating and opening a guild write 'created' and 'opened' into that guild's log")
    void createAndSwitchAreLogged() throws Exception {
        guildService.createGuild("Beta", false);
        guildService.switchToGuild("Beta");

        assertEquals(List.of(LanguageService.displayName("guildLog.guildCreated", "Beta", 0),
                LanguageService.displayName("guildLog.guildOpened")), guildLogTexts("Beta"));
    }

    @Test
    @DisplayName("saving the guild writes exactly one 'guild saved' entry, with its origin if given")
    void saveGuildIsLoggedOnce() throws Exception {
        int before = guildLogTexts("Alpha").size();

        guildService.saveGuild();
        guildService.saveGuild(context.guild(), GuildService.SaveOrigin.GUILD_EDITOR);

        List<String> texts = guildLogTexts("Alpha");
        assertEquals(before + 2, texts.size());
        assertEquals(LanguageService.displayName("guildLog.guildSaved"), texts.get(before));
        assertEquals(LanguageService.displayName("guildLog.guildSavedFrom",
                LanguageService.displayName("guildLog.origin.guildEditor")), texts.get(before + 1));
    }

    @Test
    @DisplayName("a failing guild save writes no guild log entry")
    void failedSaveIsNotLogged() throws Exception {
        int before = guildLogTexts("Alpha").size();
        Files.delete(context.guildFilePath());
        Files.createDirectory(context.guildFilePath()); // cannot be written as a file

        assertThrows(Exception.class, () -> guildService.saveGuild());

        assertEquals(before, guildLogTexts("Alpha").size());
    }

    @Test
    @DisplayName("slugify builds a filesystem-friendly id")
    void slugify() {
        assertEquals("my-guild-1", GuildService.slugify("  My Guild #1 "));
        assertEquals("guild", GuildService.slugify("###"));
    }
}
