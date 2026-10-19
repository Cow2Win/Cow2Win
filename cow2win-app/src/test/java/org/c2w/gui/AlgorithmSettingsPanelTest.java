package org.c2w.gui;

import org.c2w.data.model.BuffEffect;
import org.c2w.domain.HodorSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The "Algorithms" area of the settings dialog - never shown. */
class AlgorithmSettingsPanelTest {

    private static final double EPS = 1e-9;

    private static AlgorithmSettingsPanel panel(HodorSettings initial) {
        return new AlgorithmSettingsPanel(new JComboBox<>(), new JComboBox<>(), initial);
    }

    @Test
    @DisplayName("Shows the given values and returns the changed ones")
    void valuesRoundTrip() {
        Map<BuffEffect, Double> weights = new EnumMap<>(HodorSettings.DEFAULTS.weights());
        weights.put(BuffEffect.ARMOR_INCREASE, 1.5);
        AlgorithmSettingsPanel panel = panel(new HodorSettings(5, weights));

        assertEquals(5, panel.reserveModel().getNumber().intValue());
        assertEquals(1.5, panel.weightModel(BuffEffect.ARMOR_INCREASE).getNumber().doubleValue(), EPS);

        panel.reserveModel().setValue(0);
        panel.weightModel(BuffEffect.DAMAGE_RESIST).setValue(0.75);
        HodorSettings settings = panel.hodorSettings();
        assertEquals(0, settings.reserve());
        assertEquals(0.75, settings.weight(BuffEffect.DAMAGE_RESIST), EPS);
        assertEquals(1.5, settings.weight(BuffEffect.ARMOR_INCREASE), EPS);
    }

    @Test
    @DisplayName("\"Defaults\" resets reserve and every weight")
    void defaults() {
        AlgorithmSettingsPanel panel = panel(new HodorSettings(9, Map.of(BuffEffect.ENERGY_GAIN, 2.0)));

        panel.resetToDefaults();

        assertEquals(HodorSettings.DEFAULTS, panel.hodorSettings());
    }

    @Test
    @DisplayName("Spinner ranges: reserve 0-10, weights 0.0-2.0 in steps of 0.05")
    void ranges() {
        AlgorithmSettingsPanel panel = panel(HodorSettings.DEFAULTS);

        assertEquals(0, panel.reserveModel().getMinimum());
        assertEquals(10, panel.reserveModel().getMaximum());
        SpinnerNumberModel weight = panel.weightModel(BuffEffect.HEALTH_INCREASE);
        assertEquals(0.0, ((Number) weight.getMinimum()).doubleValue(), EPS);
        assertEquals(2.0, ((Number) weight.getMaximum()).doubleValue(), EPS);
        assertEquals(0.05, weight.getStepSize().doubleValue(), EPS);
    }
}
