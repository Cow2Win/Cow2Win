package org.c2w.infra;

import org.c2w.data.model.FortificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link Config#getLastFortificationType()} - in memory only, never saved to config.properties. */
class ConfigFortificationTypeTest {

    private FortificationType previous;

    @BeforeEach
    void rememberPrevious() {
        previous = Config.getLastFortificationType();
    }

    @AfterEach
    void restorePrevious() {
        Config.setLastFortificationType(previous);
    }

    @Test
    @DisplayName("Without a stored value the fortification type is HERO")
    void defaultsToHero() {
        Config.setLastFortificationType(null);
        assertEquals(FortificationType.HERO, Config.getLastFortificationType());
    }

    @Test
    @DisplayName("A stored fortification type is read back")
    void roundTrip() {
        Config.setLastFortificationType(FortificationType.TITAN);
        assertEquals(FortificationType.TITAN, Config.getLastFortificationType());
        Config.setLastFortificationType(FortificationType.HERO);
        assertEquals(FortificationType.HERO, Config.getLastFortificationType());
    }
}
