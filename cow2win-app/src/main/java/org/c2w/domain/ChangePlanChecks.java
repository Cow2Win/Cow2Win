package org.c2w.domain;

import org.c2w.data.model.FortificationType;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which entries of a change plan ({@link ChangePlanOutline.Item#id()}) are checked as "done in
 * the game", for one target, and for which fortification types checked entries of this target
 * were already applied to live. A check is only a note - it changes nothing else. Immutable.
 *
 * @param target       the plan's target, e.g. the target lineup's file name, {@code "current:{file name}"}
 *                     or {@code "algorithm"}; "" for none
 * @param checkedIds   the checked entry ids (of both fortification types)
 * @param appliedTypes the fortification types "apply to live" was already used for with this target
 */
public record ChangePlanChecks(String target, Set<String> checkedIds, Set<FortificationType> appliedTypes) {

    public static final ChangePlanChecks EMPTY = new ChangePlanChecks("", Set.of(), Set.of());

    public ChangePlanChecks {
        target = target == null ? "" : target;
        checkedIds = Set.copyOf(checkedIds);
        appliedTypes = appliedTypes == null ? Set.of() : Set.copyOf(appliedTypes);
    }

    /** Checks without any applied type. */
    public ChangePlanChecks(String target, Set<String> checkedIds) {
        this(target, checkedIds, Set.of());
    }

    /**
     * The checks for a plan rebuilt for {@code newTarget} with the entries {@code itemIds}: for
     * the same target the checks of entries that still exist and the applied types, for another
     * target neither.
     */
    public ChangePlanChecks forPlan(String newTarget, Collection<String> itemIds) {
        if (!target.equals(newTarget == null ? "" : newTarget)) {
            return new ChangePlanChecks(newTarget, Set.of(), Set.of());
        }
        Set<String> kept = new LinkedHashSet<>(checkedIds);
        kept.retainAll(Set.copyOf(itemIds));
        return new ChangePlanChecks(target, kept, appliedTypes);
    }

    /** These checks with {@code id} checked or unchecked. */
    public ChangePlanChecks with(String id, boolean checked) {
        Set<String> ids = new LinkedHashSet<>(checkedIds);
        if (checked) {
            ids.add(id);
        } else {
            ids.remove(id);
        }
        return new ChangePlanChecks(target, ids, appliedTypes);
    }

    /** These checks without {@code ids} (e.g. the entries just applied to live). */
    public ChangePlanChecks without(Collection<String> ids) {
        Set<String> rest = new LinkedHashSet<>(checkedIds);
        rest.removeAll(Set.copyOf(ids));
        return new ChangePlanChecks(target, rest, appliedTypes);
    }

    /** These checks with {@code type} noted as applied to live. */
    public ChangePlanChecks withApplied(FortificationType type) {
        Set<FortificationType> types = EnumSet.noneOf(FortificationType.class);
        types.addAll(appliedTypes);
        types.add(type);
        return new ChangePlanChecks(target, checkedIds, types);
    }

    public boolean isChecked(String id) {
        return checkedIds.contains(id);
    }

    /**
     * The fortification type that is "not live yet": if exactly one type was applied to live and
     * {@code outline} still has entries of the other one, that other type - otherwise null
     * (nothing applied, both applied, or nothing left to do for the other type).
     */
    public static FortificationType missingLiveType(ChangePlanOutline outline, Set<FortificationType> appliedTypes) {
        if (outline == null || appliedTypes == null || appliedTypes.size() != 1) {
            return null;
        }
        FortificationType applied = appliedTypes.iterator().next();
        FortificationType other = applied == FortificationType.HERO ? FortificationType.TITAN : FortificationType.HERO;
        return outline.ofType(other).isEmpty() ? null : other;
    }
}
