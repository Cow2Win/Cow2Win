package org.c2w.eval;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link AlgorithmDescriptions#summaryFor} and {@link LineupAlgorithm#usedSettings()}. */
class AlgorithmDescriptionsTest {

    private static final List<String> LANGUAGES = List.of("deutsch", "english", "francais");
    private static final List<String> SUMMARY_KEYS = List.of(
            "algorithm.hodor.summary", "algorithm.hodorCowScore.summary", "algorithm.shield.summary");

    @ParameterizedTest
    @EnumSource(Lineup.TeamType.class)
    @DisplayName("Every assigning algorithm has a summary in the configured language")
    void summaryForEveryAlgorithm(Lineup.TeamType teamType) {
        for (LineupAlgorithm algorithm : LineupAlgorithms.forAssignment(teamType)) {
            String summary = AlgorithmDescriptions.summaryFor(algorithm);
            assertFalse(summary.isBlank(), algorithm.displayName());
            assertFalse(summary.startsWith("algorithm."), algorithm.displayName());
        }
    }

    @Test
    @DisplayName("The summaries exist in every language and are one short sentence")
    void summariesInEveryLanguage() {
        for (String language : LANGUAGES) {
            for (String key : SUMMARY_KEYS) {
                String text = LanguageService.textIn(language, key);
                assertNotNull(text, language + " " + key);
                assertFalse(text.isBlank(), language + " " + key);
                assertTrue(text.length() <= 110, language + " " + key + ": " + text);
            }
        }
    }

    @Test
    @DisplayName("An unknown algorithm has an empty summary, not the raw key")
    void unknownAlgorithm() {
        LineupAlgorithm unknown = new LineupAlgorithm() {
            @Override
            public String displayName() {
                return "Unknown";
            }

            @Override
            public Lineup.TeamType teamType() {
                return Lineup.TeamType.HERO;
            }

            @Override
            public Lineup run(Lineup lineup, Guild guild) {
                return lineup;
            }
        };
        assertEquals("", AlgorithmDescriptions.summaryFor(unknown));
        assertTrue(unknown.usedSettings().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Lineup.TeamType.class)
    @DisplayName("HODOR uses reserve and weights, HODOR (CowScore) reserve and bonuses, S.H.I.E.L.D only weights")
    void usedSettings(Lineup.TeamType teamType) {
        for (LineupAlgorithm algorithm : LineupAlgorithms.forType(teamType)) {
            EnumSet<AlgorithmSetting> expected = switch (algorithm.displayName()) {
                case "HODOR" -> EnumSet.of(AlgorithmSetting.RESERVE, AlgorithmSetting.BUFF_WEIGHTS);
                case "HODOR (CowScore)" -> EnumSet.of(AlgorithmSetting.RESERVE, AlgorithmSetting.COW_SCORE_BONUSES);
                case ShieldAlgorithm.DISPLAY_NAME -> EnumSet.of(AlgorithmSetting.BUFF_WEIGHTS);
                case ManualLineupAlgorithm.DISPLAY_NAME -> EnumSet.noneOf(AlgorithmSetting.class);
                default -> throw new AssertionError("unexpected algorithm " + algorithm.displayName());
            };
            assertEquals(expected, algorithm.usedSettings(), algorithm.displayName());
        }
    }
}
