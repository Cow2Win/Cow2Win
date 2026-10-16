package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.FortificationTypeStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ChangePlanPanel#itemColor}: titan entries of the change plan in titan gold, heroes unchanged. */
class ChangePlanPanelColorTest {

    @Test
    @DisplayName("Titan open: gold; titan checked: gold with the alpha of TEXT_MUTED")
    void titanColors() {
        Color gold = FortificationTypeStyle.color(FortificationType.TITAN);

        assertEquals(gold, ChangePlanPanel.itemColor(FortificationType.TITAN, false));
        Color checked = ChangePlanPanel.itemColor(FortificationType.TITAN, true);
        assertEquals(gold.getRGB() & 0xFFFFFF, checked.getRGB() & 0xFFFFFF, "same RGB value as the gold");
        assertEquals(140, checked.getAlpha());
        assertEquals(AppColors.TEXT_MUTED.getAlpha(), checked.getAlpha());
    }

    @Test
    @DisplayName("Hero open: Label.foreground; hero checked: TEXT_MUTED")
    void heroColors() {
        assertEquals(UIManager.getColor("Label.foreground"), ChangePlanPanel.itemColor(FortificationType.HERO, false));
        assertEquals(AppColors.TEXT_MUTED, ChangePlanPanel.itemColor(FortificationType.HERO, true));
    }
}
