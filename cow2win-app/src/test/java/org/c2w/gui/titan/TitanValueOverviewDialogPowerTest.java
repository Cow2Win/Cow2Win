package org.c2w.gui.titan;

import org.c2w.domain.TeamPower;
import org.c2w.gui.common.GuiUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link TitanValueOverviewDialog#powerRejectionMessage}: which "Power" column edits are rejected. */
class TitanValueOverviewDialogPowerTest {

    @Test
    @DisplayName("0 and MAX are accepted, negative power and MAX + 1 are rejected")
    void powerRejection() {
        assertNull(TitanValueOverviewDialog.powerRejectionMessage(0));
        assertNull(TitanValueOverviewDialog.powerRejectionMessage(TeamPower.MAX));
        assertNotNull(TitanValueOverviewDialog.powerRejectionMessage(-1));
        assertEquals(GuiUtils.powerTooHighMessage(), TitanValueOverviewDialog.powerRejectionMessage(2_000_001));
    }
}
