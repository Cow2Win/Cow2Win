package org.c2w.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The summed CowScore of {@link BuffCalculationService}: the teams' full CowScore, power term included. */
class BuffCalculationServiceCowScoreTest {

    private static final double EPS = 1e-9;

    private final CowScoreFixture fixture = new CowScoreFixture();

    @BeforeEach
    @AfterEach
    void defaultBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    @Test
    @DisplayName("hero/titan sum = sum of the placed teams' CowScore totals (power term + bonus)")
    void sumsAreTotals() {
        assertEquals(fixture.expectedHeroCowScore(),
                BuffCalculationService.sumHeroCowScore(fixture.lineup, fixture.guild), EPS);
        assertEquals(fixture.expectedTitanCowScore(),
                BuffCalculationService.sumTitanCowScore(fixture.lineup, fixture.guild), EPS);
        // 1.7 M hero power -> power term 17 plus a few percent, not just the bonus points
        assertTrue(BuffCalculationService.sumHeroCowScore(fixture.lineup, fixture.guild) > 17.0);
    }

    @Test
    @DisplayName("per fortification: sum of the CowScore totals of its teams")
    void sumPerFortification() {
        assertEquals(TeamScoreCalculator.scoreFor(fixture.buffHeroTeam, fixture.roleFort).total(),
                BuffCalculationService.sumCowScoreForFortification(fixture.roleFort.id(), fixture.lineup,
                        fixture.guild, fixture.roleFort), EPS);
        assertEquals(fixture.expectedTitanCowScore(),
                BuffCalculationService.sumCowScoreForFortification(fixture.elementFort.id(), fixture.lineup,
                        fixture.guild, fixture.elementFort), EPS);
    }

    @Test
    @DisplayName("a team without any bonus contributes exactly power / 100 000")
    void teamWithoutBonusIsPowerTerm() {
        assertEquals(CowScoreFixture.PLAIN_HERO_POWER / 100_000.0,
                BuffCalculationService.sumCowScoreForFortification(fixture.plainHeroFort.id(), fixture.lineup,
                        fixture.guild, fixture.plainHeroFort), EPS);
    }

    @Test
    @DisplayName("the buff counts are the matching heroes/titans")
    void buffCounts() {
        assertEquals(2, BuffCalculationService.countHeroesIncreasingBuff(fixture.lineup, fixture.guild));
        assertEquals(1, BuffCalculationService.countTitansIncreasingBuff(fixture.lineup, fixture.guild));
    }
}
