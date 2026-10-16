package org.c2w.service;

import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.Config;
import org.c2w.infra.JsonSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkspaceBootstrap#start} on the very first start (no config file yet) in a temp
 * workspace: the "Demo" guild is created from the bundled demo data, including its live
 * lineup and its Clash of Worlds journal with one battle, and has no unsaved changes.
 */
class WorkspaceBootstrapTest {

    @TempDir
    Path temp;

    private String previousConfigFile;
    private AppContext context;

    @BeforeEach
    void freshInstallation() {
        previousConfigFile = System.getProperty(Config.CONFIG_FILE_PROPERTY);
        System.setProperty(Config.CONFIG_FILE_PROPERTY, temp.resolve("config.properties").toString());
        Config.load();
        Config.setWorkspacePath(temp.resolve("workspace").toString());
        Config.setBackupDir(temp.resolve("backup").toString());
    }

    @AfterEach
    void restoreConfig() {
        if (context != null) {
            context.journal().closeCurrent();
        }
        System.setProperty(Config.CONFIG_FILE_PROPERTY, previousConfigFile);
        Config.load();
    }

    @Test
    @DisplayName("first start opens the demo guild: guild master, all bundled members, game guild id 1, saved")
    void firstStartOpensDemoGuild() throws Exception {
        context = WorkspaceBootstrap.start(step -> {
        });

        Guild guild = context.guild();
        Guild bundled = GuildRepository.load(JsonSupport.resolveDataFile("data", "guild.json"), context.catalog());
        assertEquals("Demo", guild.name());
        assertTrue(guild.guildMaster());
        assertEquals(bundled.members().size(), guild.members().size());
        assertEquals(1L, guild.gameGuildId());
        assertTrue(context.isJournalAvailable());
        assertFalse(context.isGuildDirty(), "demo guild must have no unsaved changes");
        assertFalse(context.isLineupDirty(), "demo lineup must have no unsaved changes");
        assertFalse(context.lineup().entries().isEmpty());
    }

    @Test
    @DisplayName("first start saves the demo live lineup as the Original lineup, default.lineup stays open")
    void firstStartSavesDemoLiveLineup() throws Exception {
        context = WorkspaceBootstrap.start(step -> {
        });

        Path guildDir = temp.resolve("workspace").resolve("Demo");
        Path original = LineupFiles.originalPathFor(guildDir);
        assertTrue(Files.isRegularFile(original));
        Lineup live = LineupRepository.load(original);
        assertEquals("demo", live.guildId());
        assertFalse(live.entries().isEmpty());
        assertFalse(LineupFiles.isOriginal(context.lineupFilePath()), "default.lineup stays the open lineup");
        assertFalse(context.isGuildDirty());
    }

    @Test
    @DisplayName("first start imports the demo journal with exactly one battle and leaves it closed")
    void firstStartImportsDemoJournal() throws Exception {
        context = WorkspaceBootstrap.start(step -> {
        });

        Path guildDir = temp.resolve("workspace").resolve("Demo");
        assertTrue(Files.isRegularFile(guildDir.resolve("journal.mv.db")));
        assertFalse(context.journal().isOpen(), "the import must not leave the journal open");

        JournalRepository repo = context.journal().repository(false).orElseThrow();
        assertEquals(1, repo.listBattles().size());
        assertFalse(context.isGuildDirty(), "demo guild must have no unsaved changes");
        assertEquals(1L, repo.ownGameGuildId().orElseThrow());
    }

    @Test
    @DisplayName("a later start does not import the demo journal again")
    void laterStartDoesNotImportAgain() throws Exception {
        context = WorkspaceBootstrap.start(step -> {
        });
        context.journal().closeCurrent();

        context = WorkspaceBootstrap.start(step -> {
        });

        assertEquals(1, context.journal().repository(false).orElseThrow().listBattles().size());
        assertFalse(context.isGuildDirty());
    }
}
