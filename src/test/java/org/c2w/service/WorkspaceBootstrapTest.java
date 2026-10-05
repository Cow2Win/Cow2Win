package org.c2w.service;

import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkspaceBootstrap#openLastGuildAndLineup}: valid saved paths are
 * kept, empty or broken ones fall back to what is in the workspace, and only
 * paths of existing files end up in the config (in memory only -
 * config.properties is never written).
 */
class WorkspaceBootstrapTest {

    @TempDir
    Path workspace;

    @TempDir
    Path otherWorkspace;

    private String previousWorkspace;
    private String previousGuildPath;
    private String previousLineupPath;
    private AppContext context;

    @BeforeEach
    void setUp() {
        previousWorkspace = Config.getWorkspacePath();
        previousGuildPath = Config.getLastGuildPath();
        previousLineupPath = Config.getLastLineUpPath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
    }

    @AfterEach
    void tearDown() {
        Config.setWorkspacePath(previousWorkspace);
        Config.setLastGuildPath(previousGuildPath);
        Config.setLastLineUpPath(previousLineupPath);
    }

    private Path createGuild(String name) {
        Path guildDir = workspace.resolve(name);
        GuildService.createInitialGuildFile(name, guildDir);
        LineupService.createInitialLineupFile(name, guildDir);
        return guildDir;
    }

    private static Path createLineup(Path guildDir, String fileName) throws Exception {
        Path file = guildDir.resolve(fileName);
        LineupRepository.save(new Lineup("x", "x", "", LocalDateTime.now(), List.of()), file);
        return file;
    }

    private void assertOpened(Path guildFile, Path lineupFile) {
        assertEquals(guildFile, context.guildFilePath());
        assertEquals(lineupFile, context.lineupFilePath());
        assertEquals(guildFile.toString(), Config.getLastGuildPath());
        assertEquals(lineupFile.toString(), Config.getLastLineUpPath());
    }

    @Test
    @DisplayName("valid saved paths are opened as they are")
    void validPathsAreKept() throws Exception {
        createGuild("Alpha");
        Path beta = createGuild("Beta");
        Path lineup = createLineup(beta, "war.lineup");
        Config.setLastGuildPath(beta.resolve(GuildService.GUILD_FILE_NAME).toString());
        Config.setLastLineUpPath(lineup.toString());

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(beta.resolve(GuildService.GUILD_FILE_NAME), lineup);
        assertEquals("Beta", context.guild().name());
    }

    @Test
    @DisplayName("paths saved under another workspace location are re-anchored into the current one")
    void pathsFromOtherWorkspaceAreReanchored() throws Exception {
        Path beta = createGuild("Beta");
        Path lineup = createLineup(beta, "war.lineup");
        Config.setLastGuildPath(otherWorkspace.resolve("Beta").resolve(GuildService.GUILD_FILE_NAME).toString());
        Config.setLastLineUpPath(otherWorkspace.resolve("Beta").resolve("war.lineup").toString());

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(beta.resolve(GuildService.GUILD_FILE_NAME), lineup);
    }

    @Test
    @DisplayName("empty saved paths fall back to the first guild and its first lineup")
    void emptyPathsFallBackToFirstGuild() throws Exception {
        Path alpha = createGuild("Alpha");
        createGuild("Beta");
        Path aLineup = createLineup(alpha, "a.lineup");
        Config.setLastGuildPath("");
        Config.setLastLineUpPath("");

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(alpha.resolve(GuildService.GUILD_FILE_NAME), aLineup);
        assertEquals("Alpha", context.guild().name());
    }

    @Test
    @DisplayName("the broken doubled workspace path written by older versions is repaired")
    void doubledWorkspacePathIsRepaired() {
        Path alpha = createGuild("Alpha");
        Path broken = workspace.resolve(".cow2Win").resolve("workspace");
        Config.setLastGuildPath(broken.toString());
        Config.setLastLineUpPath(broken.toString());

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(alpha.resolve(GuildService.GUILD_FILE_NAME),
                alpha.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME));
    }

    @Test
    @DisplayName("a missing lineup falls back to a lineup of the opened guild")
    void missingLineupFallsBackWithinGuild() {
        createGuild("Alpha");
        Path beta = createGuild("Beta");
        Config.setLastGuildPath(beta.resolve(GuildService.GUILD_FILE_NAME).toString());
        Config.setLastLineUpPath(beta.resolve("gone.lineup").toString());

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(beta.resolve(GuildService.GUILD_FILE_NAME),
                beta.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME));
    }

    @Test
    @DisplayName("a guild fallback does not combine the new guild with a lineup of another guild")
    void guildFallbackIgnoresForeignLineup() throws Exception {
        Path alpha = createGuild("Alpha");
        Path beta = createGuild("Beta");
        Path foreign = createLineup(beta, "war.lineup");
        Config.setLastGuildPath(workspace.resolve("Gone").resolve(GuildService.GUILD_FILE_NAME).toString());
        Config.setLastLineUpPath(foreign.toString());

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(alpha.resolve(GuildService.GUILD_FILE_NAME),
                alpha.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME));
    }

    @Test
    @DisplayName("a guild folder without lineups gets a default lineup")
    void guildWithoutLineupGetsDefault() {
        Path alpha = workspace.resolve("Alpha");
        GuildService.createInitialGuildFile("Alpha", alpha);
        Config.setLastGuildPath("");
        Config.setLastLineUpPath("");

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        Path lineup = alpha.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME);
        assertTrue(Files.isRegularFile(lineup));
        assertOpened(alpha.resolve(GuildService.GUILD_FILE_NAME), lineup);
    }

    @Test
    @DisplayName("an empty workspace gets a new, empty default guild instead of a broken path")
    void emptyWorkspaceGetsDefaultGuild() {
        Config.setLastGuildPath("");
        Config.setLastLineUpPath("");

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        Path guildFile = workspace.resolve("Demo").resolve(GuildService.GUILD_FILE_NAME);
        Path lineupFile = workspace.resolve("Demo").resolve(LineupService.DEFAULT_LINEUP_FILE_NAME);
        assertTrue(Files.isRegularFile(guildFile));
        assertTrue(Files.isRegularFile(lineupFile));
        assertOpened(guildFile, lineupFile);
    }

    @Test
    @DisplayName("an existing but unreadable guild file is still remembered and an empty guild is opened")
    void corruptGuildKeepsPath() throws Exception {
        Path alpha = createGuild("Alpha");
        Path guildFile = alpha.resolve(GuildService.GUILD_FILE_NAME);
        Files.writeString(guildFile, "{ not json");
        Config.setLastGuildPath(guildFile.toString());
        Config.setLastLineUpPath(alpha.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME).toString());

        WorkspaceBootstrap.openLastGuildAndLineup(context);

        assertOpened(guildFile, alpha.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME));
        assertNotEquals("Alpha", context.guild().name());
    }
}
