package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    }

    /**
     * Deletes the given guild folder from disk. Does NOT switch to another
     * guild - see {@link #guildAfterRemoval} for which one should take its place.
     */
    public void deleteGuild(String folderName) throws IOException {
        Path guildDir = guildDir(folderName);
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

    /** Saves the currently open guild as-is. */
    public void saveGuild() throws IOException {
        GuildRepository.save(context.guild(), context.guildFilePath());
        context.setGuildDirty(false);
        Logger.log("Saved: " + context.guildFilePath());
    }

    /** Saves {@code updated} to the current guild file and makes it the open guild. */
    public void saveGuild(Guild updated) throws IOException {
        GuildRepository.save(updated, context.guildFilePath());
        context.setGuild(updated);
        context.setGuildDirty(false);
        Logger.log("Saved: " + context.guildFilePath());
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
