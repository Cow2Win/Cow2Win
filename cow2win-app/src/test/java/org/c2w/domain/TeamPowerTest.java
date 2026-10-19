package org.c2w.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamPowerTest {

    @Test
    @DisplayName("MAX itself and 0 are allowed, one above MAX is too high")
    void isTooHigh() {
        assertFalse(TeamPower.isTooHigh(0));
        assertFalse(TeamPower.isTooHigh(2_000_000));
        assertTrue(TeamPower.isTooHigh(2_000_001));
    }
}
