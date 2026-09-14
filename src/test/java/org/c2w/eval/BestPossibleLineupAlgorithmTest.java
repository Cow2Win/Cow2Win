package org.c2w.eval;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First unit test class for this project (added 2026-09-10, see
 * cow2win-verbesserungsvorschlaege.md). Covers
 * {@link BestPossibleLineupAlgorithm#computeUnlockDepths(List)} - a pure,
 * self-contained piece of the algorithm's logic with an already-documented
 * expected result (see clash-of-worlds-event-recherche.md), and the one
 * place a previous reading of this code got the semantics wrong (OR vs AND
 * on {@link Fortification#prerequisites()} - see the class javadoc there).
 *
 * Does NOT cover assignStrongestFirst/assignOne/the sorting criteria in
 * fillFortifications() or buffFitScore - see
 * {@link BestPossibleLineupAlgorithmAssignmentTest} for those; this class
 * focuses on unlock-depth computation only.
 */
class BestPossibleLineupAlgorithmTest {

    /** Builds a minimal, valid {@link Fortification} for these tests - only id and prerequisites matter here. */
    private static Fortification fort(String id, String... prerequisites) {
        return new Fortification(id, FortificationType.HERO, 3, 100, 0, 0, null, List.of(prerequisites), 1);
    }

    @Test
    @DisplayName("A fortification with no prerequisites has unlock depth 0")
    void initiallyAttackableFortsHaveDepthZero() {
        List<Fortification> catalog = List.of(fort("bridge"), fort("barracks"));

        Map<String, Integer> depths = BestPossibleLineupAlgorithm.computeUnlockDepths(catalog);

        assertEquals(0, depths.get("bridge"));
        assertEquals(0, depths.get("barracks"));
    }

    @Test
    @DisplayName("Depth is 1 + the SHALLOWEST prerequisite (OR-relation), not the deepest (AND)")
    void depthUsesShallowestPrerequisiteNotDeepest() {
        // A long chain on one side (depth 3) and a direct, already-unlocked
        // fortification on the other (depth 0) - "target" needs only ONE of
        // them captured (see Fortification#prerequisites' javadoc), so its
        // depth must come from the SHORT path (0 -> 1), not the long one
        // (3 -> 4). This is exactly the bug the research doc's first pass
        // got wrong (documented there as "fälschlich alle drei").
        List<Fortification> catalog = List.of(
                fort("start"),                       // depth 0
                fort("shallow", "start"),             // depth 1
                fort("deepChain1", "shallow"),        // depth 2
                fort("deepChain2", "deepChain1"),     // depth 3
                fort("target", "start", "deepChain2") // OR: min(0, 3) + 1 = 1, NOT max(0, 3) + 1 = 4
        );

        Map<String, Integer> depths = BestPossibleLineupAlgorithm.computeUnlockDepths(catalog);

        assertEquals(1, depths.get("target"), "OR-semantics: only the shallowest prerequisite should count");
        assertTrue(depths.get("target") != 4, "must not fall back to AND-semantics (deepest prerequisite)");
    }

    @Test
    @DisplayName("A prerequisite id that does not exist in the catalog is treated as already unlocked (depth 0)")
    void unknownPrerequisiteIsTreatedAsAlreadyUnlocked() {
        List<Fortification> catalog = List.of(fort("orphan", "does-not-exist"));

        Map<String, Integer> depths = BestPossibleLineupAlgorithm.computeUnlockDepths(catalog);

        assertEquals(1, depths.get("orphan"));
    }

    @Test
    @DisplayName("A cycle in the prerequisites graph does not cause infinite recursion (hand-maintained JSON can have editing mistakes)")
    void cyclicPrerequisitesDoNotRecurseForever() {
        // "a" needs "b" and "b" needs "a" - not valid game data, but the
        // catalog is hand-edited JSON (see fortifications.json), so this
        // must degrade gracefully instead of a StackOverflowError.
        List<Fortification> catalog = List.of(fort("a", "b"), fort("b", "a"));

        Map<String, Integer> depths = assertTimeoutPreemptively(() ->
                BestPossibleLineupAlgorithm.computeUnlockDepths(catalog));

        assertEquals(Set.of("a", "b"), depths.keySet());
        assertTrue(depths.get("a") >= 0, "depth must be a valid non-negative number even for a cyclic entry");
        assertTrue(depths.get("b") >= 0, "depth must be a valid non-negative number even for a cyclic entry");
    }

    @Test
    @DisplayName("Real catalog (fortifications.json) matches the depth table documented in clash-of-worlds-event-recherche.md")
    void realCatalogMatchesDocumentedDepths() {
        List<Fortification> catalog = FortificationRepository.findAll();
        assertEquals(20, catalog.size(), "this test pins the depth table below to exactly 20 fortifications");

        Map<String, Integer> depths = BestPossibleLineupAlgorithm.computeUnlockDepths(catalog);

        assertIdsAtDepth(depths, 0, "bridge", "barracks", "lighthouse", "mage-academy");
        assertIdsAtDepth(depths, 1, "bastion-of-fire", "bastion-of-ice", "gates-of-nature", "engineerium", "foundry", "spring-of-elements");
        assertIdsAtDepth(depths, 2, "heros-bridge", "altar-of-life", "ether-prism", "shooting-range", "bastion");
        assertIdsAtDepth(depths, 3, "alchemy-tower", "city-hall", "sun-temple", "moon-temple");
        assertIdsAtDepth(depths, 4, "citadel");
    }

    private static void assertIdsAtDepth(Map<String, Integer> depths, int expectedDepth, String... expectedIds) {
        Set<String> actual = depths.entrySet().stream()
                .filter(e -> e.getValue() == expectedDepth)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> expected = new TreeSet<>(List.of(expectedIds));
        assertEquals(expected, actual, "fortifications at unlock depth " + expectedDepth);
    }

    /** Small timeout wrapper so a real infinite-recursion regression fails fast instead of hanging the build. */
    private static <T> T assertTimeoutPreemptively(java.util.function.Supplier<T> supplier) {
        return org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2), supplier::get);
    }
}
