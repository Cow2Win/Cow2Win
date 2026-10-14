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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Use cases around the lineups of the currently open guild: creating,
 * copying, selecting, deleting and saving ".lineup" files, running a lineup
 * algorithm for one fortification type (replacing that type's assignments
 * or only adding to them, see {@link AssignmentMode}) and editing team
 * assignments (assigning, moving, removing and swapping teams). Keeps {@link AppContext}
 * (including its dirty state), the files on disk and config.properties
 * consistent with each other, so the GUI only has to collect input and
 * report errors.
 */
public class LineupService {

    /** File name of the lineup created together with a new guild. */
    public static final String DEFAULT_LINEUP_FILE_NAME = "default.lineup";

    /** Glob pattern matching lineup files in a guild folder. */
    private static final String LINEUP_FILE_GLOB = "*" + LineupFiles.SUFFIX;

    /** A lineup name ending in " (n)" - the number of an earlier copy, see {@link #copyFileName}. */
    private static final Pattern COPY_NUMBER = Pattern.compile("(.+) \\(\\d+\\)");

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

    /** How {@link #computeAlgorithm} treats the existing assignments of the algorithm's type. */
    public enum AssignmentMode {
        /** Removes them first - the algorithm assigns every team of the type anew. */
        REPLACE,
        /** Keeps them - the algorithm only distributes the teams that have no assignment yet. */
        COMPLETE
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
     * Copies the saved state of the open lineup - read fresh from its file, so unsaved changes
     * are NOT copied (ask first) - into a new file of the same guild named by
     * {@link #copyFileName}, and opens the copy. Every entry (heroes and titans) and the
     * algorithm name are kept; the copy of the Original ("Live") is a normal, editable lineup.
     *
     * @return the file name of the copy
     */
    public String copyLineup() throws IOException {
        Path sourcePath = context.lineupFilePath();
        Lineup saved = LineupRepository.load(sourcePath);
        String sourceFileName = sourcePath.getFileName().toString();
        String fileName = copyFileName(sourceFileName, listLineupFileNames());
        Path lineupPath = guildDir().resolve(fileName);
        Lineup copy = new Lineup(saved.guildId(), saved.guildName(), saved.algorithmName(), LocalDateTime.now(),
                saved.entries());
        LineupRepository.save(copy, lineupPath);
        open(copy, lineupPath);
        Logger.log("Copied: " + sourcePath + " -> " + lineupPath);
        GuildLog.event(guildDirOrNull(), "guildLog.lineupCopied",
                LineupFiles.displayName(sourceFileName), LineupFiles.displayName(fileName));
        return fileName;
    }

    /**
     * File name of a copy of {@code sourceFileName}: "&lt;name&gt; (n).lineup" with the smallest
     * n from 1 that is not taken in {@code existingFileNames} (compared ignoring case, like the
     * Windows file system). The name is the display name of the source ("Live" for the
     * Original); a trailing " (n)" of it is cut off first, so a copy of "Test (1)" is "Test (2)".
     */
    public static String copyFileName(String sourceFileName, Collection<String> existingFileNames) {
        String base = LineupFiles.displayName(sourceFileName);
        Matcher numbered = COPY_NUMBER.matcher(base);
        if (numbered.matches()) {
            base = numbered.group(1);
        }
        Set<String> taken = new HashSet<>();
        existingFileNames.forEach(name -> taken.add(name.toLowerCase(Locale.ROOT)));
        for (int n = 1; ; n++) {
            String candidate = base + " (" + n + ")" + LineupFiles.SUFFIX;
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
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
     * Runs {@code algorithm} on {@code lineup}: with {@link AssignmentMode#REPLACE} after
     * removing every assignment of the algorithm's type, with {@link AssignmentMode#COMPLETE}
     * on the lineup as it is (the algorithms only add entries for teams without one, so the
     * existing assignments stay). The other type's assignments always stay as they are. A pure
     * computation that touches no shared state, so it may run on a background thread (see
     * {@code ActionBar#onRunAlgorithm}). Apply the result with {@link #applyAlgorithmRun}.
     *
     * @throws IllegalArgumentException for a {@link ManualLineupAlgorithm}, which assigns nothing
     */
    public static AlgorithmRun computeAlgorithm(Lineup lineup, Guild guild, LineupAlgorithm algorithm,
                                                AssignmentMode mode) {
        if (algorithm instanceof ManualLineupAlgorithm) {
            throw new IllegalArgumentException("\"Manual\" cannot run an assignment");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        Lineup start = lineup;
        if (mode == AssignmentMode.REPLACE) {
            Lineup.TeamType teamType = algorithm.teamType();
            start = withEntries(lineup, lineup.entries().stream().filter(entry -> entry.teamType() != teamType).toList());
        }
        Lineup result = algorithm.run(start, guild);
        int removed = lineup.entries().size() - start.entries().size();
        int assigned = result.entries().size() - start.entries().size();
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
     * Runs {@code algorithm} on the open lineup in {@code mode} and applies the
     * result right away, on the calling thread - see
     * {@link #computeAlgorithm}/{@link #applyAlgorithmRun}.
     */
    public AlgorithmRun runAlgorithm(LineupAlgorithm algorithm, AssignmentMode mode) {
        AlgorithmRun run = computeAlgorithm(context.lineup(), context.guild(), algorithm, mode);
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
     * Swaps the fortifications of two teams in the open lineup (in memory
     * only, until saved): {@code a} goes to {@code b}'s fortification and vice
     * versa, as one single change. No capacity check is needed - both
     * fortifications keep their number of teams. Nothing happens if both are
     * in the same fortification.
     *
     * @throws IllegalArgumentException if {@code a} or {@code b} is not an entry of the open lineup
     */
    public void swapTeams(Lineup.Entry a, Lineup.Entry b) {
        Lineup current = context.lineup();
        if (!current.entries().contains(a) || !current.entries().contains(b)) {
            throw new IllegalArgumentException("Both entries must be in the open lineup: " + a + ", " + b);
        }
        if (a.fortificationId().equals(b.fortificationId())) {
            return;
        }
        List<Lineup.Entry> entries = new ArrayList<>();
        for (Lineup.Entry entry : current.entries()) {
            if (entry.equals(a)) {
                entries.add(new Lineup.Entry(b.fortificationId(), a.teamMemberId(), a.teamType(), a.teamIndex()));
            } else if (entry.equals(b)) {
                entries.add(new Lineup.Entry(a.fortificationId(), b.teamMemberId(), b.teamType(), b.teamIndex()));
            } else {
                entries.add(entry);
            }
        }
        update(withEntries(current, entries));
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
