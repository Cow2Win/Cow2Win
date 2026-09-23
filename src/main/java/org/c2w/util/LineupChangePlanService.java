package org.c2w.util;

import org.c2w.util.LineupComparisonService.LineupComparison;
import org.c2w.util.LineupComparisonService.TeamDiff;
import org.c2w.util.LineupComparisonService.TeamKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a {@link LineupComparison} (Original lineup vs. a target lineup) into
 * an ordered list of concrete, actionable change steps - the "what do I
 * actually have to re-arrange on the Hero Wars side" guide (added
 * 2026-09-23). Purely a projection of {@link LineupComparisonService}'s
 * per-team diff: every {@link TeamDiff} that isn't {@link TeamDiff.Status#UNCHANGED}
 * becomes exactly one {@link ChangeStep}; unchanged teams produce none.
 *
 * <p>Steps are ordered {@link ChangeType#REMOVE} first, then
 * {@link ChangeType#MOVE}, then {@link ChangeType#PLACE} - so a player who
 * works through the list top to bottom frees up fortification slots before
 * trying to move or place teams into them, which avoids running into a
 * "fortification full" wall mid-way. Within one type the steps are sorted by
 * the relevant fortification's display name (the one being vacated for
 * REMOVE/MOVE, the one being filled for PLACE) and then by team member id,
 * for a stable, readable order. This is a projection only: it never mutates
 * the lineups, the guild or {@link AppContext}.
 */
public final class LineupChangePlanService {

    private LineupChangePlanService() {
    }

    /** The kind of in-game action a {@link ChangeStep} describes. Declaration order IS the step order (see class Javadoc). */
    public enum ChangeType {
        /** Team is assigned in Original but not in the target - pull it off its fortification. */
        REMOVE,
        /** Team is assigned to a different fortification in the target - move it there. */
        MOVE,
        /** Team is unassigned in Original but assigned in the target - place it. */
        PLACE
    }

    /**
     * One concrete change. {@code fromFortificationId} is the fortification
     * the team currently sits on in Original (null for {@link ChangeType#PLACE}),
     * {@code toFortificationId} the one it should end up on in the target
     * (null for {@link ChangeType#REMOVE}).
     */
    public record ChangeStep(ChangeType type, TeamKey teamKey,
                             String fromFortificationId, String toFortificationId) {
    }

    /**
     * Builds the ordered change plan from a comparison whose {@code before}
     * is the Original lineup and whose {@code after} is the target lineup.
     */
    public static List<ChangeStep> from(LineupComparison comparison) {
        if (comparison == null) {
            throw new IllegalArgumentException("comparison must not be null");
        }
        List<ChangeStep> steps = new ArrayList<>();
        for (TeamDiff diff : comparison.teamDiffs()) {
            switch (diff.status()) {
                case REMOVED -> steps.add(new ChangeStep(ChangeType.REMOVE, diff.teamKey(),
                        diff.fortificationIdBefore(), null));
                // UPDATED is unreachable in practice (see TeamDiff Javadoc) but
                // kept exhaustive; treated like MOVE, same fortification then.
                case MOVED, UPDATED -> steps.add(new ChangeStep(ChangeType.MOVE, diff.teamKey(),
                        diff.fortificationIdBefore(), diff.fortificationIdAfter()));
                case ADDED -> steps.add(new ChangeStep(ChangeType.PLACE, diff.teamKey(),
                        null, diff.fortificationIdAfter()));
                case UNCHANGED -> {
                    // no action needed
                }
            }
        }
        steps.sort(Comparator
                .comparingInt((ChangeStep s) -> s.type().ordinal())
                .thenComparing(s -> LineupComparisonService.fortificationDisplayName(relevantFortificationId(s)))
                .thenComparing(s -> s.teamKey().teamMemberId(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(s -> s.teamKey().teamIndex()));
        return steps;
    }

    /** The fortification a step is anchored on for sorting: the one being vacated for REMOVE/MOVE, the one being filled for PLACE. */
    private static String relevantFortificationId(ChangeStep step) {
        String id = step.type() == ChangeType.PLACE ? step.toFortificationId() : step.fromFortificationId();
        return id == null ? "" : id;
    }
}
