package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Use cases around the guild(s) in the workspace: listing, creating,
 * switching, deleting and saving them. Keeps {@link AppContext}, the files
 * on disk and config.properties consistent with each other, so the GUI only
 * has to collect input and report errors.
 *
 * <p>Every guild lives in its own folder directly under
 * {@link Config#getWorkspaceDir()}, holding a {@value #GUILD_FILE_NAME} and
 * any number of ".lineup" files (see {@link LineupService}).
 */
public class GuildService {

    /** File name of a guild's own data file inside its guild folder. */
    public static final String GUILD_FILE_NAME = "guild.json";

    private final AppContext context;
    private final RecentFiles recentFiles;

    public GuildService(AppContext context) {
        this(context, RecentFiles.CONFIG);
    }

    public GuildService(AppContext context, RecentFiles recentFiles) {
        if (context == null) {
            throw new IllegalArgumentException("GuildService needs an AppContext");
        }
        if (recentFiles == null) {
            throw new IllegalArgumentException("GuildService needs a RecentFiles");
        }
        this.context = context;
        this.recentFiles = recentFiles;
    }

    /**
     * Where a guild save was triggered - named in the guild log entry (see {@link GuildLog}).
     * Each value carries the language key of its origin text, null for a plain "Guild saved".
     */
    public enum SaveOrigin {
        CONTEXT_BAR(null),
        /** The former guild editor dialog - kept for old guild log entries only. */
        GUILD_EDITOR("guildLog.origin.guildEditor"),
        /** Adding or deleting a member in the member overview of the input stage. */
        MEMBER_OVERVIEW("guildLog.origin.memberOverview"),
        TEAM_ENTRY("guildLog.origin.teamEntry"),
        FORTIFICATION_ENTRY("guildLog.origin.fortificationEntry"),
        HERO_OVERVIEW("guildLog.origin.heroOverview"),
        TITAN_OVERVIEW("guildLog.origin.titanOverview"),
        JOURNAL_IMPORT("guildLog.origin.journalImport"),
        JOURNAL_SYNC("guildLog.origin.journalSync"),
        JOURNAL_TEAM_BUILDER("guildLog.origin.journalTeamBuilder");

        private final String key;

        SaveOrigin(String key) {
            this.key = key;
        }

        /** Language key of the origin text, null for none. */
        public String key() {
            return key;
        }
    }

    /** Writes the "guild saved" entry (with its origin, if any) to the guild log of {@code guildDir}. */
    static void logGuildSaved(Path guildDir, SaveOrigin origin) {
        if (origin == null || origin.key() == null) {
            GuildLog.event(guildDir, "guildLog.guildSaved");
        } else {
            GuildLog.event(guildDir, "guildLog.guildSavedFrom", LanguageService.displayName(origin.key()));
        }
    }

    /**
     * Thrown by {@link #switchToGuild} when the guild itself could be loaded
     * but none of its lineups - lets the GUI tell the two failures apart.
     */
    public static final class LineupLoadException extends IOException {
        LineupLoadException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    /**
     * The workspace directory, taken straight from {@link Config#getWorkspaceDir()}
     * - the single source of truth for "the workspace". Guild folders are
     * always listed and resolved under the configured workspace, never under
     * whichever folder the currently loaded guild happens to live in.
     */
    public Path workspaceDir() {
        return Config.getWorkspaceDir();
    }

    /** The folder of the guild with the given folder name (whether it exists or not). */
    public Path guildDir(String folderName) {
        return workspaceDir().resolve(folderName);
    }

    /** True if a file or folder with this name already exists in the workspace. */
    public boolean guildExists(String folderName) {
        return Files.exists(guildDir(folderName));
    }

    /** Folder name of the currently open guild, or null if unknown. */
    public String currentGuildFolderName() {
        Path guildDir = context.guildFilePath() == null ? null : context.guildFilePath().getParent();
        return guildDir == null ? null : guildDir.getFileName().toString();
    }

    /** Sorted names of every folder in the workspace that contains a {@value #GUILD_FILE_NAME}. */
    public List<String> listGuildFolderNames() {
        List<String> result = new ArrayList<>();
        Path workspaceDir = workspaceDir();
        if (!Files.isDirectory(workspaceDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workspaceDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && Files.isRegularFile(path.resolve(GUILD_FILE_NAME))) {
                    result.add(path.getFileName().toString());
                }
            }
        } catch (IOException e) {
            Logger.logException("Could not list guild folders in " + workspaceDir, e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /**
     * Creates a new guild folder named {@code name} with an empty guild and
     * an empty default lineup. Does NOT open it - see {@link #switchToGuild}.
     */
    public void createGuild(String name) {
        Path guildDir = guildDir(name);
        Path guildFilePath = createInitialGuildFile(name, guildDir);
        LineupService.createInitialLineupFile(name, guildDir);
        Logger.log("Created guild: " + guildFilePath);
        GuildLog.event(guildDir, "guildLog.guildCreated", name);
    }

    /**
     * Loads the guild in the given folder together with its first lineup
     * (alphabetically; an empty default lineup is created if there is none)
     * and makes both the open guild/lineup - discarding any unsaved changes
     * of the previously open ones, so ask the user first.
     *
     * @throws LineupLoadException if the guild loaded but its lineup could not be
     * @throws IOException if the guild itself could not be loaded
     */
    public void switchToGuild(String folderName) throws IOException {
        Path guildDir = guildDir(folderName);
        Path guildFilePath = guildDir.resolve(GUILD_FILE_NAME);
        Guild guild = GuildRepository.load(guildFilePath, context.catalog());

        Path lineupPath;
        Lineup lineup;
        try {
            List<String> lineupFileNames = LineupService.listLineupFileNames(guildDir);
            lineupPath = lineupFileNames.isEmpty()
                    ? LineupService.createInitialLineupFile(guild.name(), guildDir)
                    : guildDir.resolve(lineupFileNames.get(0));
            lineup = LineupRepository.load(lineupPath);
        } catch (IOException e) {
            throw new LineupLoadException(e);
        }

        context.switchTo(guild, guildFilePath, lineup, lineupPath);
        context.setGuildDirty(false);
        context.setLineupDirty(false);
        recentFiles.guildOpened(guildFilePath);
        recentFiles.lineupOpened(lineupPath);
        Logger.log("Switched to guild: " + guildFilePath);
        GuildLog.event(guildDir, "guildLog.guildOpened");
    }

    /**
     * Deletes the given guild folder from disk, including its journal database
     * (closed first if it is open - Windows would refuse to delete an open file).
     * Does NOT switch to another guild - see {@link #guildAfterRemoval} for which
     * one should take its place.
     */
    public void deleteGuild(String folderName) throws IOException {
        Path guildDir = guildDir(folderName);
        context.journal().closeIfOpenFor(guildDir);
        GuildRepository.delete(guildDir);
        Logger.log("Removed guild: " + guildDir);
    }

    /**
     * The guild folder to show after the guild at {@code removedIndex} (in
     * the list {@link #listGuildFolderNames()} returned before removing it)
     * was deleted: the one that moved up into its place, or the new last one.
     * Null if no guild is left.
     */
    public String guildAfterRemoval(int removedIndex) {
        List<String> remaining = listGuildFolderNames();
        if (remaining.isEmpty()) {
            return null;
        }
        return remaining.get(Math.max(0, Math.min(removedIndex, remaining.size() - 1)));
    }

    /** Saves the currently open guild as-is (from the context bar). */
    public void saveGuild() throws IOException {
        saveGuild(SaveOrigin.CONTEXT_BAR);
    }

    /** Saves the currently open guild as-is; {@code origin} is named in the guild log. */
    public void saveGuild(SaveOrigin origin) throws IOException {
        GuildRepository.save(context.guild(), context.guildFilePath());
        context.setGuildDirty(false);
        Logger.log("Saved: " + context.guildFilePath());
        logGuildSaved(context.guildFilePath().getParent(), origin);
    }

    /** Saves {@code updated} to the current guild file and makes it the open guild. */
    public void saveGuild(Guild updated) throws IOException {
        saveGuild(updated, SaveOrigin.CONTEXT_BAR);
    }

    /** Like {@link #saveGuild(Guild)}; {@code origin} is named in the guild log. */
    public void saveGuild(Guild updated, SaveOrigin origin) throws IOException {
        GuildRepository.save(updated, context.guildFilePath());
        context.setGuild(updated);
        context.setGuildDirty(false);
        Logger.log("Saved: " + context.guildFilePath());
        logGuildSaved(context.guildFilePath().getParent(), origin);
    }

    /**
     * Makes {@code updated} the open guild WITHOUT saving it and marks the guild
     * as having unsaved changes - like an edit in a dialog (e.g. the journal
     * import renaming or adding members); the user saves it the usual way.
     */
    public void updateGuild(Guild updated) {
        context.setGuild(updated);
        context.setGuildDirty(true);
    }

    /**
     * The name of another guild in the workspace (not {@code excludedFolder})
     * whose {@code gameGuildId} is {@code gameGuildId}, read cheaply from its
     * guild file - empty if there is none. Unreadable guild files are logged and skipped.
     */
    public Optional<String> findOtherGuildByGameGuildId(long gameGuildId, String excludedFolder) {
        return findOtherGuildFolderByGameGuildId(gameGuildId, excludedFolder).map(folder -> {
            try {
                String name = GuildRepository.readSummary(guildDir(folder).resolve(GUILD_FILE_NAME)).name();
                return name.isBlank() ? folder : name;
            } catch (IOException | RuntimeException e) {
                return folder;
            }
        });
    }

    /**
     * Like {@link #findOtherGuildByGameGuildId}, but the guild's workspace folder
     * name - what {@link #switchToGuild} needs.
     */
    public Optional<String> findOtherGuildFolderByGameGuildId(long gameGuildId, String excludedFolder) {
        for (String folder : listGuildFolderNames()) {
            if (folder.equals(excludedFolder)) {
                continue;
            }
            Path guildFile = guildDir(folder).resolve(GUILD_FILE_NAME);
            try {
                GuildRepository.GuildFileSummary summary = GuildRepository.readSummary(guildFile);
                if (summary.gameGuildId() != null && summary.gameGuildId() == gameGuildId) {
                    return Optional.of(folder);
                }
            } catch (IOException | RuntimeException e) {
                Logger.logException("Could not read " + guildFile, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Records that the guild was edited somewhere without being saved yet
     * (e.g. a pending power edit in a value overview dialog), so the user
     * gets asked before those changes are discarded.
     */
    public void markGuildEdited() {
        context.setGuildDirty(true);
    }

    /**
     * Writes a new, empty guild named {@code guildName} to
     * {@code guildDir}/{@value #GUILD_FILE_NAME} and returns that path. A
     * failure is logged, not thrown - loading the path afterwards reports it.
     */
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

    /** Builds a simple, URL-/filesystem-friendly id from the guild name (analogous to the catalog JSONs). */
    public static String slugify(String name) {
        String slug = name.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "guild" : slug;
    }
}
