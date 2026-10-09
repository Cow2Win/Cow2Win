package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link CowScoreBonuses} and the calculation with changed percentages. */
class CowScoreBonusesTest {

    private static final double EPS = 1e-9;

    @AfterEach
    void resetBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    @Test
    @DisplayName("Defaults: buffs 1.5 %, every small bonus 1.25 %")
    void defaults() {
        assertEquals(new CowScoreBonuses(1.5, 1.5, 1.25, 1.25, 1.25, 1.25, 1.25), CowScoreBonuses.DEFAULTS);
        assertEquals(CowScoreBonuses.DEFAULTS, CowScoreBonuses.DEFAULTS.normalized());
        assertEquals(CowScoreBonuses.DEFAULTS, TeamScoreCalculator.bonuses());
    }

    @Test
    @DisplayName("Every value is limited to its range: buffs 1-3 %, small bonuses 1-2 %")
    void clampsToRange() {
        CowScoreBonuses tooLow = new CowScoreBonuses(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5).normalized();
        assertEquals(new CowScoreBonuses(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0), tooLow);
        CowScoreBonuses tooHigh = new CowScoreBonuses(5, 5, 5, 5, 5, 5, 5).normalized();
        assertEquals(new CowScoreBonuses(3.0, 3.0, 2.0, 2.0, 2.0, 2.0, 2.0), tooHigh);
        assertEquals(1.0, new CowScoreBonuses(Double.NaN, 1.5, 1.25, 1.25, 1.25, 1.25, 1.25).clamped().rolePercent());
    }

    @Test
    @DisplayName("A buff smaller than a small bonus of its side is raised - each side on its own")
    void raisesBuffPerSide() {
        CowScoreBonuses combo = new CowScoreBonuses(1.2, 1.5, 1.25, 1.25, 1.25, 1.8, 1.25).normalized();
        assertEquals(1.8, combo.rolePercent(), EPS);
        assertEquals(1.5, combo.elementPercent(), EPS, "the titan side is not affected by the combo");

        CowScoreBonuses totem = new CowScoreBonuses(1.5, 1.5, 1.25, 1.25, 1.25, 1.25, 1.9).normalized();
        assertEquals(1.9, totem.elementPercent(), EPS);
        assertEquals(1.5, totem.rolePercent(), EPS);

        CowScoreBonuses relation = new CowScoreBonuses(1.5, 1.5, 2.0, 1.25, 1.25, 1.25, 1.25).normalized();
        assertEquals(2.0, relation.rolePercent(), EPS, "the relation counts for both sides");
        assertEquals(2.0, relation.elementPercent(), EPS);
    }

    @Test
    @DisplayName("Changed percentages: relation 2 %, role buff 2.5 % - heroBonus, titanBonus and scoreFor follow")
    void calculationUsesRegisteredBonuses() {
        TeamScoreCalculator.setBonuses(new CowScoreBonuses(2.5, 3.0, 2.0, 1.5, 1.75, 1.25, 1.5));
        Fortification foundry = new Fortification("foundry", FortificationType.HERO, 5, 0, 0, 0,
                new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
        HeroTeam heroes = new HeroTeam("m1", 0, List.of(new Hero("t1", List.of(Role.TANK)), new Hero("t2", List.of(Role.TANK)),
                new Hero("m", List.of(Role.MAGE), null, new FortMarks(Map.of("foundry", FortMark.POSITIVE)))),
                null, null, 1_000_000, null);

        TeamScoreCalculator.HeroBonus heroBonus = TeamScoreCalculator.heroBonus(heroes, foundry, TeamCombos.NONE);
        assertEquals(5.0, heroBonus.rolePercent(), EPS);
        assertEquals(2.0, heroBonus.relationPercent(), EPS);
        assertEquals(10.0 * 1.07, TeamScoreCalculator.scoreFor(heroes, foundry, TeamCombos.NONE).total(), EPS);

        Fortification fireFort = new Fortification("fire-fort", FortificationType.TITAN, 5, 0, 0, 0,
                new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
        TitanTeam titans = new TitanTeam("m1", 0, List.of(new Titan("f1", TitanElement.FIRE), new Titan("f2", TitanElement.FIRE),
                new Titan("w1", TitanElement.WATER).withFortMarks(new FortMarks(Map.of("fire-fort", FortMark.NEGATIVE)))),
                1_000_000, null, Set.of(TitanElement.FIRE));
        TeamScoreCalculator.TitanBonus titanBonus = TeamScoreCalculator.titanBonus(titans, fireFort);
        assertEquals(6.0, titanBonus.elementPercent(), EPS);
        assertEquals(-2.0, titanBonus.relationPercent(), EPS);
        assertEquals(1.5, titanBonus.totemPercent(), EPS);
        assertEquals(10.0 * 1.055, TeamScoreCalculator.scoreFor(titans, fireFort).total(), EPS);
    }
}
