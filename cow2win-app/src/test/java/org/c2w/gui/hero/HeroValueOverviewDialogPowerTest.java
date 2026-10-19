package org.c2w.gui.hero;

import org.c2w.domain.TeamPower;
import org.c2w.gui.common.GuiUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link HeroValueOverviewDialog#powerRejectionMessage}: which "Power" column edits are rejected. */
class HeroValueOverviewDialogPowerTest {

    @Test
    @DisplayName("0 and MAX are accepted, negative power and MAX + 1 are rejected")
    void powerRejection() {
        assertNull(HeroValueOverviewDialog.powerRejectionMessage(0));
        assertNull(HeroValueOverviewDialog.powerRejectionMessage(TeamPower.MAX));
        assertNotNull(HeroValueOverviewDialog.powerRejectionMessage(-1));
        assertEquals(GuiUtils.powerTooHighMessage(), HeroValueOverviewDialog.powerRejectionMessage(2_000_001));
    }
}
