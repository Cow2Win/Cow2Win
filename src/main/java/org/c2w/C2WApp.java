package org.c2w;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.Cow2Frame;
import org.c2w.gui.InitialSetupDialog;
import org.c2w.util.AppContext;
import org.c2w.util.BackupService;
import org.c2w.util.CatalogVersion;
import org.c2w.util.Config;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

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

    public static void main(String[] args) {
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

        SwingUtilities.invokeLater(() -> new Cow2Frame(context));
    }

    /**
     * Asks for language and guild name via a modal dialog on the very first
     * start, creates the guild folder along with guild.json and a matching
     * default.lineup, and writes language/lastGuildPath/lastLineUpPath to a
     * new config.properties.
     */
    private static void runInitialSetup()  {

        Path guildDir = Paths.get("workspace", DEFAULT_GUILD_NAME);
        Path guildFilePath = createInitialGuildFile(DEFAULT_GUILD_NAME, guildDir);
        Path lineupFilePath = createInitialLineupFile(DEFAULT_GUILD_NAME, guildDir);

        Config.setLanguage("english.txt");
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
        Logger.log("Catalog data version: " + CatalogVersion.dataVersion() + suffix);
    }

    private static void loadDemo(AppContext context){

        try {
            context.setGuild(GuildRepository.load(DEMO_GUILD_PATH));
            GuildRepository.save(context.guild(),context.guildFilePath());

            context.setLineup(LineupRepository.load(DEMO_GUILD_LINEUP));
            LineupRepository.save(context.lineup(),context.lineupFilePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static void loadGuildContext(AppContext context) {
        Path guildFilePath = Paths.get(Config.getLastGuildPath());
        Guild guild;
        try {
            guild = GuildRepository.load(guildFilePath);
        } catch (IOException e) {
            System.err.println("Could not load guild from " + guildFilePath + ": " + e.getMessage());
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
            System.err.println("Could not load lineup from " + lineupFilePath + ": " + e.getMessage());
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
            System.err.println("Could not create " + guildFile + ": " + e.getMessage());
        }
        return guildFile;
    }

    public static Path createInitialLineupFile(String guildName, Path guildDir) {
        Path lineupFile = guildDir.resolve(LINEUP_FILE_NAME);
        Lineup lineup = new Lineup(slugify(guildName), guildName, "", LocalDateTime.now(), List.of());
        try {
            LineupRepository.save(lineup, lineupFile);
        } catch (IOException e) {
            System.err.println("Could not create " + lineupFile + ": " + e.getMessage());
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
