package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.LineupChangePlanService;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupComparisonService;
import org.c2w.domain.LineupComparisonService.LineupComparison;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GUI-free core of the change plan ("what to change in Hero Wars to get from the guild's
 * Original lineup - the one in the game - to a target lineup"): loads the Original, determines
 * the target, checks it belongs to the same guild and computes the steps
 * ({@link LineupComparisonService#compare} → {@link LineupChangePlanService#from}). Read only:
 * never saves anything.
 */
public final class ChangePlanModel {

    private static final String LINEUP_FILE_GLOB = "*.lineup";

    private ChangePlanModel() {
    }

    /** The outcome of {@link #plan}. */
    public enum State {
        /** A plan was computed - see {@link Result#steps()}. */
        OK,
        /** The guild has no Original lineup yet. */
        NO_ORIGINAL,
        /** The Original or the target could not be loaded (see {@link Result#messageKey()} and the log). */
        LOAD_ERROR,
        /** The target lineup belongs to another guild. */
        DIFFERENT_GUILD,
        /** No target chosen (no saved lineup or no algorithm selected). */
        NO_TARGET,
        /** The target is the open lineup, and that is the Original itself - nothing to compare. */
        ORIGINAL_IS_OPEN
    }

    /**
     * The result of {@link #plan}: for {@link State#OK} the comparison and the steps, otherwise
     * empty.
     *
     * @param messageKey for {@link State#LOAD_ERROR}: the language file key saying what could not be loaded
     * @param detail     for {@link State#LOAD_ERROR}: the exception's message
     */
    public record Result(State state, LineupComparison comparison, List<ChangeStep> steps, String messageKey,
                         String detail) {

        static Result of(State state) {
            return new Result(state, null, List.of(), null, null);
        }

        static Result loadError(String messageKey, Exception e) {
            return new Result(State.LOAD_ERROR, null, List.of(), messageKey, e.getMessage());
        }

        public boolean isOk() {
            return state == State.OK;
        }
    }

    /** What the plan leads to. */
    public sealed interface Target permits CurrentLineup, SavedLineup, Algorithms {
    }

    /**
     * The lineup open in the context bar, as it stands in memory - including unsaved changes.
     *
     * @param lineupFilePath its file, to tell whether it is the Original itself
     */
    public record CurrentLineup(Lineup lineup, Path lineupFilePath) implements Target {
    }

    /** A saved ".lineup" file of the guild, as it stands on disk. */
    public record SavedLineup(String fileName) implements Target {
    }

    /** A candidate the two algorithms produce from the Original on the fly (see {@link LineupAlgorithms#runBoth}). */
    public record Algorithms(LineupAlgorithm heroAlgorithm, LineupAlgorithm titanAlgorithm) implements Target {
    }

    /** The steps from the Original of the guild whose file is {@code guildFilePath} to {@code target}. */
    public static Result plan(Path guildFilePath, Guild guild, Target target) {
        if (target instanceof CurrentLineup current
                && current.lineupFilePath() != null && LineupFiles.isOriginal(current.lineupFilePath())) {
            return Result.of(State.ORIGINAL_IS_OPEN);
        }
        Path guildDir = guildFilePath == null ? null : guildFilePath.getParent();
        if (guildDir == null) {
            return Result.of(State.NO_ORIGINAL);
        }
        Path originalPath = LineupFiles.originalPathFor(guildDir);
        if (!Files.exists(originalPath)) {
            return Result.of(State.NO_ORIGINAL);
        }
        Lineup original;
        try {
            original = LineupRepository.load(originalPath);
        } catch (IOException e) {
            Logger.logException("Change plan: could not load the Original lineup " + originalPath, e);
            return Result.loadError("changePlan.loadOriginalError", e);
        }

        Lineup lineup;
        switch (target) {
            case CurrentLineup current -> {
                if (current.lineup() == null) {
                    return Result.of(State.NO_TARGET);
                }
                lineup = current.lineup();
            }
            case SavedLineup saved -> {
                if (saved.fileName() == null) {
                    return Result.of(State.NO_TARGET);
                }
                try {
                    lineup = LineupRepository.load(guildDir.resolve(saved.fileName()));
                } catch (IOException e) {
                    Logger.logException("Change plan: could not load the target lineup " + saved.fileName(), e);
                    return Result.loadError("changePlan.loadTargetError", e);
                }
            }
            case Algorithms algorithms -> {
                if (algorithms.heroAlgorithm() == null || algorithms.titanAlgorithm() == null) {
                    return Result.of(State.NO_TARGET);
                }
                lineup = LineupAlgorithms.runBoth(algorithms.heroAlgorithm(), algorithms.titanAlgorithm(), original, guild);
            }
        }
        if (!(target instanceof Algorithms) && !original.guildId().equals(lineup.guildId())) {
            return Result.of(State.DIFFERENT_GUILD);
        }

        LineupComparison comparison = LineupComparisonService.compare(original, lineup, guild);
        return new Result(State.OK, comparison, LineupChangePlanService.from(comparison), null, null);
    }

    /** Every ".lineup" file of the guild except the Original itself (always the "before" side), sorted. */
    public static List<String> targetLineupFileNames(Path guildFilePath) {
        List<String> result = new ArrayList<>();
        Path guildDir = guildFilePath == null ? null : guildFilePath.getParent();
        if (guildDir == null || !Files.isDirectory(guildDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(guildDir, LINEUP_FILE_GLOB)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (!LineupFiles.isOriginalFileName(fileName)) {
                    result.add(fileName);
                }
            }
        } catch (IOException e) {
            Logger.logException("Could not list lineup files for the change plan", e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }
}
