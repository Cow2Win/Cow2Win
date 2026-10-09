package org.c2w.infra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** The stale check settings of {@link Config} - in memory only, never saved to config.properties. */
class ConfigStaleCheckTest {

    private Properties previous;

    @BeforeEach
    void startEmpty() {
        previous = Config.snapshot();
        Config.restore(new Properties());
    }

    @AfterEach
    void restorePrevious() {
        Config.restore(previous);
    }

    @Test
    @DisplayName("Default: off, 30 days, not set by the user")
    void defaults() {
        assertFalse(Config.isStaleCheckEnabled());
        assertEquals(30, Config.getStaleAfterDays());
        assertFalse(Config.isStaleCheckUserSet());
    }

    @Test
    @DisplayName("Days outside 1-365 are clamped")
    void clamped() {
        Config.setStaleAfterDays(0);
        assertEquals(1, Config.getStaleAfterDays());
        Config.setStaleAfterDays(1000);
        assertEquals(365, Config.getStaleAfterDays());
        Config.setStaleAfterDays(45);
        assertEquals(45, Config.getStaleAfterDays());
    }

    @Test
    @DisplayName("First import switches the check on with 30 days; a second import changes nothing")
    void firstImport() {
        assertTrue(Config.enableStaleCheckAfterFirstImport());
        assertTrue(Config.isStaleCheckEnabled());
        assertEquals(30, Config.getStaleAfterDays());

        assertFalse(Config.enableStaleCheckAfterFirstImport());
        assertTrue(Config.isStaleCheckEnabled());
    }

    @Test
    @DisplayName("Set by the user: an import leaves the setting as it is")
    void userSet() {
        Config.setStaleCheckUserSet(true);
        Config.setStaleCheckEnabled(false);
        Config.setStaleAfterDays(60);
        assertFalse(Config.enableStaleCheckAfterFirstImport());
        assertFalse(Config.isStaleCheckEnabled());
        assertEquals(60, Config.getStaleAfterDays());
    }
}
