package org.c2w.service.journal;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.TitanElement;
import org.c2w.service.journal.TeamBuildPlan.Composition;

import java.time.LocalDate;
import java.util.*;

/**
 * One team of a member in the defense log, matched to one of the member's stored
 * teams of the same kind. The user may pick another stored team of that kind as
 * target - therefore everything that depends on the stored team is kept per
 * possible {@link Target}.
 *
 * @param id                 stable id (member, kind, fortification, position)
 * @param memberId           the guild member
 * @param memberName         the member's name
 * @param logName            the defender's name in the log
 * @param kind               hero or titan team (from the fortification type)
 * @param fortificationId    catalog id of the fortification ({@code null} if unknown)
 * @param fortificationName  raw fortification name
 * @param position           position in the fortification
 * @param logPower           team power in the log
 * @param certainty          how sure the match with {@link #suggestedIndex()} is
 * @param suggestedIndex     the matched stored team
 * @param logHasUnits        true if the log has the defender's units for this team
 * @param logUnits           the units as composition if every name has a catalog id, else {@code null}
 * @param targets            every stored team of the member of this kind, by index
 */
public record SyncRow(String id, String memberId, String memberName, String logName, TeamKind kind,
                      String fortificationId, String fortificationName, int position, int logPower,
                      Certainty certainty, int suggestedIndex, boolean logHasUnits, Composition logUnits,
                      List<Target> targets) {

    public SyncRow {
        targets = List.copyOf(targets);
    }

    /** How sure a log team belongs to the stored team. */
    public enum Certainty {
        /** Same heroes/titans as the stored team (only if the log has units). */
        UNITS,
        /** Power within the sure tolerance (3 %). */
        SURE,
        /** Power within the unsure tolerance (10 %). */
        UNSURE,
        /** Another stored team is about as close - may be the other one. */
        AMBIGUOUS
    }

    /**
     * A stored team the row may change.
     *
     * @param index             team index
     * @param storedPower       its power
     * @param lastModified      its last change ({@code null} if unknown)
     * @param empty             true if it has no heroes/titans - only the power can be taken over
     * @param editedSinceBattle true if it was changed after the battle day
     * @param unitsChange       how the log's units differ from it; {@code null} if they do not or the log has none
     * @param unitsSelectable   true if the units can be taken over (log units resolved, different, team not empty)
     * @param lineupHint        where the open lineup has the team, if not at the log's fortification; {@code null} if fine
     */
    public record Target(int index, int storedPower, LocalDate lastModified, boolean empty, boolean editedSinceBattle,
                         UnitsChange unitsChange, boolean unitsSelectable, LineupHint lineupHint) {
    }

    /**
     * How the units of the log differ from a stored team - data for the display
     * ("+ Nova, − Sigurd", "Pet Vex → Albus", "Totems Fire → Light, Dark").
     *
     * @param added        hero/titan ids in the log but not in the team (log order)
     * @param removed      hero/titan ids in the team but not in the log
     * @param petBefore    hero team: the stored pet ({@code null} if none)
     * @param petAfter     hero team: the log's pet ({@code null} if none)
     * @param totemsBefore titan team: the stored totems
     * @param totemsAfter  titan team: the log's totems
     */
    public record UnitsChange(List<String> added, List<String> removed, String petBefore, String petAfter,
                              Set<TitanElement> totemsBefore, Set<TitanElement> totemsAfter) {
        public UnitsChange {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
            totemsBefore = totemsBefore == null || totemsBefore.isEmpty() ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(totemsBefore));
            totemsAfter = totemsAfter == null || totemsAfter.isEmpty() ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(totemsAfter));
        }

        public boolean unitsChanged() {
            return !added.isEmpty() || !removed.isEmpty();
        }

        public boolean petChanged() {
            return !Objects.equals(petBefore, petAfter);
        }

        public boolean totemsChanged() {
            return !totemsBefore.equals(totemsAfter);
        }
    }

    /**
     * The open lineup has the team somewhere else than the log (display only).
     *
     * @param kind                   other fortification, or not in the lineup at all
     * @param lineupFortificationId  {@link Kind#OTHER_FORTIFICATION}: the lineup's fortification
     */
    public record LineupHint(Kind kind, String lineupFortificationId) {

        public enum Kind {OTHER_FORTIFICATION, NOT_IN_LINEUP}
    }

    // --- convenience ---

    /** The stored team with this index. */
    public Optional<Target> target(int index) {
        return targets.stream().filter(t -> t.index() == index).findFirst();
    }

    /** The matched stored team. */
    public Target suggested() {
        return target(suggestedIndex).orElseThrow();
    }

    /** True if the log power differs from the stored team's. */
    public boolean powerChanged(int index) {
        return target(index).map(t -> t.storedPower() != logPower).orElse(false);
    }

    /** {@code |log − stored| / log} for the stored team with this index. */
    public double deviation(int index) {
        return deviation(logPower, target(index).map(Target::storedPower).orElse(0));
    }

    /** {@code |log − stored| / log}; 0 for two zeros, 1 for a zero log power otherwise. */
    public static double deviation(int logPower, int storedPower) {
        if (logPower <= 0) {
            return storedPower == logPower ? 0 : 1;
        }
        return Math.abs((long) logPower - storedPower) / (double) logPower;
    }

    /** True if nothing would change for the stored team with this index (same power, no unit difference). */
    public boolean unchanged(int index) {
        return !powerChanged(index) && target(index).map(t -> t.unitsChange() == null).orElse(true);
    }

    /** True if nothing would change for the matched team. */
    public boolean unchanged() {
        return unchanged(suggestedIndex);
    }

    /** True if the power is taken over by default: units or sure match, different power, not edited since. */
    public boolean preselectPower() {
        Target t = suggested();
        return (certainty == Certainty.UNITS || certainty == Certainty.SURE) && powerChanged(suggestedIndex)
                && !t.editedSinceBattle();
    }
}
