package org.c2w.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which entries of a change plan ({@link ChangePlanOutline.Item#id()}) are checked as "done in
 * the game", for one target. A check is only a note - it changes nothing else. Immutable.
 *
 * @param target     the plan's target, e.g. the target lineup's file name, {@code "current:{file name}"}
 *                   or {@code "algorithm"}; "" for none
 * @param checkedIds the checked entry ids
 */
public record ChangePlanChecks(String target, Set<String> checkedIds) {

    public static final ChangePlanChecks EMPTY = new ChangePlanChecks("", Set.of());

    public ChangePlanChecks {
        target = target == null ? "" : target;
        checkedIds = Set.copyOf(checkedIds);
    }

    /**
     * The checks for a plan rebuilt for {@code newTarget} with the entries {@code itemIds}: for
     * the same target the checks of entries that still exist, for another target none.
     */
    public ChangePlanChecks forPlan(String newTarget, Collection<String> itemIds) {
        if (!target.equals(newTarget == null ? "" : newTarget)) {
            return new ChangePlanChecks(newTarget, Set.of());
        }
        Set<String> kept = new LinkedHashSet<>(checkedIds);
        kept.retainAll(Set.copyOf(itemIds));
        return new ChangePlanChecks(target, kept);
    }

    /** These checks with {@code id} checked or unchecked. */
    public ChangePlanChecks with(String id, boolean checked) {
        Set<String> ids = new LinkedHashSet<>(checkedIds);
        if (checked) {
            ids.add(id);
        } else {
            ids.remove(id);
        }
        return new ChangePlanChecks(target, ids);
    }

    /** These checks without {@code ids} (e.g. the entries just applied to live). */
    public ChangePlanChecks without(Collection<String> ids) {
        Set<String> rest = new LinkedHashSet<>(checkedIds);
        rest.removeAll(Set.copyOf(ids));
        return new ChangePlanChecks(target, rest);
    }

    public boolean isChecked(String id) {
        return checkedIds.contains(id);
    }
}
