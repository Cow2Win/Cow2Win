package org.c2w.eval;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The gate groups of the HODOR algorithms, derived from the real catalog. */
class HodorGatesTest {

    private static Fortification fortification(String id, int captureBonus, String... prerequisites) {
        return new Fortification(id, FortificationType.TITAN, 4, captureBonus, 0, 0, null, List.of(prerequisites), 1);
    }

    @Test
    @DisplayName("Real catalog: four gate groups with their run and points behind, by points behind descending")
    void realCatalog() {
        List<HodorGates.Gate> gates = HodorGates.fromCatalog(FortificationRepository.findAll());

        assertEquals(List.of(
                new HodorGates.Gate(List.of("bridge"), FortificationType.TITAN, 3520),
                new HodorGates.Gate(List.of("bastion-of-fire", "gates-of-nature", "bastion-of-ice"),
                        FortificationType.TITAN, 2310),
                new HodorGates.Gate(List.of("heros-bridge"), FortificationType.HERO, 1280),
                new HodorGates.Gate(List.of("sun-temple", "moon-temple"), FortificationType.TITAN, 410)
        ), gates);
    }

    @Test
    @DisplayName("OR-logic: a fortification behind two gates stays reachable through the other one")
    void orLogic() {
        List<Fortification> catalog = List.of(
                fortification("a", 10),
                fortification("b", 20),
                fortification("c", 100, "a"),
                fortification("d", 1000, "c", "b"));

        List<HodorGates.Gate> gates = HodorGates.fromCatalog(catalog);

        assertEquals(List.of(
                new HodorGates.Gate(List.of("c", "b"), FortificationType.TITAN, 1000),
                new HodorGates.Gate(List.of("a"), FortificationType.TITAN, 100)
        ), gates, "\"d\" is still reachable via \"b\" if \"a\" holds - only \"c\" is behind \"a\"");
    }

    @Test
    @DisplayName("Unknown prerequisite ids are ignored, no prerequisites - no gates")
    void unknownAndNone() {
        assertEquals(List.of(), HodorGates.fromCatalog(List.of(fortification("a", 10, "missing"))));
        assertEquals(List.of(), HodorGates.fromCatalog(List.of(fortification("a", 10))));
    }
}
