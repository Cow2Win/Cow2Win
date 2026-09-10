package org.c2w.data.repository;

import org.c2w.data.model.Fortification;
import org.c2w.util.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the load-time data validation added 2026-09-10 (see
 * cow2win-verbesserungsvorschlaege.md, item 4: "Datenvalidierung beim Laden") -
 * {@link FortificationRepository#validateCatalog} and the per-entry warnings in
 * {@link FortificationRepository#parseFortificationsJson}. These don't throw (the app's
 * existing convention is to log and continue, see {@code PATCH-CHECKLIST.md} and
 * {@link BestPossibleLineupAlgorithm}'s own graceful handling of unknown/cyclic
 * prerequisites), so the only thing to assert is that {@link Logger} receives the
 * expected message for each kind of problem - and nothing extra for valid data.
 *
 * <p>{@link Logger} is a process-wide singleton with no reset, so every test here
 * registers its own listener and immediately clears the replayed history before acting,
 * then only inspects entries logged after that point.
 */
class FortificationRepositoryValidationTest {

    private static List<String> captureLogSince() {
        List<String> captured = new ArrayList<>();
        Logger.addListener(captured::add);
        captured.clear(); // drop the immediate replay of everything logged before this test
        return captured;
    }

    @Test
    @DisplayName("The real fortifications.json loads with zero validation warnings")
    void realCatalogHasNoValidationWarnings() {
        List<String> log = captureLogSince();

        FortificationRepository.resetCache();
        FortificationRepository.findAll();

        assertTrue(log.isEmpty(), "real fortifications.json should not trigger any validation warning: " + log);
    }

    @Test
    @DisplayName("A prerequisite referencing an unknown fortification id is logged")
    void unknownPrerequisiteIsLogged() {
        List<String> log = captureLogSince();

        Map<String, Fortification> catalog = FortificationRepository.parseFortificationsJson(
                "[{\"id\":\"a\",\"type\":\"HERO\",\"capacity\":3,\"captureBonus\":10,\"row\":0,\"column\":0,"
                        + "\"prerequisites\":[\"does-not-exist\"],\"strategicImportance\":1}]");
        FortificationRepository.validateCatalog(catalog);

        assertTrue(log.stream().anyMatch(s -> s.contains("'a'") && s.contains("does-not-exist")),
                "expected an unknown-prerequisite warning, got: " + log);
    }

    @Test
    @DisplayName("A prerequisite cycle is logged exactly once, even with an unrelated third entry")
    void cycleIsLoggedExactlyOnce() {
        List<String> log = captureLogSince();

        Map<String, Fortification> catalog = FortificationRepository.parseFortificationsJson(
                "["
                        + "{\"id\":\"a\",\"type\":\"HERO\",\"capacity\":3,\"captureBonus\":10,\"row\":0,\"column\":0,"
                        + "\"prerequisites\":[],\"strategicImportance\":1},"
                        + "{\"id\":\"b\",\"type\":\"HERO\",\"capacity\":3,\"captureBonus\":10,\"row\":0,\"column\":1,"
                        + "\"prerequisites\":[\"c\"],\"strategicImportance\":1},"
                        + "{\"id\":\"c\",\"type\":\"HERO\",\"capacity\":3,\"captureBonus\":10,\"row\":0,\"column\":2,"
                        + "\"prerequisites\":[\"b\"],\"strategicImportance\":1}"
                        + "]");
        FortificationRepository.validateCatalog(catalog);

        long cycleWarnings = log.stream().filter(s -> s.contains("cyclic prerequisites detected")).count();
        assertEquals(1L, cycleWarnings, "expected exactly one cycle warning, got: " + log);
    }

    @Test
    @DisplayName("An unknown fortification type is logged and the entry is skipped")
    void unknownTypeIsLoggedAndSkipped() {
        List<String> log = captureLogSince();

        Map<String, Fortification> catalog = FortificationRepository.parseFortificationsJson(
                "[{\"id\":\"a\",\"type\":\"NOT_A_REAL_TYPE\",\"capacity\":3,\"captureBonus\":10,\"row\":0,\"column\":0,"
                        + "\"prerequisites\":[],\"strategicImportance\":1}]");

        assertTrue(catalog.isEmpty(), "entry with an unknown type must be skipped, not silently kept");
        assertTrue(log.stream().anyMatch(s -> s.contains("'a'") && s.contains("unknown type")),
                "expected an unknown-type warning, got: " + log);
    }

    @Test
    @DisplayName("A fortification entry missing a required field is logged and skipped")
    void missingRequiredFieldIsLoggedAndSkipped() {
        List<String> log = captureLogSince();

        // No "type" field at all.
        Map<String, Fortification> catalog = FortificationRepository.parseFortificationsJson(
                "[{\"id\":\"a\",\"capacity\":3,\"captureBonus\":10,\"row\":0,\"column\":0,"
                        + "\"prerequisites\":[],\"strategicImportance\":1}]");

        assertTrue(catalog.isEmpty(), "entry missing a required field must be skipped, not silently kept");
        assertTrue(log.stream().anyMatch(s -> s.contains("missing required field")),
                "expected a missing-required-field warning, got: " + log);
    }
}
