package org.c2w.service.journal;

import java.util.*;

/**
 * Which proposals of a {@link TeamBuildPlan} to apply, and where to.
 *
 * @param targets selected proposal id -> target (new team or filling an empty team)
 */
public record TeamBuildSelection(Map<String, TeamBuildPlan.Target> targets) {

    public TeamBuildSelection {
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
    }

    /** Nothing selected. */
    public static TeamBuildSelection none() {
        return new TeamBuildSelection(Map.of());
    }

    /** The preselected proposals with their suggested targets. */
    public static TeamBuildSelection preselected(TeamBuildPlan plan) {
        Map<String, TeamBuildPlan.Target> targets = new LinkedHashMap<>();
        for (TeamBuildPlan.Proposal p : plan.proposals()) {
            if (p.preselected()) {
                targets.put(p.id(), p.suggestedTarget());
            }
        }
        return new TeamBuildSelection(targets);
    }

    /** This selection plus {@code proposalId} with {@code target}. */
    public TeamBuildSelection with(String proposalId, TeamBuildPlan.Target target) {
        Map<String, TeamBuildPlan.Target> copy = new LinkedHashMap<>(targets);
        copy.put(proposalId, Objects.requireNonNull(target));
        return new TeamBuildSelection(copy);
    }

    /** This selection without {@code proposalId}. */
    public TeamBuildSelection without(String proposalId) {
        Map<String, TeamBuildPlan.Target> copy = new LinkedHashMap<>(targets);
        copy.remove(proposalId);
        return new TeamBuildSelection(copy);
    }
}
