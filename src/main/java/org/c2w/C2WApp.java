package org.c2w;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.Cow2Frame;
import org.c2w.util.AppContext;
import org.c2w.util.BackupService;
import org.c2w.util.CatalogVersion;
import org.c2w.util.Config;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;
import org.c2w.util.Workspace;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

public class C2WApp {

    private static final String DEFAULT_GUILD_NAME = "Demo";
    public static final String GUILD_FILE_NAME = "guild.json";
    public static final String LINEUP_FILE_NAME = "default.lineup";
    // See JsonSupport#resolveDataFile for how these resolve in the IDE vs. the packaged app.
    private static final Path DEMO_GUILD_PATH = JsonSupport.resolveDataFile("data", "guild.json");
    private static final Path DEMO_GUILD_LINEUP = JsonSupport.resolveDataFile("data", "default.lineup");
    public static final String BASE_TITLE = "Cow2Win";

    public static void main(String[] args) {
        installUncaughtExceptionLogging();

        boolean first = false;
        if (first = !Config.exists()) {
            runInitialSetup();
        }

        Config.load();
        BackupService.checkAndCreateBackups();
        logCatalogVersion();
        AppContext context = new AppContext();
        loadGuildContext(context);
        loadLineupContext(context);

        if(first){
            loadDemo(context);
        }

        Logger.log("Started: ");

        SwingUtilities.invokeLater(() -> new Cow2Frame(context));
    }

    /**
     * Makes sure that literally every exception reaches {@link Logger}
     * (LogPanel + the persistent log file), even ones that are not already
     * wrapped in a {@code try}/{@code catch} that itself calls
     * {@link Logger#logException} - the last line of defense for "a) alle
     * Exceptions" from the log-file todo item.
     *
     * <p>Two handlers are needed because Swing does not use the JVM-wide
     * uncaught-exception handler for exceptions thrown out of an event
     * listener (a button click, a table edit, ...): those are caught
     * internally by {@code java.awt.EventDispatchThread} and only reach a
     * custom handler that is registered via the "sun.awt.exception.handler"
     * system property - an old but still-supported hook, present in every
     * mainstream OpenJDK build, for exactly this purpose. Any other thread
     * (this one, background tasks, ...) is covered by the ordinary
     * {@link Thread#setDefaultUncaughtExceptionHandler}.
     */
    private static void installUncaughtExceptionLogging() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                Logger.logException("Uncaught exception on thread '" + thread.getName() + "'", throwable));
        System.setProperty("sun.awt.exception.handler", SwingUncaughtExceptionHandler.class.getName());
    }

    /**
     * Registered via the "sun.awt.exception.handler" system property (see
     * {@link #installUncaughtExceptionLogging()}) - Swing's
     * {@code EventDispatchThread} instantiates this reflectively (hence the
     * required public no-arg constructor) and calls {@link #handle} for
     * every exception that escapes an event listener uncaught.
     */
    public static final class SwingUncaughtExceptionHandler {
        public void handle(Throwable throwable) {
            Logger.logException("Uncaught exception on the Swing event thread", throwable);
        }
    }

    /**
     * Asks for language and guild name via a modal dialog on the very first
     * start, creates the guild folder along with guild.json and a matching
     * default.lineup, and writes language/lastGuildPath/lastLineUpPath to a
     * new config.properties.
     */
    private static void runInitialSetup()  {

        Path guildDir = Workspace.DIR.resolve(DEFAULT_GUILD_NAME);
        Path guildFilePath = createInitialGuildFile(DEFAULT_GUILD_NAME, guildDir);
        Path lineupFilePath = createInitialLineupFile(DEFAULT_GUILD_NAME, guildDir);

        Config.setLanguage("english");
        Config.setLastGuildPath(guildFilePath.toString());
        Config.setLastLineUpPath(lineupFilePath.toString());
        Config.save();
    }

    /**
     * Logs the {@link CatalogVersion} of heroes.json/titans.json/fortifications.json
     * at startup (visible in the app's log panel), so it's clear at a glance
     * which game/patch state the catalog data was last checked against
     * without having to go dig through the data files themselves.
     */
    private static void logCatalogVersion() {
        String suffix = CatalogVersion.note().isBlank() ? "" : " (" + CatalogVersion.note() + ")";
        Logger.logToFile("Catalog data version: " + CatalogVersion.dataVersion() + suffix);
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
            Guild demoGuild = GuildRepository.load(DEMO_GUILD_PATH);
            GuildRepository.save(demoGuild, context.guildFilePath());
            context.setGuild(demoGuild);
        } catch (IOException e) {
            Logger.logException("Could not load demo guild from " + DEMO_GUILD_PATH, e);
        }

        try {
            Lineup demoLineup = LineupRepository.load(DEMO_GUILD_LINEUP);
            LineupRepository.save(demoLineup, context.lineupFilePath());
            context.setLineup(demoLineup);
        } catch (IOException e) {
            Logger.logException("Could not load demo lineup from " + DEMO_GUILD_LINEUP, e);
        }
    }
    private static void loadGuildContext(AppContext context) {
        Path guildFilePath = Paths.get(Config.getLastGuildPath());
        Guild guild;
        try {
            guild = GuildRepository.load(guildFilePath);
        } catch (IOException e) {
            Logger.logException("Could not load guild from " + guildFilePath, e);
            guild = new Guild("guild", "", List.of());
        }
        context.set(guild, guildFilePath);
    }

    private static void loadLineupContext(AppContext context) {
        Path lineupFilePath = Paths.get(Config.getLastLineUpPath());
        Lineup lineup;
        try {
            lineup = LineupRepository.load(lineupFilePath);
        } catch (IOException e) {
            Logger.logException("Could not load lineup from " + lineupFilePath, e);
            lineup = LineupRepository.createEmptyLineup();
        }
        context.set(lineup, lineupFilePath);
    }

    public static Path createInitialGuildFile(String guildName, Path guildDir) {
        Path guildFile = guildDir.resolve(GUILD_FILE_NAME);
        Guild guild = new Guild(slugify(guildName), guildName, List.of());
        try {
            GuildRepository.save(guild, guildFile);
        } catch (IOException e) {
            Logger.logException("Could not create " + guildFile, e);
        }
        return guildFile;
    }

    public static Path createInitialLineupFile(String guildName, Path guildDir) {
        Path lineupFile = guildDir.resolve(LINEUP_FILE_NAME);
        Lineup lineup = new Lineup(slugify(guildName), guildName, "", LocalDateTime.now(), List.of());
        try {
            LineupRepository.save(lineup, lineupFile);
        } catch (IOException e) {
            Logger.logException("Could not create " + lineupFile, e);
        }
        return lineupFile;
    }

    /** Builds a simple, URL-/filesystem-friendly id from the guild name (analogous to the catalog JSONs). */
    public static String slugify(String name) {
        String slug = name.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "guild" : slug;
    }
}
