package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.domain.FortificationHeatmap;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.c2w.gui.fort.FortificationPanel.FillLevel.*;
import static org.junit.jupiter.api.Assertions.*;

/** The GUI-free parts of the {@link FortificationPanel} tile. */
class FortificationPanelTest {

    @Test
    @DisplayName("The stripe is ERROR for an empty, WARNING for a partly and SUCCESS for a fully filled fortification")
    void fillLevel() {
        assertEquals(EMPTY, FortificationPanel.fillLevel(0, 5));
        assertEquals(PARTIAL, FortificationPanel.fillLevel(1, 5));
        assertEquals(PARTIAL, FortificationPanel.fillLevel(4, 5));
        assertEquals(FULL, FortificationPanel.fillLevel(5, 5));

        assertEquals(AppColors.ERROR, FortificationPanel.fillLevelColor(EMPTY));
        assertEquals(AppColors.WARNING, FortificationPanel.fillLevelColor(PARTIAL));
        assertNotEquals(FortificationTypeStyle.color(FortificationType.TITAN), FortificationPanel.fillLevelColor(PARTIAL));
        assertEquals(AppColors.SUCCESS, FortificationPanel.fillLevelColor(FULL));
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
                FortificationPanel.powerTooltip(FortificationValueMode.LIVE_COMPARISON, 1_000_000, 200_000, 5, 12_345, true, 1));
        assertEquals(LanguageService.displayName("fortification.liveDiffTooltipNoBuff", gain),
                FortificationPanel.powerTooltip(FortificationValueMode.LIVE_COMPARISON, 1_000_000, 200_000, 5, 12_345, false, 0));
        assertEquals(LanguageService.displayName("fortification.liveUnchangedTooltip"),
                FortificationPanel.powerTooltip(FortificationValueMode.LIVE_COMPARISON, 1_000_000, 200_000, 5, 0, true, 0));

        assertEquals(LanguageService.displayName("fortification.totalPowerTooltip", total),
                FortificationPanel.powerTooltip(FortificationValueMode.CHANGES, 1_000_000, 200_000, 5, 12_345, true, 1));

        assertEquals(LanguageService.displayName("fortification.averagePowerTooltip",
                        GuiUtils.NUMBER_FORMAT.format(200_000), total, "5"),
                FortificationPanel.powerTooltip(FortificationValueMode.AVERAGE_POWER, 1_000_000, 200_000, 5, 12_345, true, 1));

        assertEquals(LanguageService.displayName("fortification.diffTooltip", gain, "+1"),
                FortificationPanel.powerTooltip(FortificationValueMode.POWER, 1_000_000, 200_000, 5, 12_345, true, 1));
        assertEquals(LanguageService.displayName("fortification.diffTooltipNoBuff", gain),
                FortificationPanel.powerTooltip(FortificationValueMode.POWER, 1_000_000, 200_000, 5, 12_345, false, 0));
        assertEquals(LanguageService.displayName("fortification.unchangedTooltip"),
                FortificationPanel.powerTooltip(FortificationValueMode.POWER, 1_000_000, 200_000, 5, 0, true, 0));
    }

    private static FortificationPanel panel(Fortification fortification) {
        return new FortificationPanel(fortification, 2, 1_000_000, 500_000, 0, FortificationValueMode.POWER, 10, 0, 0, false, f -> { }, f -> { });
    }

    @Test
    @DisplayName("A click reports to onClick, a double-click to onDoubleClick - on the tile and on its labels")
    void clickAndDoubleClick() {
        Fortification foundry = new Fortification("foundry", FortificationType.HERO, 5, 0, 0, 0,
                new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
        List<String> events = new ArrayList<>();
        FortificationPanel panel = new FortificationPanel(foundry, 2, 1_000_000, 200_000, 0, FortificationValueMode.POWER,
                10, 0, 0, false, f -> events.add("click " + f.id()), f -> events.add("double " + f.id()));

        click(panel, 1);
        click(panel, 2);
        click(panel.getNameLabel(), 1);
        click(panel.getPowerLabel(), 2);

        assertEquals(List.of("click foundry", "double foundry", "click foundry", "double foundry"), events);
    }

    /** Dispatches a left click with {@code clickCount} to {@code component}. */
    static void click(Component component, int clickCount) {
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, 5, 5, clickCount, false, MouseEvent.BUTTON1));
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
    @DisplayName("Value modes: AVERAGE_POWER right after POWER, and like POWER it shows no change")
    void valueModes() {
        assertArrayEquals(new FortificationValueMode[]{FortificationValueMode.POWER, FortificationValueMode.AVERAGE_POWER,
                FortificationValueMode.CHANGES, FortificationValueMode.LIVE_COMPARISON}, FortificationValueMode.values());
        assertFalse(FortificationValueMode.POWER.showsChange());
        assertFalse(FortificationValueMode.AVERAGE_POWER.showsChange());
        assertTrue(FortificationValueMode.CHANGES.showsChange());
        assertTrue(FortificationValueMode.LIVE_COMPARISON.showsChange());
    }

    @Test
    @DisplayName("Average power per slot: whole thousands with 'k' below one million, millions from 999 500 on")
    void compactAveragePower() {
        String millions = LanguageService.textIn("deutsch", "fortification.powerMillions");
        String thousands = LanguageService.textIn("deutsch", "fortification.powerThousands");
        assertEquals("868 k", FortificationPanel.compactAveragePower(867_914, Locale.GERMANY, millions, thousands));
        assertEquals("986 k", FortificationPanel.compactAveragePower(985_853, Locale.GERMANY, millions, thousands));
        assertEquals("1 k", FortificationPanel.compactAveragePower(500, Locale.GERMANY, millions, thousands), "half up");
        assertEquals("999 k", FortificationPanel.compactAveragePower(999_499, Locale.GERMANY, millions, thousands));
        assertEquals("1,00 Mio", FortificationPanel.compactAveragePower(999_500, Locale.GERMANY, millions, thousands));
        assertEquals("1,16 Mio", FortificationPanel.compactAveragePower(1_155_466, Locale.GERMANY, millions, thousands));
        assertEquals("0 k", FortificationPanel.compactAveragePower(0, Locale.GERMANY, millions, thousands), "empty fortification");

        String englishMillions = LanguageService.textIn("english", "fortification.powerMillions");
        String englishThousands = LanguageService.textIn("english", "fortification.powerThousands");
        assertEquals("868 k", FortificationPanel.compactAveragePower(867_914, Locale.UK, englishMillions, englishThousands));
        assertEquals("1.16 M", FortificationPanel.compactAveragePower(1_155_466, Locale.UK, englishMillions, englishThousands));
    }

    @Test
    @DisplayName("In AVERAGE_POWER the tile shows the heatmap's value per slot, the tooltip its calculation")
    void averagePowerFromHeatmap() {
        Fortification fortress = new Fortification("fortress", FortificationType.HERO, 8, 0, 0, 0, null, List.of(), 0);
        Fortification other = new Fortification("other", FortificationType.HERO, 8, 0, 1, 0, null, List.of(), 0);
        List<GuildMember> members = new ArrayList<>();
        List<Lineup.Entry> entries = new ArrayList<>();
        int[] powers = {900_000, 900_000, 900_000, 900_000, 900_000, 900_000, 900_000, 643_309};
        for (int i = 0; i < powers.length; i++) {
            String memberId = "m" + i;
            members.add(new GuildMember(memberId, memberId, List.of(new HeroTeam(memberId, 0,
                    List.of(new Hero("h-" + memberId, List.of(Role.MAGE))), null, null, powers[i], null)), List.of()));
            entries.add(new Lineup.Entry(fortress.id(), memberId, Lineup.TeamType.HERO, 0));
        }
        Guild guild = new Guild("g1", "Guild", members);
        Lineup lineup = new Lineup("g1", "Guild", "", LocalDateTime.of(2026, 1, 1, 0, 0), entries);
        FortificationHeatmap heatmap = FortificationHeatmap.of(FortificationType.HERO, List.of(fortress, other), lineup, guild);

        int average = FortificationMapPanel.averagePower(heatmap, fortress);
        assertEquals(867_914, average, "6 943 309 / 8, rounded");
        assertEquals(Math.round(heatmap.rating("fortress").value()), average, "same source as the heatmap");
        assertEquals(0, FortificationMapPanel.averagePower(heatmap, other), "empty fortification");

        FortificationPanel panel = new FortificationPanel(fortress, 8, 6_943_309, average, 0,
                FortificationValueMode.AVERAGE_POWER, 0, 0, 0, false, f -> { }, f -> { });
        assertEquals("8/8 · " + FortificationPanel.compactAveragePower(867_914), panel.getPowerLabel().getText());
        assertEquals(LanguageService.displayName("fortification.averagePowerTooltip",
                        GuiUtils.NUMBER_FORMAT.format(867_914), GuiUtils.NUMBER_FORMAT.format(6_943_309), "8"),
                panel.getPowerLabel().getToolTipText());
        assertEquals("Ø Stärke je Platz: 867.914 (Summe 6.943.309 ÷ 8 Plätze)",
                LanguageService.textIn("deutsch", "fortification.averagePowerTooltip")
                        .replace("{0}", "867.914").replace("{1}", "6.943.309").replace("{2}", "8"));
    }

    @Test
    @DisplayName("The buff percentage gets a sign when positive")
    void formatPercent() {
        assertEquals("+8 %", FortificationPanel.formatPercent(8));
        assertEquals("0 %", FortificationPanel.formatPercent(0));
        assertEquals("-5 %", FortificationPanel.formatPercent(-5));
    }
}
