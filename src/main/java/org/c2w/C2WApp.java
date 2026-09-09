package org.c2w;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.Cow2Frame;
import org.c2w.gui.InitialSetupDialog;
import org.c2w.util.AppContext;
import org.c2w.util.Config;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

public class C2WApp {

    public static final String GUILD_FILE_NAME = "guild.json";
    public static final String LINEUP_FILE_NAME = "default.lineup";

    public static void main(String[] args) {
        if (Config.exists()) {
            Config.load();
        } else {
            runInitialSetup();
        }

        AppContext context = new AppContext();
        loadGuildContext(context);
        loadLineupContext(context);

        SwingUtilities.invokeLater(() -> new Cow2Frame(context));
    }

    /**
     * Asks for language and guild name via a modal dialog on the very first
     * start, creates the guild folder along with guild.json and a matching
     * default.lineup, and writes language/lastGuildPath/lastLineUpPath to a
     * new config.properties.
     */
    private static void runInitialSetup() {
        InitialSetupDialog dialog = InitialSetupDialog.show(null);

        Path guildDir = Paths.get("workspace", dialog.getGuildName());
        Path guildFilePath = createInitialGuildFile(dialog.getGuildName(), guildDir);
        Path lineupFilePath = createInitialLineupFile(dialog.getGuildName(), guildDir);

        Config.setLanguage(dialog.getSelectedLanguageFile());
        Config.setLastGuildPath(guildFilePath.toString());
        Config.setLastLineUpPath(lineupFilePath.toString());
        Config.save();
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
