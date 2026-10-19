package org.c2w.infra;

import org.c2w.data.model.BuffEffect;
import org.c2w.domain.HodorSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** The HODOR settings in {@link Config} - in memory only, never saved to config.properties. */
class ConfigHodorSettingsTest {

    private static final double EPS = 1e-9;

    private Properties previous;

    @BeforeEach
    void startEmpty() {
        previous = Config.snapshot();
        Config.restore(new Properties());
        Logger.holdEntries(); // keeps "Config changed" entries out of the real log
    }

    @AfterEach
    void restorePrevious() {
        Logger.releaseHeldEntries();
        Config.restore(previous);
    }

    @Test
    @DisplayName("Missing keys: the defaults - reserve 3, DAMAGE_RESIST 1.0 ... MAGIC_DEFENSE_INCREASE 0.0")
    void missingKeys() {
        HodorSettings settings = Config.getHodorSettings();

        assertEquals(HodorSettings.DEFAULTS, settings);
        assertEquals(3, settings.reserve());
        assertEquals(1.0, settings.weight(BuffEffect.DAMAGE_RESIST), EPS);
        assertEquals(0.5, settings.weight(BuffEffect.ENERGY_GAIN), EPS);
        assertEquals(0.5, settings.weight(BuffEffect.SKILL_COOLDOWN_DECREASE), EPS);
        assertEquals(0.25, settings.weight(BuffEffect.HEALTH_INCREASE), EPS);
        assertEquals(0.2, settings.weight(BuffEffect.HEALING_INCREASE), EPS);
        assertEquals(0.1, settings.weight(BuffEffect.ARMOR_INCREASE), EPS);
        assertEquals(0.0, settings.weight(BuffEffect.MAGIC_DEFENSE_INCREASE), EPS);
    }

    @Test
    @DisplayName("Invalid -> default, out of range -> limit")
    void invalidAndOutOfRange() {
        Properties properties = new Properties();
        properties.setProperty("hodor.reserve", "abc");
        properties.setProperty("hodor.weight.DAMAGE_RESIST", "x");
        properties.setProperty("hodor.weight.ENERGY_GAIN", "NaN");
        properties.setProperty("hodor.weight.HEALTH_INCREASE", "5");
        properties.setProperty("hodor.weight.ARMOR_INCREASE", "-1");
        Config.restore(properties);

        HodorSettings settings = Config.getHodorSettings();

        assertEquals(3, settings.reserve());
        assertEquals(1.0, settings.weight(BuffEffect.DAMAGE_RESIST), EPS);
        assertEquals(0.5, settings.weight(BuffEffect.ENERGY_GAIN), EPS);
        assertEquals(2.0, settings.weight(BuffEffect.HEALTH_INCREASE), EPS);
        assertEquals(0.0, settings.weight(BuffEffect.ARMOR_INCREASE), EPS);

        properties.setProperty("hodor.reserve", "42");
        Config.restore(properties);
        assertEquals(10, Config.getHodorSettings().reserve());
        properties.setProperty("hodor.reserve", "-2");
        Config.restore(properties);
        assertEquals(0, Config.getHodorSettings().reserve());
    }

    @Test
    @DisplayName("Saving and loading gives the same values")
    void roundTrip() throws IOException {
        Map<BuffEffect, Double> weights = new EnumMap<>(BuffEffect.class);
        for (BuffEffect effect : BuffEffect.values()) {
            weights.put(effect, 0.05 * (effect.ordinal() + 1));
        }
        HodorSettings settings = new HodorSettings(7, weights).normalized();
        Config.setHodorSettings(settings);

        StringWriter file = new StringWriter();
        Config.snapshot().store(file, null);
        Properties loaded = new Properties();
        loaded.load(new StringReader(file.toString()));
        Config.restore(loaded);

        assertEquals(settings, Config.getHodorSettings());
        assertEquals(0.35, Config.getHodorSettings().weight(BuffEffect.ARMOR_INCREASE), EPS);
    }

    @Test
    @DisplayName("normalized(): missing weights get their default, values are clamped and rounded")
    void normalized() {
        HodorSettings settings = new HodorSettings(11, Map.of(BuffEffect.DAMAGE_RESIST, 1.234)).normalized();

        assertEquals(10, settings.reserve());
        assertEquals(1.23, settings.weight(BuffEffect.DAMAGE_RESIST), EPS);
        assertEquals(0.25, settings.weight(BuffEffect.HEALTH_INCREASE), EPS);
        assertEquals(BuffEffect.values().length, settings.weights().size());
    }
}
