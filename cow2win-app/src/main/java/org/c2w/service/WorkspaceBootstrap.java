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
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.infra.WorkspaceMaintenance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Everything that has to happen before the first window opens: the first-run
 * setup, loading config.properties, a restore or move of the workspace scheduled
 * in the settings (see {@link WorkspaceMaintenance}), the startup backup, loading the
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

    /** Date and time of a restored backup in the notice after the start. */
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** A message for the user once the main window is open - see {@link #startupNotices()}. */
    public record StartupNotice(String text, boolean warning) {
    }

    /** The notices of the last {@link #start}. */
    private static List<StartupNotice> startupNotices = List.of();

    private WorkspaceBootstrap() {
        // Utility class, no instantiation
    }

    /**
     * Messages from the last {@link #start} to show once the main window is open (not on the
     * splash screen) - e.g. that the workspace was restored from a backup.
     */
    public static List<StartupNotice> startupNotices() {
        return startupNotices;
    }

    /** The message for the user about a restore or move run at startup. */
    static StartupNotice noticeFor(WorkspaceMaintenance.Outcome outcome) {
        return switch (outcome) {
            case WorkspaceMaintenance.Restored r -> new StartupNotice(LanguageService.displayName(
                    "workspaceMaintenance.restore.done", formatBackupTime(r.backupTime())), false);
            case WorkspaceMaintenance.RestoreFailed f -> new StartupNotice(LanguageService.displayName(
                    "workspaceMaintenance.restore.failed", f.zip().toString()), true);
            case WorkspaceMaintenance.Moved m -> m.oldWorkspaceRemoved()
                    ? new StartupNotice(LanguageService.displayName("workspaceMaintenance.move.done", m.to().toString()), false)
                    : new StartupNotice(LanguageService.displayName("workspaceMaintenance.move.done", m.to().toString())
                    + "\n" + LanguageService.displayName("workspaceMaintenance.move.oldNotDeleted", m.from().toString()), true);
            case WorkspaceMaintenance.MoveFailed f -> new StartupNotice(f.problem() != null
                    ? LanguageService.displayName("workspaceMaintenance.move.rejected", f.from().toString())
                    + "\n" + moveProblemText(f.problem(), f.from(), f.to())
                    : LanguageService.displayName("workspaceMaintenance.move.failed", f.from().toString(), f.to().toString()), true);
        };
    }

    /** "2026-10-06 19:12" - the time a backup ZIP was written. */
    public static String formatBackupTime(FileTime time) {
        return BACKUP_TIME_FORMAT.format(time.toInstant().atZone(ZoneId.systemDefault()));
    }

    /** Why the workspace cannot be moved from {@code from} to {@code to}, in the user's language. */
    public static String moveProblemText(WorkspaceMaintenance.MoveProblem problem, Path from, Path to) {
        Path parent = to.toAbsolutePath().normalize().getParent();
        return LanguageService.displayName("workspaceMaintenance.move.problem." + problem.name(),
                from.toString(), to.toString(), parent == null ? "" : parent.toString());
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
        return start(progress, () -> {
        });
    }

    /**
     * Like {@link #start(Consumer)}; {@code configLoaded} runs right after the
     * configuration is loaded, before anything else is logged - e.g. to write the
     * start block of the technical log into the configured workspace.
     */
    public static AppContext start(Consumer<String> progress, Runnable configLoaded) {
        boolean firstStart = !Config.exists();
        if (firstStart) {
            progress.accept("Creating workspace ...");
            runInitialSetup();
        }

        progress.accept("Loading configuration ...");
        Config.load();
        // A restore or move scheduled in the settings runs first - before backups and journal
        // databases. Its log entries are written after the start block (a move: into the new log).
        Logger.holdEntries();
        Optional<WorkspaceMaintenance.Outcome> maintenance = Optional.empty();
        try {
            maintenance = WorkspaceMaintenance.runPendingOperation(progress);
        } catch (RuntimeException e) {
            Logger.logException("Pending workspace operation failed", e);
        } finally {
            List<String> heldEntries = Logger.releaseHeldEntries();
            configLoaded.run();
            Logger.writeEntries(heldEntries);
        }
        startupNotices = maintenance.map(WorkspaceBootstrap::noticeFor).map(List::of).orElse(List.of());
        // The CowScore bonus percentages of the settings - before anything is scored.
        TeamScoreCalculator.setBonuses(Config.getCowScoreBonuses());
        progress.accept("Creating backups ...");
        BackupService.checkAndCreateBackups();
        progress.accept("Loading catalogs ...");
        Catalog catalog = new Catalog(Config.getWorkspaceDir());
        TeamScoreCalculator.setHeroCombos(catalog.heroCombos().combos());
        TeamScoreCalculator.setTitanCombos(catalog.titanCombos().combos());
        AppContext context = new AppContext(catalog);
        progress.accept("Opening guild and lineup ...");
        boolean guildLoaded = loadGuildContext(context);
        loadLineupContext(context);

        if (firstStart) {
            loadDemo(context);
        }
        if (guildLoaded) {
            Path guildDir = context.guildFilePath().getParent();
            if (firstStart) {
                GuildLog.event(guildDir, "guildLog.guildCreated", DEFAULT_GUILD_NAME, 0);
            }
            GuildLog.event(guildDir, "guildLog.guildOpened");
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
        Path guildFilePath = GuildService.createInitialGuildFile(DEFAULT_GUILD_NAME, guildDir, false);
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

    /** Opens the last guild (an empty one if it cannot be loaded); true if it was loaded. */
    private static boolean loadGuildContext(AppContext context) {
        Path guildFilePath = reanchorIntoWorkspace(Config.getLastGuildPath());
        Guild guild;
        boolean loaded;
        try {
            guild = GuildRepository.load(guildFilePath, context.catalog());
            loaded = true;
        } catch (IOException e) {
            Logger.logException("Could not load guild from " + guildFilePath, e);
            guild = new Guild("guild", "", List.of());
            loaded = false;
        }
        context.set(guild, guildFilePath);
        // Self-heal config so the corrected, workspace-anchored path is what
        // gets persisted the next time config.properties is written.
        Config.setLastGuildPath(guildFilePath.toString());
        return loaded;
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
