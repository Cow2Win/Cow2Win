package org.c2w.eval;

import org.c2w.data.model.Fortification;

import java.util.*;

/**
 * The unlock depth of every fortification: the smallest number of captures before it can be
 * attacked. A fortification without prerequisites has depth 0; otherwise its depth is 1 + the
 * SMALLEST depth of its prerequisites, since prerequisites are an OR-relation (capturing any one of
 * them unlocks it). Prerequisites cross the types (the hero "bastion" needs a titan bastion), so the
 * depth is always computed over the WHOLE catalog.
 *
 * <p>Robust against catalog mistakes (the repository already warns about them when loading):
 * prerequisite ids missing from the catalog are ignored, like in {@link HodorGates} - a fortification
 * with only unknown prerequisites therefore counts as depth 0. A fortification that can never be
 * reached (it only depends on a cycle) is put at the very back, one deeper than the deepest reachable
 * fortification - an attacker never gets there, so it is the least urgent to defend.
 */
final class FortificationDepths {

    private FortificationDepths() {
    }

    /** The depth of every fortification of {@code catalog}, by id. */
    static Map<String, Integer> fromCatalog(List<Fortification> catalog) {
        Set<String> ids = new HashSet<>();
        for (Fortification fortification : catalog) {
            ids.add(fortification.id());
        }
        Map<String, Integer> depths = new LinkedHashMap<>();
        for (Fortification fortification : catalog) {
            if (fortification.prerequisites().stream().noneMatch(ids::contains)) {
                depths.put(fortification.id(), 0);
            }
        }
        // Breadth-first: depth d + 1 for everything unlocked by a fortification of depth d.
        int depth = 0;
        boolean grown = !depths.isEmpty();
        while (grown) {
            grown = false;
            Map<String, Integer> next = new LinkedHashMap<>();
            for (Fortification fortification : catalog) {
                if (depths.containsKey(fortification.id()) || next.containsKey(fortification.id())) {
                    continue;
                }
                final int current = depth;
                if (fortification.prerequisites().stream().anyMatch(id -> Objects.equals(depths.get(id), current))) {
                    next.put(fortification.id(), depth + 1);
                }
            }
            depths.putAll(next);
            grown = !next.isEmpty();
            depth++;
        }
        int unreachable = depths.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        for (Fortification fortification : catalog) {
            depths.putIfAbsent(fortification.id(), unreachable);
        }
        return depths;
    }
}
