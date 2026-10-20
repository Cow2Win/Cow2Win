package org.c2w.eval;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link FortificationDepths} against the real catalog and a few broken catalogs. */
class FortificationDepthsTest {

    private static Fortification fortification(String id, String... prerequisites) {
        return new Fortification(id, FortificationType.TITAN, 3, 10, 0, 0, null, List.of(prerequisites), 1);
    }

    @Test
    @DisplayName("Real catalog: depth = 1 + the smallest depth of the prerequisites, across both types")
    void realCatalog() {
        Map<String, Integer> depths = FortificationDepths.fromCatalog(FortificationRepository.findAll());

        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("bridge", 0), Map.entry("barracks", 0), Map.entry("lighthouse", 0),
                Map.entry("mage-academy", 0),
                Map.entry("bastion-of-fire", 1), Map.entry("bastion-of-ice", 1), Map.entry("gates-of-nature", 1),
                Map.entry("spring-of-elements", 1), Map.entry("engineerium", 1), Map.entry("foundry", 1),
                Map.entry("heros-bridge", 2), Map.entry("altar-of-life", 2), Map.entry("ether-prism", 2),
                Map.entry("shooting-range", 2), Map.entry("bastion", 2),
                Map.entry("sun-temple", 3), Map.entry("moon-temple", 3), Map.entry("alchemy-tower", 3),
                Map.entry("city-hall", 3),
                Map.entry("citadel", 4));
        assertEquals(expected, depths);
    }

    @Test
    @DisplayName("Unknown prerequisites are ignored; a fortification only reachable through a cycle goes to the very back")
    void brokenCatalog() {
        List<Fortification> catalog = List.of(
                fortification("start"),
                fortification("next", "start", "missing"),
                fortification("orphan", "missing"),
                fortification("cycle-a", "cycle-b"),
                fortification("cycle-b", "cycle-a"),
                fortification("reached", "cycle-a", "next"));

        Map<String, Integer> depths = FortificationDepths.fromCatalog(catalog);

        assertEquals(Map.of("start", 0, "next", 1, "orphan", 0, "reached", 2, "cycle-a", 3, "cycle-b", 3), depths);
        assertEquals(Map.of(), FortificationDepths.fromCatalog(List.of()));
    }
}
