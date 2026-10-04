package org.c2w.service.journal;

import java.util.*;

/**
 * Which proposals of a {@link TeamBuildPlan} to apply, where to, and - for a
 * proposal without composition - a known composition the user chose.
 *
 * @param targets      selected proposal id -> target (new team or filling an empty team)
 * @param compositions proposal id -> chosen known composition (only for proposals without one)
 */
public record TeamBuildSelection(Map<String, TeamBuildPlan.Target> targets,
                                 Map<String, TeamBuildPlan.Composition> compositions) {

    public TeamBuildSelection {
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        compositions = Map.copyOf(compositions);
    }

    /** Nothing selected. */
    public static TeamBuildSelection none() {
        return new TeamBuildSelection(Map.of(), Map.of());
    }

    /** The preselected proposals with their suggested targets. */
    public static TeamBuildSelection preselected(TeamBuildPlan plan) {
        Map<String, TeamBuildPlan.Target> targets = new LinkedHashMap<>();
        for (TeamBuildPlan.Proposal p : plan.proposals()) {
            if (p.preselected()) {
                targets.put(p.id(), p.suggestedTarget());
            }
        }
        return new TeamBuildSelection(targets, Map.of());
    }

    /** This selection plus {@code proposalId} with {@code target}. */
    public TeamBuildSelection with(String proposalId, TeamBuildPlan.Target target) {
        Map<String, TeamBuildPlan.Target> copy = new LinkedHashMap<>(targets);
        copy.put(proposalId, Objects.requireNonNull(target));
        return new TeamBuildSelection(copy, compositions);
    }

    /** This selection without {@code proposalId}. */
    public TeamBuildSelection without(String proposalId) {
        Map<String, TeamBuildPlan.Target> copy = new LinkedHashMap<>(targets);
        copy.remove(proposalId);
        return new TeamBuildSelection(copy, compositions);
    }

    /** This selection with a known composition for {@code proposalId} ({@code null} removes it). */
    public TeamBuildSelection withComposition(String proposalId, TeamBuildPlan.Composition composition) {
        Map<String, TeamBuildPlan.Composition> copy = new HashMap<>(compositions);
        if (composition == null) {
            copy.remove(proposalId);
        } else {
            copy.put(proposalId, composition);
        }
        return new TeamBuildSelection(targets, copy);
    }
}
