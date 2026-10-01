package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.BackupService;
import org.c2w.infra.Config;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Everything that has to happen before the first window opens: the first-run
 * setup, loading config.properties, the startup backup, loading the
 * catalogs and reopening the last guild/lineup - see {@link #start()}.
 */
public final class WorkspaceBootstrap {

    private static final String DEFAULT_GUILD_NAME = "Demo";
    // See JsonSupport#resolveDataFile for how these resolve in the IDE vs. the packaged app.
    private static final Path DEMO_GUILD_PATH = JsonSupport.resolveDataFile("data", "guild.json");
    private static final Path DEMO_GUILD_LINEUP = JsonSupport.resolveDataFile("data", "default.lineup");

    private WorkspaceBootstrap() {
        // Utility class, no instantiation
    }

    /**
     * Runs the first-run setup if there is no config.properties yet, loads
     * the config, creates the due workspace backups and returns a context
     * with the catalogs loaded and the last guild/lineup open (the demo
     * guild on the very first start). Never throws for a missing or broken
     * guild/lineup file - that is logged and an empty one is opened instead.
     */
    public static AppContext start() {
        boolean firstStart = !Config.exists();
        if (firstStart) {
            runInitialSetup();
        }

        Config.load();
        BackupService.checkAndCreateBackups();
        AppContext context = new AppContext(new Catalog(Config.getWorkspaceDir()));
        loadGuildContext(context);
        loadLineupContext(context);

        if (firstStart) {
            loadDemo(context);
        }
        return context;
    }

    /**
     * On the very first start: creates the "Demo" guild folder along with
     * guild.json and a matching default.lineup, and writes
     * language/lastGuildPath/lastLineUpPath to a new config.properties.
     */
    private static void runInitialSetup() {
        Path guildDir = Config.getWorkspaceDir().resolve(DEFAULT_GUILD_NAME);
        Path guildFilePath = GuildService.createInitialGuildFile(DEFAULT_GUILD_NAME, guildDir);
        Path lineupFilePath = LineupService.createInitialLineupFile(DEFAULT_GUILD_NAME, guildDir);

        Config.setLanguage("english");
        Config.setLastGuildPath(guildFilePath.toString());
        Config.setLastLineUpPath(lineupFilePath.toString());
        Config.save();
    }

    /**
     * Overwrites the freshly-created (still empty) first-run guild/lineup
     * files with the bundled demo data from {@code src/main/resources/data}
     * (guild.json / default.lineup), so the "Demo" guild created by
     * {@link #runInitialSetup()} shows up pre-filled instead of empty.
     *
     * <p>Guild and lineup are loaded/saved independently and any failure is
     * logged rather than thrown - a problem with the bundled demo data
     * (missing file, malformed JSON, ...) must not prevent the application
     * from starting up (same pattern as {@link #loadGuildContext} /
     * {@link #loadLineupContext}); the just-created empty guild/lineup
     * remain in place as a safe fallback for whichever half failed.
     */
    private static void loadDemo(AppContext context) {
        try {
            Guild demoGuild = GuildRepository.load(DEMO_GUILD_PATH, context.catalog());
            GuildRepository.save(demoGuild, context.guildFilePath());
            context.setGuild(demoGuild);
        } catch (IOException e) {
            Logger.logException("Could not load demo guild from " + DEMO_GUILD_PATH, e);
        }

        try {
            Lineup demoLineup = LineupRepository.load(DEMO_GUILD_LINEUP);
            LineupRepository.save(demoLineup, context.lineupFilePath());
            context.set(demoLineup, context.lineupFilePath());
        } catch (IOException e) {
            Logger.logException("Could not load demo lineup from " + DEMO_GUILD_LINEUP, e);
        }
    }
    /**
     * Re-anchors a saved (typically absolute) guild/lineup path from
     * config.properties into the currently configured workspace
     * ({@link Config#getWorkspaceDir()}): it keeps only the guild-folder name
     * and the file name and resolves them under the configured workspace. This
     * makes the configured {@code workspacePath} the single source of truth on
     * startup, so a {@code lastGuildPath}/{@code lastLineUpPath} that still
     * points at a previous workspace no longer silently pulls the app back to
     * that old location (the "config says one workspace but the app really uses
     * another" mismatch). A blank saved path (nothing ever opened) resolves to
     * the workspace directory itself.
     */
    private static Path reanchorIntoWorkspace(String savedPath) {
        Path workspace = Config.getWorkspaceDir();
        if (savedPath == null || savedPath.isBlank()) {
            return workspace;
        }
        Path saved = Paths.get(savedPath);
        Path fileName = saved.getFileName();
        Path guildDir = saved.getParent();
        Path guildFolder = guildDir == null ? null : guildDir.getFileName();
        Path base = guildFolder == null ? workspace : workspace.resolve(guildFolder);
        return fileName == null ? base : base.resolve(fileName);
    }

    private static void loadGuildContext(AppContext context) {
        Path guildFilePath = reanchorIntoWorkspace(Config.getLastGuildPath());
        Guild guild;
        try {
            guild = GuildRepository.load(guildFilePath, context.catalog());
        } catch (IOException e) {
            Logger.logException("Could not load guild from " + guildFilePath, e);
            guild = new Guild("guild", "", List.of());
        }
        context.set(guild, guildFilePath);
        // Self-heal config so the corrected, workspace-anchored path is what
        // gets persisted the next time config.properties is written.
        Config.setLastGuildPath(guildFilePath.toString());
    }

    private static void loadLineupContext(AppContext context) {
        Path lineupFilePath = reanchorIntoWorkspace(Config.getLastLineUpPath());
        Lineup lineup;
        try {
            lineup = LineupRepository.load(lineupFilePath);
        } catch (IOException e) {
            Logger.logException("Could not load lineup from " + lineupFilePath, e);
            lineup = LineupRepository.createEmptyLineup();
        }
        context.set(lineup, lineupFilePath);
        Config.setLastLineUpPath(lineupFilePath.toString());
    }
}
