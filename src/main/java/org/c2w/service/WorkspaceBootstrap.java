package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.infra.BackupService;
import org.c2w.infra.Config;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything that has to happen before the first window opens: the first-run
 * setup, loading config.properties, the startup backup, loading the
 * catalogs (and handing the hero combos to {@link TeamScoreCalculator}; this also
 * creates the titan team templates' workspace file from the shipped defaults
 * if it is missing, see {@link org.c2w.data.repository.TeamTemplateRepository}) and
 * reopening the last guild/lineup - see {@link #start()}.
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
     *
     * @param progress receives a short, human-readable description of each
     *                 step right before it runs (shown on the splash screen)
     */
    public static AppContext start(Consumer<String> progress) {
        boolean firstStart = !Config.exists();
        if (firstStart) {
            progress.accept("Creating workspace ...");
            runInitialSetup();
        }

        progress.accept("Loading configuration ...");
        Config.load();
        progress.accept("Creating backups ...");
        BackupService.checkAndCreateBackups();
        progress.accept("Loading catalogs ...");
        Catalog catalog = new Catalog(Config.getWorkspaceDir());
        TeamScoreCalculator.setHeroCombos(catalog.heroCombos().combos());
        AppContext context = new AppContext(catalog);
        progress.accept("Opening guild and lineup ...");
        openLastGuildAndLineup(context);

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
     * another" mismatch). Returns null for a blank saved path (nothing ever
     * opened) - the caller falls back to what is in the workspace then.
     */
    private static Path reanchorIntoWorkspace(String savedPath) {
        if (savedPath == null || savedPath.isBlank()) {
            return null;
        }
        Path workspace = Config.getWorkspaceDir();
        Path saved;
        try {
            saved = Paths.get(savedPath);
        } catch (InvalidPathException e) {
            Logger.logException("Ignoring invalid saved path " + savedPath, e);
            return null;
        }
        Path fileName = saved.getFileName();
        Path guildDir = saved.getParent();
        Path guildFolder = guildDir == null ? null : guildDir.getFileName();
        Path base = guildFolder == null ? workspace : workspace.resolve(guildFolder);
        return fileName == null ? base : base.resolve(fileName);
    }

    /**
     * Opens the guild and lineup to start with and remembers both in the
     * config. Takes the re-anchored {@code lastGuildPath}/{@code lastLineUpPath}
     * if they point to existing files; otherwise falls back to the first guild
     * folder in the workspace (creating an empty {@value #DEFAULT_GUILD_NAME}
     * guild if there is none) and the lineup of that guild - see
     * {@link #resolveGuildFile} / {@link #resolveLineupFile}. Only paths of
     * existing files are written to the config, so a broken entry can never
     * get worse from one start to the next.
     */
    static void openLastGuildAndLineup(AppContext context) {
        Path guildFilePath = resolveGuildFile(context);
        Path lineupFilePath = resolveLineupFile(guildFilePath);
        loadGuildContext(context, guildFilePath);
        loadLineupContext(context, lineupFilePath);
    }

    /**
     * The guild file to open: the re-anchored {@code lastGuildPath} if it
     * exists, else guild.json of the first guild folder in the workspace,
     * else that of a newly created, empty {@value #DEFAULT_GUILD_NAME} guild.
     */
    private static Path resolveGuildFile(AppContext context) {
        String saved = Config.getLastGuildPath();
        Path candidate = reanchorIntoWorkspace(saved);
        if (candidate != null && Files.isRegularFile(candidate)) {
            return candidate;
        }

        GuildService guildService = new GuildService(context);
        List<String> folders = guildService.listGuildFolderNames();
        Path fallback;
        if (!folders.isEmpty()) {
            fallback = guildService.guildDir(folders.get(0)).resolve(GuildService.GUILD_FILE_NAME);
        } else {
            Path guildDir = guildService.guildDir(DEFAULT_GUILD_NAME);
            fallback = GuildService.createInitialGuildFile(DEFAULT_GUILD_NAME, guildDir);
            if (!Files.isRegularFile(guildDir.resolve(LineupService.DEFAULT_LINEUP_FILE_NAME))) {
                LineupService.createInitialLineupFile(DEFAULT_GUILD_NAME, guildDir);
            }
        }
        Logger.log("Last guild " + describe(saved, candidate) + " not found, opening " + fallback + " instead");
        return fallback;
    }

    /**
     * The lineup file to open: the re-anchored {@code lastLineUpPath} if it
     * exists (and, if the guild is not the one from the config, belongs to
     * that guild), else the first lineup in the guild's folder, else a newly
     * created default lineup there.
     */
    private static Path resolveLineupFile(Path guildFilePath) {
        Path guildDir = guildFilePath.getParent();
        String saved = Config.getLastLineUpPath();
        Path candidate = reanchorIntoWorkspace(saved);
        boolean guildFromConfig = guildFilePath.equals(reanchorIntoWorkspace(Config.getLastGuildPath()));
        if (candidate != null && Files.isRegularFile(candidate)
                && (guildFromConfig || guildDir.equals(candidate.getParent()))) {
            return candidate;
        }

        List<String> lineupFileNames = LineupService.listLineupFileNames(guildDir);
        Path fallback = lineupFileNames.isEmpty()
                ? LineupService.createInitialLineupFile(guildDir.getFileName().toString(), guildDir)
                : guildDir.resolve(lineupFileNames.get(0));
        Logger.log("Last lineup " + describe(saved, candidate) + " not usable, opening " + fallback + " instead");
        return fallback;
    }

    private static String describe(String saved, Path reanchored) {
        if (saved == null || saved.isBlank()) {
            return "(not set)";
        }
        return reanchored == null ? "\"" + saved + "\"" : reanchored.toString();
    }

    private static void loadGuildContext(AppContext context, Path guildFilePath) {
        Guild guild;
        try {
            guild = GuildRepository.load(guildFilePath, context.catalog());
        } catch (IOException e) {
            Logger.logException("Could not load guild from " + guildFilePath, e);
            guild = new Guild("guild", "", List.of());
        }
        context.set(guild, guildFilePath);
        // Self-heal config so the corrected, workspace-anchored path is what
        // gets persisted the next time config.properties is written - but
        // never replace the saved entry with a path that does not exist.
        if (Files.isRegularFile(guildFilePath)) {
            Config.setLastGuildPath(guildFilePath.toString());
        }
    }

    private static void loadLineupContext(AppContext context, Path lineupFilePath) {
        Lineup lineup;
        try {
            lineup = LineupRepository.load(lineupFilePath);
        } catch (IOException e) {
            Logger.logException("Could not load lineup from " + lineupFilePath, e);
            lineup = LineupRepository.createEmptyLineup();
        }
        context.set(lineup, lineupFilePath);
        if (Files.isRegularFile(lineupFilePath)) {
            Config.setLastLineUpPath(lineupFilePath.toString());
        }
    }
}
