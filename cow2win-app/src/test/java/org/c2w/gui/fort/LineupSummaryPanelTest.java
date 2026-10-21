package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.CowScoreFixture;
import org.c2w.domain.LineupKeyFigures;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The lineup overview's hero/titan summary: power, CowScore (full value) and buff heroes/titans. Never shown. */
class LineupSummaryPanelTest {

    private final CowScoreFixture fixture = new CowScoreFixture();

    @BeforeEach
    @AfterEach
    void defaultBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    @Test
    @DisplayName("heroes: three lines; the CowScore is the sum of the totals, the buff line the matching heroes")
    void heroLines() {
        HeroLineupSummaryPanel panel = new HeroLineupSummaryPanel(fixture.lineup, fixture.guild);

        assertEquals(3, panel.getComponentCount());
        assertEquals(LanguageService.displayName("lineupSummary.heroCowScore") + ": "
                + LineupSummaryPanel.formatCowScore(fixture.expectedHeroCowScore()), panel.cowScoreLabel().getText());
        int buffHeroes = BuffCalculationService.countHeroesIncreasingBuff(fixture.lineup, fixture.guild);
        assertEquals(LanguageService.displayName("lineupSummary.heroBuffCount") + ": "
                        + LanguageService.displayName("lineupSummary.heroBuffCountValue", buffHeroes),
                panel.buffCountLabel().getText());
        assertTrue(panel.buffCountLabel().getText().contains("2"), panel.buffCountLabel().getText());
        assertNotNull(panel.buffCountLabel().getToolTipText());
    }

    @Test
    @DisplayName("titans: the CowScore is the sum of the totals, the buff line the matching titans")
    void titanLines() {
        TitanLineupSummaryPanel panel = new TitanLineupSummaryPanel(fixture.lineup, fixture.guild);

        assertEquals(3, panel.getComponentCount());
        assertEquals(LanguageService.displayName("lineupSummary.titanCowScore") + ": "
                + LineupSummaryPanel.formatCowScore(fixture.expectedTitanCowScore()), panel.cowScoreLabel().getText());
        assertEquals(LanguageService.displayName("lineupSummary.titanBuffCount") + ": "
                        + LanguageService.displayName("lineupSummary.titanBuffCountValue",
                        BuffCalculationService.countTitansIncreasingBuff(fixture.lineup, fixture.guild)),
                panel.buffCountLabel().getText());
    }

    @Test
    @DisplayName("the CowScore format groups thousands and keeps one decimal digit")
    void cowScoreFormat() {
        assertEquals("1.234,5", LineupSummaryPanel.formatCowScore(1234.54));
        assertEquals("616", LineupSummaryPanel.formatCowScore(616.0));
    }

    @Test
    @DisplayName("differences get a sign; power in millions when the value is, in full if that would hide it")
    void diffFormats() {
        assertEquals("+0,3", LineupSummaryPanel.formatPowerDiff(300_000, 56_500_000));
        assertEquals("-1,2", LineupSummaryPanel.formatPowerDiff(-1_200_000, 56_500_000));
        assertEquals("+12.345", LineupSummaryPanel.formatPowerDiff(12_345, 56_500_000));
        assertEquals("-12.345", LineupSummaryPanel.formatPowerDiff(-12_345, 800_000));
        assertEquals("0", LineupSummaryPanel.formatPowerDiff(0, 56_500_000));
        assertEquals("+12,4", LineupSummaryPanel.formatCowScoreDiff(12.4));
        assertEquals("-3", LineupSummaryPanel.formatCowScoreDiff(-3.0));
        assertEquals("0", LineupSummaryPanel.formatCowScoreDiff(-0.01));
    }

    @Test
    @DisplayName("the summary shows the shared key figures")
    void sameAsKeyFigures() {
        HeroLineupSummaryPanel panel = new HeroLineupSummaryPanel(fixture.lineup, fixture.guild);
        LineupKeyFigures figures = LineupKeyFigures.of(fixture.lineup, fixture.guild,
                FortificationType.HERO);
        assertEquals(LanguageService.displayName("lineupSummary.heroPower") + ": "
                + LineupSummaryPanel.formatPower(figures.power()), panel.powerLabel().getText());
        assertEquals(LineupSummaryPanel.cowScoreTooltip(FortificationType.HERO),
                panel.cowScoreLabel().getToolTipText());
    }

    @Test
    @DisplayName("the CowScore tooltips show the current percentages and no unfilled placeholder")
    void tooltipsShowCurrentPercentages() {
        TeamScoreCalculator.setBonuses(new CowScoreBonuses(1.7, 2.3, 1.1, 4.2, 3.9, 2.8, 0.7));

        String hero = new HeroLineupSummaryPanel(fixture.lineup, fixture.guild).cowScoreLabel().getToolTipText();
        String titan = new TitanLineupSummaryPanel(fixture.lineup, fixture.guild).cowScoreLabel().getToolTipText();

        for (String percent : new String[]{"1.7", "1.1", "4.2", "3.9", "2.8", "0.6"}) {
            assertTrue(hero.contains(LineupSummaryPanel.formatPercent(Double.parseDouble(percent))), percent + " in " + hero);
        }
        for (String percent : new String[]{"2.3", "1.1", "0.7", "2.8", "0.6"}) {
            assertTrue(titan.contains(LineupSummaryPanel.formatPercent(Double.parseDouble(percent))), percent + " in " + titan);
        }
        for (String tooltip : new String[]{hero, titan}) {
            assertTrue(tooltip.startsWith("<html>") && tooltip.contains("<br>"), tooltip);
            assertFalse(tooltip.matches("(?s).*\\{\\d}.*"), "unfilled placeholder in " + tooltip);
        }
    }

    @Test
    @DisplayName("refresh updates all three lines")
    void refreshUpdatesAllLines() {
        HeroLineupSummaryPanel panel = new HeroLineupSummaryPanel(fixture.lineup, fixture.guild);
        org.c2w.data.model.Lineup empty = new org.c2w.data.model.Lineup("g1", "Guild", "",
                fixture.lineup.createdAt(), java.util.List.of());

        panel.refresh(empty);

        assertTrue(panel.powerLabel().getText().endsWith(": 0"), panel.powerLabel().getText());
        assertTrue(panel.cowScoreLabel().getText().endsWith(": 0"), panel.cowScoreLabel().getText());
        assertEquals(LanguageService.displayName("lineupSummary.heroBuffCount") + ": "
                + LanguageService.displayName("lineupSummary.heroBuffCountValue", 0), panel.buffCountLabel().getText());
    }
}
