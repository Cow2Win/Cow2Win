package org.c2w.service;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Use cases around the lineups of the currently open guild: creating,
 * selecting, deleting and saving ".lineup" files, running a lineup
 * algorithm for one fortification type (replacing that type's assignments)
 * and editing team assignments. Keeps {@link AppContext}
 * (including its dirty state), the files on disk and config.properties
 * consistent with each other, so the GUI only has to collect input and
 * report errors.
 */
public class LineupService {

    /** File name of the lineup created together with a new guild. */
    public static final String DEFAULT_LINEUP_FILE_NAME = "default.lineup";

    /** Glob pattern matching lineup files in a guild folder. */
    private static final String LINEUP_FILE_GLOB = "*" + LineupFiles.SUFFIX;

    private final AppContext context;
    private final RecentFiles recentFiles;

    public LineupService(AppContext context) {
        this(context, RecentFiles.CONFIG);
    }

    public LineupService(AppContext context, RecentFiles recentFiles) {
        if (context == null) {
            throw new IllegalArgumentException("LineupService needs an AppContext");
        }
        if (recentFiles == null) {
            throw new IllegalArgumentException("LineupService needs a RecentFiles");
        }
        this.context = context;
        this.recentFiles = recentFiles;
    }

    /**
     * Outcome of {@link #computeAlgorithm}: the lineup the run started from,
     * the lineup it produced, the algorithm, how many assignments of the
     * algorithm's type were removed first and how many teams it then assigned.
     */
    public record AlgorithmRun(Lineup before, Lineup result, LineupAlgorithm algorithm, int removed, int assigned) {

        /** True if the run removed or assigned at least one team. */
        public boolean changedAnything() {
            return removed > 0 || assigned > 0;
        }
    }

    /**
     * Outcome of {@link #assignTeam}: {@code assigned} is false if the target
     * fortification was already full ({@code filledSlots} other teams in it).
     */
    public record AssignResult(boolean assigned, long filledSlots) {
    }

    /** Folder of the currently open guild - where its lineup files live. */
    public Path guildDir() {
        return context.guildFilePath().getParent();
    }

    /** Sorted file names of every lineup of the currently open guild. */
    public List<String> listLineupFileNames() {
        return listLineupFileNames(guildDir());
    }

    /**
     * Sorted file names (not full paths) of every ".lineup" file directly
     * inside {@code guildDir}. Empty if the folder is null, does not exist,
     * or cannot be read (logged).
     */
    public static List<String> listLineupFileNames(Path guildDir) {
        List<String> result = new ArrayList<>();
        if (guildDir == null || !Files.isDirectory(guildDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(guildDir, LINEUP_FILE_GLOB)) {
            for (Path path : stream) {
                result.add(path.getFileName().toString());
            }
        } catch (IOException e) {
            Logger.logException("Could not list lineup files in " + guildDir, e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /** {@code name} with the lineup file suffix appended, unless it already ends with it. */
    public static String toLineupFileName(String name) {
        return name.endsWith(LineupFiles.SUFFIX) ? name : name + LineupFiles.SUFFIX;
    }

    /** True if the current guild already has a file with this name. */
    public boolean lineupExists(String fileName) {
        return Files.exists(guildDir().resolve(fileName));
    }

    /** True if the currently open lineup is the guild's read-only "Original" lineup (see {@link LineupFiles}). */
    public boolean isOriginalOpen() {
        return LineupFiles.isOriginal(context.lineupFilePath());
    }

    /** Creates a new, empty lineup file in the current guild folder and opens it. */
    public void createLineup(String fileName) throws IOException {
        Path lineupPath = guildDir().resolve(fileName);
        Guild guild = context.guild();
        Lineup lineup = new Lineup(guild.id(), guild.name(), "", LocalDateTime.now(), List.of());
        LineupRepository.save(lineup, lineupPath);
        open(lineup, lineupPath);
        Logger.log("Created: " + lineupPath);
        GuildLog.event(guildDirOrNull(), "guildLog.lineupCreated", LineupFiles.displayName(fileName));
    }

    /**
     * Loads the given lineup file of the current guild and opens it -
     * discarding unsaved changes of the currently open lineup, so ask first.
     */
    public void selectLineup(String fileName) throws IOException {
        Path lineupPath = guildDir().resolve(fileName);
        open(LineupRepository.load(lineupPath), lineupPath);
    }

    /**
     * Deletes the given lineup file of the current guild. Does NOT open
     * another lineup - see {@link #lineupAfterRemoval}.
     */
    public void deleteLineup(String fileName) throws IOException {
        LineupRepository.delete(guildDir().resolve(fileName));
        Logger.log("Removed: " + fileName);
        GuildLog.event(guildDirOrNull(), "guildLog.lineupRemoved", LineupFiles.displayName(fileName));
    }

    /**
     * The lineup file to open after the one at {@code removedIndex} (in the
     * list {@link #listLineupFileNames()} returned before removing it) was
     * deleted: the one that moved up into its place, or the new last one.
     * Null if no lineup is left.
     */
    public String lineupAfterRemoval(int removedIndex) {
        List<String> remaining = listLineupFileNames();
        if (remaining.isEmpty()) {
            return null;
        }
        return remaining.get(Math.max(0, Math.min(removedIndex, remaining.size() - 1)));
    }

    /**
     * Saves the currently open lineup to its file and makes it the new
     * baseline of the "Changes" view (see {@link AppContext#markLineupSaved()}).
     * On failure nothing changes - old baseline, still dirty.
     */
    public void saveLineup() throws IOException {
        LineupRepository.save(context.lineup(), context.lineupFilePath());
        context.markLineupSaved();
        Logger.log("Saved: " + context.lineupFilePath());
        GuildLog.event(guildDirOrNull(), "guildLog.lineupSaved", lineupDisplayName());
    }

    /** Removes every team assignment from the open lineup (in memory only, until saved). */
    public void clearLineup() {
        Lineup current = context.lineup();
        if (current.entries().isEmpty()) {
            return;
        }
        update(withEntries(current, List.of()));
        Logger.log("Cleared: " + context.lineupFilePath());
        GuildLog.event(guildDirOrNull(), "guildLog.lineupCleared", lineupDisplayName());
    }

    /**
     * Removes every assignment of {@code algorithm}'s type from {@code lineup}
     * and then runs {@code algorithm} on it - the other type's assignments stay
     * as they are. A pure computation that touches no shared state, so it may
     * run on a background thread (see {@code ActionBar#onRunAlgorithm}). Apply
     * the result with {@link #applyAlgorithmRun}.
     *
     * @throws IllegalArgumentException for a {@link ManualLineupAlgorithm}, which assigns nothing
     */
    public static AlgorithmRun computeAlgorithm(Lineup lineup, Guild guild, LineupAlgorithm algorithm) {
        if (algorithm instanceof ManualLineupAlgorithm) {
            throw new IllegalArgumentException("\"Manual\" cannot run an assignment");
        }
        Lineup.TeamType teamType = algorithm.teamType();
        List<Lineup.Entry> kept = lineup.entries().stream().filter(entry -> entry.teamType() != teamType).toList();
        Lineup cleared = withEntries(lineup, kept);
        Lineup result = algorithm.run(cleared, guild);
        int removed = lineup.entries().size() - kept.size();
        int assigned = result.entries().size() - cleared.entries().size();
        return new AlgorithmRun(lineup, result, algorithm, removed, assigned);
    }

    /**
     * Makes {@code run}'s result the open lineup (in memory only, until
     * saved) - unless the open lineup changed while the algorithm was
     * running, in which case the run is discarded, so no edit made in the
     * meantime is lost. Must be called on the Swing event thread.
     *
     * @return false if the run was discarded
     */
    public boolean applyAlgorithmRun(AlgorithmRun run) {
        if (context.lineup() != run.before()) {
            Logger.log("Algorithm run discarded: the lineup was changed while it was running.");
            return false;
        }
        if (run.changedAnything()) {
            update(run.result());
        }
        Lineup.TeamType teamType = run.algorithm().teamType();
        Logger.log(teamType + " (" + run.algorithm().displayName() + "): " + run.removed() + " assignment(s) removed, "
                + run.assigned() + " team(s) assigned.");
        GuildLog.event(guildDirOrNull(), "guildLog.algorithmRun", teamTypeName(teamType),
                run.algorithm().localizedName(), run.removed(), run.assigned());
        return true;
    }

    /**
     * Runs {@code algorithm} on the open lineup (after removing its type's
     * assignments) and applies the result right away, on the calling thread -
     * see {@link #computeAlgorithm}/{@link #applyAlgorithmRun}.
     */
    public AlgorithmRun runAlgorithm(LineupAlgorithm algorithm) {
        AlgorithmRun run = computeAlgorithm(context.lineup(), context.guild(), algorithm);
        applyAlgorithmRun(run);
        return run;
    }

    /** The localized name of {@code teamType} ("Heroes"/"Titans"). */
    public static String teamTypeName(Lineup.TeamType teamType) {
        return LanguageService.displayName(teamType == Lineup.TeamType.HERO
                ? "fortificationMap.showHeroes" : "fortificationMap.showTitans");
    }

    /**
     * Moves one team to {@code fortification} in the open lineup (in memory
     * only, until saved) - or removes its assignment if {@code fortification}
     * is null. Refused (nothing changes) if the fortification is already
     * full without this team.
     */
    public AssignResult assignTeam(String teamMemberId, Lineup.TeamType teamType, int teamIndex,
                                   Fortification fortification) {
        Lineup current = context.lineup();
        List<Lineup.Entry> otherEntries = new ArrayList<>();
        for (Lineup.Entry entry : current.entries()) {
            boolean sameTeam = entry.teamMemberId().equals(teamMemberId) && entry.teamType() == teamType
                    && entry.teamIndex() == teamIndex;
            if (!sameTeam) {
                otherEntries.add(entry);
            }
        }

        long filledSlots = 0;
        if (fortification != null) {
            filledSlots = otherEntries.stream()
                    .filter(entry -> entry.fortificationId().equals(fortification.id()))
                    .count();
            if (filledSlots >= fortification.capacity()) {
                return new AssignResult(false, filledSlots);
            }
            otherEntries.add(new Lineup.Entry(fortification.id(), teamMemberId, teamType, teamIndex));
        }
        update(withEntries(current, otherEntries));
        return new AssignResult(true, filledSlots);
    }

    /**
     * Saves {@code guild} and {@code lineup} to the current guild/lineup
     * files and makes them the open ones - e.g. after editing one
     * fortification's teams, which can change both. The saved lineup becomes the new
     * baseline of the "Changes" view (see {@link AppContext#markLineupSaved()}).
     */
    public void saveWithGuild(Guild guild, Lineup lineup) throws IOException {
        GuildRepository.save(guild, context.guildFilePath());
        LineupRepository.save(lineup, context.lineupFilePath());
        context.setGuild(guild);
        context.setLineup(lineup);
        context.setGuildDirty(false);
        context.markLineupSaved();
        logGuildAndLineupSaved(context.lineupFilePath(), GuildService.SaveOrigin.FORTIFICATION_ENTRY);
    }

    /**
     * Saves {@code guild} and the guild's "Original" lineup (the actual
     * in-game deployment, see {@link LineupFiles}) and opens that Original
     * lineup, so the toolbar and the fortification map immediately show what
     * was just entered.
     */
    public void saveOriginal(Guild guild, Lineup original, Path originalLineupPath) throws IOException {
        GuildRepository.save(guild, context.guildFilePath());
        LineupRepository.save(original, originalLineupPath);
        context.setGuild(guild);
        context.setGuildDirty(false);
        open(original, originalLineupPath);
        logGuildAndLineupSaved(originalLineupPath, GuildService.SaveOrigin.TEAM_ENTRY);
    }

    /** One guild log entry for saving the guild together with the lineup at {@code lineupPath}. */
    private void logGuildAndLineupSaved(Path lineupPath, GuildService.SaveOrigin origin) {
        GuildLog.event(guildDirOrNull(), "guildLog.guildAndLineupSaved",
                LineupFiles.displayName(lineupPath.getFileName().toString()),
                LanguageService.displayName(origin.key()));
    }

    /** Folder of the open guild for its guild log, null if none is open. */
    private Path guildDirOrNull() {
        return context.guildFilePath() == null ? null : context.guildFilePath().getParent();
    }

    /** The open lineup's name as shown in the UI. */
    private String lineupDisplayName() {
        Path lineupPath = context.lineupFilePath();
        return lineupPath == null ? "" : LineupFiles.displayName(lineupPath.getFileName().toString());
    }

    /**
     * Writes a new, empty {@value #DEFAULT_LINEUP_FILE_NAME} for the guild
     * {@code guildName} into {@code guildDir} and returns its path. A failure
     * is logged, not thrown - loading the path afterwards reports it.
     */
    public static Path createInitialLineupFile(String guildName, Path guildDir) {
        Path lineupFile = guildDir.resolve(DEFAULT_LINEUP_FILE_NAME);
        Lineup lineup = new Lineup(GuildService.slugify(guildName), guildName, "", LocalDateTime.now(), List.of());
        try {
            LineupRepository.save(lineup, lineupFile);
        } catch (IOException e) {
            Logger.logException("Could not create " + lineupFile, e);
        }
        return lineupFile;
    }

    // --- private ---

    /** Makes a lineup that matches its file on disk the open one, and remembers it for the next start. */
    private void open(Lineup lineup, Path lineupPath) {
        context.set(lineup, lineupPath);
        context.setLineupDirty(false);
        recentFiles.lineupOpened(lineupPath);
    }

    /** Replaces the open lineup with an in-memory edit of it. */
    private void update(Lineup lineup) {
        context.setLineup(lineup);
        context.setLineupDirty(true);
    }

    private static Lineup withEntries(Lineup lineup, List<Lineup.Entry> entries) {
        return new Lineup(lineup.guildId(), lineup.guildName(), lineup.algorithmName(), lineup.createdAt(), entries);
    }
}
