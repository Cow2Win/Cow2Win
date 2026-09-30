package org.c2w.service;

import org.c2w.data.repository.Catalog;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared setup for the service tests: points the workspace at a temp folder
 * (in memory only - config.properties is never written, see
 * {@link RecentFiles}) and opens a guild "Alpha" there.
 */
abstract class ServiceTestSupport {

    /** {@link RecentFiles} that only records which files were reported as opened. */
    static final class RecordingRecentFiles implements RecentFiles {
        final List<Path> guilds = new ArrayList<>();
        final List<Path> lineups = new ArrayList<>();

        @Override
        public void guildOpened(Path guildFilePath) {
            guilds.add(guildFilePath);
        }

        @Override
        public void lineupOpened(Path lineupFilePath) {
            lineups.add(lineupFilePath);
        }
    }

    @TempDir
    Path workspace;

    private String previousWorkspace;

    AppContext context;
    final RecordingRecentFiles recentFiles = new RecordingRecentFiles();
    GuildService guildService;
    LineupService lineupService;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, recentFiles);
        lineupService = new LineupService(context, recentFiles);

        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        recentFiles.guilds.clear();
        recentFiles.lineups.clear();
    }

    @AfterEach
    void restoreWorkspace() {
        Config.setWorkspacePath(previousWorkspace);
    }
}
