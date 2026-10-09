package org.c2w.infra;

import org.c2w.domain.CowScoreBonuses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** The CowScore bonuses in {@link Config} - in memory only, never saved to config.properties. */
class ConfigCowScoreBonusesTest {

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
    @DisplayName("Missing keys: the defaults; no change time yet")
    void missingKeys() {
        assertEquals(CowScoreBonuses.DEFAULTS, Config.getCowScoreBonuses());
        assertNull(Config.getCowScoreBonusesChangedAt());
    }

    @Test
    @DisplayName("\"abc\" -> default, 0.5 -> 1.0, 5 -> 2.0 / 3.0, buff 1.2 with combo 1.8 -> buff 1.8")
    void invalidAndOutOfRange() {
        Properties properties = new Properties();
        properties.setProperty("cowScore.petMarkedPercent", "abc");
        properties.setProperty("cowScore.relationPercent", "0.5");
        properties.setProperty("cowScore.totemPercent", "5");
        properties.setProperty("cowScore.elementMatchPercent", "5");
        properties.setProperty("cowScore.roleMatchPercent", "1.2");
        properties.setProperty("cowScore.comboPercent", "1.8");
        Config.restore(properties);

        CowScoreBonuses bonuses = Config.getCowScoreBonuses();

        assertEquals(1.25, bonuses.petPercent(), EPS);
        assertEquals(1.0, bonuses.relationPercent(), EPS);
        assertEquals(2.0, bonuses.totemPercent(), EPS);
        assertEquals(3.0, bonuses.elementPercent(), EPS);
        assertEquals(1.8, bonuses.comboPercent(), EPS);
        assertEquals(1.8, bonuses.rolePercent(), EPS, "the role buff is raised to the combo");
    }

    @Test
    @DisplayName("Saving and loading gives the same values")
    void roundTrip() throws IOException {
        CowScoreBonuses bonuses = new CowScoreBonuses(2.5, 2.25, 1.1, 1.3, 1.45, 2.0, 1.05);
        Config.setCowScoreBonuses(bonuses);

        StringWriter file = new StringWriter();
        Config.snapshot().store(file, null);
        Properties loaded = new Properties();
        loaded.load(new StringReader(file.toString()));
        Config.restore(loaded);

        assertEquals(bonuses, Config.getCowScoreBonuses());
    }

    @Test
    @DisplayName("The change time is only set if a value actually changes")
    void changedAtOnlyOnRealChange() {
        assertFalse(Config.setCowScoreBonuses(CowScoreBonuses.DEFAULTS));
        assertNull(Config.getCowScoreBonusesChangedAt());

        assertTrue(Config.setCowScoreBonuses(new CowScoreBonuses(1.5, 1.5, 1.25, 1.25, 1.25, 1.5, 1.25)));
        assertNotNull(Config.getCowScoreBonusesChangedAt());

        Properties withOldTime = Config.snapshot();
        withOldTime.setProperty("cowScore.bonusesChangedAt", "2026-01-01T00:00:00Z");
        Config.restore(withOldTime);
        assertFalse(Config.setCowScoreBonuses(new CowScoreBonuses(1.5, 1.5, 1.25, 1.25, 1.25, 1.5, 1.25)));
        assertEquals("2026-01-01T00:00:00Z", Config.getCowScoreBonusesChangedAt().toString());
    }
}
