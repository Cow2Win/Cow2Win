package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.c2w.gui.fort.FortificationPanel.FillLevel.*;
import static org.junit.jupiter.api.Assertions.*;

/** The GUI-free parts of the {@link FortificationPanel} tile. */
class FortificationPanelTest {

    @Test
    @DisplayName("The stripe is red for an empty, orange for a partly and green for a fully filled fortification")
    void fillLevel() {
        assertEquals(EMPTY, FortificationPanel.fillLevel(0, 5));
        assertEquals(PARTIAL, FortificationPanel.fillLevel(1, 5));
        assertEquals(PARTIAL, FortificationPanel.fillLevel(4, 5));
        assertEquals(FULL, FortificationPanel.fillLevel(5, 5));

        assertEquals(IconLoader.RED, FortificationPanel.fillLevelColor(EMPTY));
        assertEquals(FortificationPanel.PARTIAL_COLOR, FortificationPanel.fillLevelColor(PARTIAL));
        assertNotEquals(FortificationTypeStyle.color(FortificationType.TITAN), FortificationPanel.fillLevelColor(PARTIAL));
        assertEquals(IconLoader.GREEN, FortificationPanel.fillLevelColor(FULL));
    }

    @Test
    @DisplayName("The fortification name has the color of its fortification type")
    void nameColorFollowsFortificationType() {
        Fortification foundry = new Fortification("foundry", FortificationType.HERO, 5, 0, 0, 0,
                new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
        Fortification fireFort = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
                new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);

        assertEquals(FortificationTypeStyle.color(FortificationType.HERO), panel(foundry).getNameLabel().getForeground());
        assertEquals(FortificationTypeStyle.color(FortificationType.TITAN), panel(fireFort).getNameLabel().getForeground());
    }

    @Test
    @DisplayName("The power tooltip names its base: live in the live comparison, the last save otherwise")
    void powerTooltip() {
        String gain = "+" + GuiUtils.NUMBER_FORMAT.format(12_345);
        String total = GuiUtils.NUMBER_FORMAT.format(1_000_000);

        assertEquals(LanguageService.displayName("fortification.liveDiffTooltip", gain, "+1"),
                FortificationPanel.powerTooltip(FortificationValueMode.LIVE_COMPARISON, 1_000_000, 12_345, true, 1));
        assertEquals(LanguageService.displayName("fortification.liveDiffTooltipNoBuff", gain),
                FortificationPanel.powerTooltip(FortificationValueMode.LIVE_COMPARISON, 1_000_000, 12_345, false, 0));
        assertEquals(LanguageService.displayName("fortification.liveUnchangedTooltip"),
                FortificationPanel.powerTooltip(FortificationValueMode.LIVE_COMPARISON, 1_000_000, 0, true, 0));

        assertEquals(LanguageService.displayName("fortification.totalPowerTooltip", total),
                FortificationPanel.powerTooltip(FortificationValueMode.CHANGES, 1_000_000, 12_345, true, 1));

        assertEquals(LanguageService.displayName("fortification.diffTooltip", gain, "+1"),
                FortificationPanel.powerTooltip(FortificationValueMode.POWER, 1_000_000, 12_345, true, 1));
        assertEquals(LanguageService.displayName("fortification.diffTooltipNoBuff", gain),
                FortificationPanel.powerTooltip(FortificationValueMode.POWER, 1_000_000, 12_345, false, 0));
        assertEquals(LanguageService.displayName("fortification.unchangedTooltip"),
                FortificationPanel.powerTooltip(FortificationValueMode.POWER, 1_000_000, 0, true, 0));
    }

    private static FortificationPanel panel(Fortification fortification) {
        return new FortificationPanel(fortification, 2, 1_000_000, 0, FortificationValueMode.POWER, 10, 0, 0, false, f -> { });
    }

    @Test
    @DisplayName("Power from one million on is shown in millions with two decimals, below that in full")
    void compactPower() {
        String german = LanguageService.textIn("deutsch", "fortification.powerMillions");
        assertEquals("1,05 Mio", FortificationPanel.compactPower(1_050_000, Locale.GERMANY, german));
        assertEquals("812.345", FortificationPanel.compactPower(812_345, Locale.GERMANY, german));
        assertEquals("0", FortificationPanel.compactPower(0, Locale.GERMANY, german));

        String english = LanguageService.textIn("english", "fortification.powerMillions");
        assertEquals("1.05 M", FortificationPanel.compactPower(1_050_000, Locale.UK, english));
    }

    @Test
    @DisplayName("The buff percentage gets a sign when positive")
    void formatPercent() {
        assertEquals("+8 %", FortificationPanel.formatPercent(8));
        assertEquals("0 %", FortificationPanel.formatPercent(0));
        assertEquals("-5 %", FortificationPanel.formatPercent(-5));
    }
}
