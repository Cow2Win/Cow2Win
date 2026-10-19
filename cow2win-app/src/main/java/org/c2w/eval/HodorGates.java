package org.c2w.eval;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The "doors" of the HODOR algorithms, derived from the catalog instead of being hard-wired: every
 * distinct, non-empty {@link Fortification#prerequisites()} list is one {@link Gate} (today
 * {@code [bridge]}, the three titan bastions, {@code [heros-bridge]} and the two temples). Since
 * prerequisites are an OR-relation, the attacker passes a gate as soon as ANY ONE of its
 * fortifications falls.
 */
final class HodorGates {

    private HodorGates() {
    }

    /**
     * One gate group.
     *
     * @param fortificationIds the fortifications of the group, in catalog prerequisite order
     * @param type             the fortification type of the group - decides which run (heroes/titans) it belongs to
     * @param pointsBehind     sum of the capture bonuses of every fortification (both types) that can no
     *                         longer be reached if no fortification of this group falls
     */
    record Gate(List<String> fortificationIds, FortificationType type, int pointsBehind) {
        Gate {
            fortificationIds = List.copyOf(fortificationIds);
        }
    }

    /**
     * Every gate of {@code catalog}, by points behind descending (ties by the first id), so the
     * order is stable for the start distribution. Prerequisite ids missing from the catalog are
     * ignored; a group of mixed types gets the type of its first fortification.
     */
    static List<Gate> fromCatalog(List<Fortification> catalog) {
        Map<String, Fortification> byId = catalog.stream()
                .collect(Collectors.toMap(Fortification::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<List<String>> groups = new LinkedHashSet<>();
        for (Fortification fortification : catalog) {
            List<String> known = fortification.prerequisites().stream().filter(byId::containsKey).toList();
            if (!known.isEmpty()) {
                groups.add(known);
            }
        }
        List<Gate> gates = new ArrayList<>();
        for (List<String> group : groups) {
            gates.add(new Gate(group, byId.get(group.get(0)).type(), pointsBehind(new HashSet<>(group), byId)));
        }
        gates.sort(Comparator.comparingInt(Gate::pointsBehind).reversed()
                .thenComparing(gate -> gate.fortificationIds().get(0)));
        return gates;
    }

    /**
     * Capture bonus of every fortification outside {@code group} that is unreachable while no
     * fortification of {@code group} falls: reachable are the fortifications without prerequisites
     * and, repeatedly, every fortification with at least one reachable prerequisite (OR-logic).
     */
    private static int pointsBehind(Set<String> group, Map<String, Fortification> byId) {
        Set<String> reachable = new HashSet<>();
        boolean grown = true;
        while (grown) {
            grown = false;
            for (Fortification fortification : byId.values()) {
                if (group.contains(fortification.id()) || reachable.contains(fortification.id())) {
                    continue;
                }
                boolean open = fortification.prerequisites().isEmpty()
                        || fortification.prerequisites().stream().anyMatch(reachable::contains);
                if (open) {
                    reachable.add(fortification.id());
                    grown = true;
                }
            }
        }
        return byId.values().stream()
                .filter(fortification -> !group.contains(fortification.id()) && !reachable.contains(fortification.id()))
                .mapToInt(Fortification::captureBonus)
                .sum();
    }
}
