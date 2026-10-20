package org.c2w.gui.stage;

import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.Lineup;
import org.c2w.domain.HodorSettings;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.eval.MaxBuffAlgorithm;
import org.c2w.eval.ShieldAlgorithm;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link AlgorithmInfoPanel}: name, description and the settings each algorithm uses - headless. */
class AlgorithmInfoPanelTest {

    private static final int RESERVE = 7;
    private static final double DAMAGE_RESIST_WEIGHT = 1.35;

    private static HodorSettings settings() {
        Map<BuffEffect, Double> weights = new EnumMap<>(BuffEffect.class);
        weights.put(BuffEffect.DAMAGE_RESIST, DAMAGE_RESIST_WEIGHT);
        return new HodorSettings(RESERVE, weights);
    }

    private static String text(String key) {
        return LanguageService.displayName(key);
    }

    private static String weight(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(LanguageService.locale());
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    @ParameterizedTest
    @EnumSource(Lineup.TeamType.class)
    @DisplayName("Name, full description and the current values of the settings each algorithm uses")
    void contentPerAlgorithm(Lineup.TeamType teamType) {
        AlgorithmInfoPanel panel = new AlgorithmInfoPanel(AlgorithmInfoPanelTest::settings);
        for (LineupAlgorithm algorithm : LineupAlgorithms.forAssignment(teamType)) {
            panel.show(algorithm);
            List<String> texts = panel.texts();
            String name = algorithm.displayName();
            assertEquals(algorithm.localizedName(), texts.get(0), name);
            assertTrue(texts.contains(AlgorithmDescriptions.forAlgorithm(algorithm)), name);
            assertFalse(AlgorithmDescriptions.forAlgorithm(algorithm).isBlank(), name);
            assertTrue(texts.contains(text("algorithmInfo.settings")), name);
            assertTrue(texts.contains(text("algorithmInfo.changeHint")), name);

            boolean reserve = texts.contains(text("settings.algorithms.reserve"));
            boolean weights = texts.contains(text("settings.algorithms.weights"));
            boolean cowScore = texts.contains(text("algorithmInfo.cowScoreBonuses"));
            boolean noReserve = texts.contains(text("algorithmInfo.noReserve"));
            switch (name) {
                case "HODOR" -> assertTrue(reserve && weights && !cowScore && !noReserve, texts.toString());
                case "HODOR (CowScore)" -> assertTrue(reserve && !weights && cowScore && !noReserve, texts.toString());
                case ShieldAlgorithm.DISPLAY_NAME -> assertTrue(!reserve && weights && !cowScore && noReserve,
                        texts.toString());
                case MaxBuffAlgorithm.DISPLAY_NAME -> assertTrue(reserve && !weights && !cowScore && !noReserve,
                        texts.toString());
                default -> fail("unexpected algorithm " + name);
            }
            if (reserve) {
                assertEquals(String.valueOf(RESERVE),
                        texts.get(texts.indexOf(text("settings.algorithms.reserve")) + 1), name);
            }
            if (weights) {
                String effect = text("buffEffect." + BuffEffect.DAMAGE_RESIST.name());
                assertEquals(weight(DAMAGE_RESIST_WEIGHT), texts.get(texts.indexOf(effect) + 1), name);
                for (BuffEffect other : BuffEffect.values()) {
                    assertTrue(texts.contains(text("buffEffect." + other.name())), other.name());
                }
            }
        }
    }
}
