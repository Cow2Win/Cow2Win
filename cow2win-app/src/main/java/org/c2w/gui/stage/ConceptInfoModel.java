package org.c2w.gui.stage;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.LineupComparisonService;

import java.nio.file.Path;
import java.util.Optional;

/**
 * GUI-free values of the info panel of {@link ConceptStageView}: the comparison of the open
 * lineup with the guild's "Original" lineup (the one in the game), and the facts of a
 * selected fortification.
 */
public final class ConceptInfoModel {

    private ConceptInfoModel() {
    }

    /** What the comparison section can show. */
    public enum ComparisonState {
        /** The open lineup is the "Original" lineup itself - nothing to compare. */
        IS_ORIGINAL,
        /** The guild has no "Original" lineup, or it cannot be read. */
        NO_ORIGINAL,
        /** Compared - see the values of {@link Comparison}. */
        COMPARED
    }

    /**
     * The comparison "Original → open lineup". The power and count values are only set for
     * {@link ComparisonState#COMPARED} (0 otherwise).
     *
     * @param typePowerBefore power of the selected fortification type's teams in the Original
     * @param typePowerAfter  the same in the open lineup
     */
    public record Comparison(ComparisonState state,
                             int totalPowerBefore, int totalPowerAfter,
                             int typePowerBefore, int typePowerAfter,
                             long movedCount, long addedCount, long removedCount) {

        static Comparison of(ComparisonState state) {
            return new Comparison(state, 0, 0, 0, 0, 0, 0, 0);
        }

        public int totalPowerDiff() {
            return totalPowerAfter - totalPowerBefore;
        }

        public int typePowerDiff() {
            return typePowerAfter - typePowerBefore;
        }
    }

    /**
     * Compares {@code current} (open as {@code lineupFilePath}) with the "Original" lineup of the
     * guild whose file is {@code guildFilePath} - see {@link LineupComparisonService#compare}.
     * A missing or unreadable Original is no error (only logged).
     */
    public static Comparison compareWithOriginal(Path guildFilePath, Path lineupFilePath, Lineup current, Guild guild,
                                                 FortificationType fortificationType) {
        if (lineupFilePath != null && LineupFiles.isOriginal(lineupFilePath)) {
            return Comparison.of(ComparisonState.IS_ORIGINAL);
        }
        Optional<Lineup> loaded = LineupFiles.loadOriginal(guildFilePath == null ? null : guildFilePath.getParent());
        if (loaded.isEmpty()) {
            return Comparison.of(ComparisonState.NO_ORIGINAL);
        }
        Lineup original = loaded.get();
        LineupComparisonService.Summary summary = LineupComparisonService.compare(original, current, guild).summary();
        boolean heroes = fortificationType == FortificationType.HERO;
        return new Comparison(ComparisonState.COMPARED,
                summary.totalPowerBefore(), summary.totalPowerAfter(),
                heroes ? summary.heroPowerBefore() : summary.titanPowerBefore(),
                heroes ? summary.heroPowerAfter() : summary.titanPowerAfter(),
                summary.movedCount(), summary.addedCount(), summary.removedCount());
    }

    /**
     * Occupancy, power and buff of a fortification in a lineup.
     *
     * @param buffPercent null for a fortification without a buff
     */
    public record FortificationFacts(int filledSlots, int capacity, int totalPower, Integer buffPercent) {
    }

    /** The facts of {@code fortification} in {@code lineup} - computed like the fortification map does. */
    public static FortificationFacts factsOf(Fortification fortification, Lineup lineup, Guild guild) {
        int filled = 0;
        int power = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.fortificationId().equals(fortification.id())) {
                filled++;
                power += BuffCalculationService.totalPowerOf(entry, guild);
            }
        }
        Integer buffPercent = fortification.buff() == null ? null
                : BuffCalculationService.calculateBuffForFortification(fortification.id(), lineup, guild, fortification);
        return new FortificationFacts(Math.min(filled, fortification.capacity()), fortification.capacity(), power, buffPercent);
    }
}
