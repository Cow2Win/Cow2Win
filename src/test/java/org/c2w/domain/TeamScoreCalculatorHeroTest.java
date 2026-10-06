package org.c2w.domain;

import org.c2w.data.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hero-team CowScore formula of 2026-09-30:
 * totalPower / 100 000 x (1 + B), B = role buff (1.5 % per matching hero)
 * + relation (+/-1.25 % once) + pet (1.25 % if marked) + war flag
 * (0.6 % if present, 1.25 % if marked).
 */
class TeamScoreCalculatorHeroTest {

    private static final double EPS = 1e-9;

    private static final Fortification FOUNDRY = new Fortification("foundry", FortificationType.HERO, 5, 0, 0, 0,
            new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification BRIDGE = new Fortification("heros-bridge", FortificationType.HERO, 6, 0, 0, 0,
            null, List.of(), 0);

    private static Hero hero(String id, Role role) {
        return new Hero(id, List.of(role));
    }

    private static Hero hero(String id, Role role, String fortificationId, FortMark mark) {
        return new Hero(id, List.of(role), null, new FortMarks(Map.of(fortificationId, mark)));
    }

    private static HeroTeam team(int power, Pet pet, WarFlag warFlag, Hero... heroes) {
        return new HeroTeam("m1", 0, List.of(heroes), pet, warFlag, power, null);
    }

    @Test
    @DisplayName("no bonus at all: score = power / 100 000")
    void powerOnly() {
        HeroTeam team = team(1_000_000, null, null, hero("h1", Role.MAGE));
        assertEquals(10.0, TeamScoreCalculator.scoreFor(team, BRIDGE).total(), EPS);
        assertEquals(10.0, TeamScoreCalculator.scoreFor(team, FOUNDRY).total(), EPS);
    }

    @Test
    @DisplayName("role buff: 1.5 % per hero whose role matches the RoleBuff")
    void roleBuff() {
        HeroTeam team = team(1_000_000, null, null, hero("h1", Role.TANK), hero("h2", Role.TANK), hero("h3", Role.MAGE));
        TeamScoreCalculator.HeroBonus bonus = TeamScoreCalculator.heroBonus(team, FOUNDRY);
        assertEquals(3.0, bonus.rolePercent(), EPS);
        assertEquals(10.0 * 1.03, TeamScoreCalculator.scoreFor(team, FOUNDRY).total(), EPS);
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team, BRIDGE).rolePercent(), EPS, "no buff, no role bonus");
    }

    @Test
    @DisplayName("relation: +1.25 % once for any POSITIVE hero, -1.25 % once for any NEGATIVE hero, both: 0")
    void relation() {
        Hero pos1 = hero("p1", Role.MAGE, "foundry", FortMark.POSITIVE);
        Hero pos2 = hero("p2", Role.MAGE, "foundry", FortMark.POSITIVE);
        Hero neg = hero("n1", Role.MAGE, "foundry", FortMark.NEGATIVE);
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team(1, null, null, pos1, pos2), FOUNDRY).relationPercent(), EPS);
        assertEquals(-1.25, TeamScoreCalculator.heroBonus(team(1, null, null, neg), FOUNDRY).relationPercent(), EPS);
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team(1, null, null, pos1, neg), FOUNDRY).relationPercent(), EPS);
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team(1, null, null, pos1), BRIDGE).relationPercent(), EPS,
                "a mark only counts at its own fortification");
    }

    @Test
    @DisplayName("pet: 1.25 % only if marked for the fortification")
    void pet() {
        Pet marked = new Pet("albus", null, new FortMarks(Map.of("foundry", FortMark.POSITIVE)));
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team(1, marked, null, hero("h", Role.MAGE)), FOUNDRY).petPercent(), EPS);
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team(1, marked, null, hero("h", Role.MAGE)), BRIDGE).petPercent(), EPS);
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team(1, new Pet("albus"), null, hero("h", Role.MAGE)), FOUNDRY).petPercent(), EPS);
    }

    @Test
    @DisplayName("war flag: 0.6 % if merely present, 1.25 % (in total) if marked for the fortification")
    void warFlag() {
        WarFlag marked = new WarFlag("flag-frost", null, new FortMarks(Map.of("foundry", FortMark.POSITIVE)));
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team(1, null, marked, hero("h", Role.MAGE)), FOUNDRY).warFlagPercent(), EPS);
        assertEquals(0.6, TeamScoreCalculator.heroBonus(team(1, null, marked, hero("h", Role.MAGE)), BRIDGE).warFlagPercent(), EPS);
        assertEquals(0.0, TeamScoreCalculator.heroBonus(team(1, null, null, hero("h", Role.MAGE)), BRIDGE).warFlagPercent(), EPS);
    }

    @Test
    @DisplayName("everything combined, and total = powerTerm + sum(memberScores)")
    void combined() {
        Pet pet = new Pet("albus", null, new FortMarks(Map.of("foundry", FortMark.POSITIVE)));
        HeroTeam team = team(450_000, pet, new WarFlag("flag-frost"),
                hero("t1", Role.TANK), hero("t2", Role.TANK), hero("t3", Role.TANK), hero("t4", Role.TANK),
                hero("m1", Role.MAGE));
        // B = 4 x 1.5 + 0.6 + 1.25 = 7.85 %  (example "Team A" of the concept)
        assertEquals(7.85, TeamScoreCalculator.heroBonus(team, FOUNDRY).totalPercent(), EPS);
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team, FOUNDRY);
        assertEquals(4.5 * 1.0785, breakdown.total(), EPS);
        assertEquals(4.5, breakdown.powerTerm(), EPS);
        assertEquals(breakdown.total(),
                breakdown.powerTerm() + breakdown.memberScores().stream().mapToDouble(Double::doubleValue).sum(), EPS);
    }

    @Test
    @DisplayName("sortScore: power / 100 000 x (1 + 0.6 % if a war flag is fielded)")
    void sortScore() {
        assertEquals(1.0, team(100_000, null, null, hero("h", Role.MAGE)).sortScore(), EPS);
        assertEquals(1.006, team(100_000, null, new WarFlag("flag-frost"), hero("h", Role.MAGE)).sortScore(), EPS);
        assertEquals(1.0, team(100_000, new Pet("albus"), null, hero("h", Role.MAGE)).sortScore(), EPS,
                "a pet's strength is already part of the power");
    }

    @Test
    @DisplayName("a mark on a buff-matching hero is ignored - the role buff (1.5 %) still counts")
    void buffMatchingMarkIgnored() {
        for (FortMark mark : FortMark.values()) {
            TeamScoreCalculator.HeroBonus bonus = TeamScoreCalculator.heroBonus(
                    team(1_000_000, null, null, hero("t", Role.TANK, "foundry", mark)), FOUNDRY, TeamCombos.NONE);
            assertEquals(0.0, bonus.relationPercent(), EPS, mark.name());
            assertEquals(TeamScoreCalculator.ROLE_MATCH_PERCENT, bonus.totalPercent(), EPS, mark.name());
        }
    }

    @Test
    @DisplayName("a mark on a hero whose role does not match counts as before (+-1.25 %)")
    void nonMatchingMarkCounts() {
        assertEquals(1.25, TeamScoreCalculator.heroBonus(team(1_000_000, null, null,
                hero("m", Role.MAGE, "foundry", FortMark.POSITIVE)), FOUNDRY, TeamCombos.NONE).relationPercent(), EPS);
        assertEquals(-1.25, TeamScoreCalculator.heroBonus(team(1_000_000, null, null,
                hero("m", Role.MAGE, "foundry", FortMark.NEGATIVE)), FOUNDRY, TeamCombos.NONE).relationPercent(), EPS);
    }

    @Test
    @DisplayName("the role-match count (buffFitScore) ignores war flag and pet")
    void roleMatchCountIgnoresPetWarFlag() {
        assertEquals(1, team(1, new Pet("albus"), new WarFlag("flag-frost"), hero("h", Role.TANK)).buffFitScore(FOUNDRY.buff()));
    }
}
