package org.c2w.data.model;

import org.c2w.util.TeamScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a hero team's optional war flag and pet feed into its score (user's
 * decisions, 2026-09-29): same tiers as heroes but worth less
 * ({@link CowScoreTier#petWarFlagValue()}, GOOD = 0.3), generalScore as the
 * default at a buffed fortification too, 0 when absent, never part of the
 * role-match count.
 */
class HeroTeamPetWarFlagScoreTest {

    private static final double EPS = 1e-9;

    private static final Fortification BUFFED = new Fortification("f1", FortificationType.HERO, 3, 0, 0, 0,
            new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10), List.of(), 0);
    private static final Fortification UNBUFFED = new Fortification("f2", FortificationType.HERO, 3, 0, 0, 0,
            null, List.of(), 0);

    private static Pet pet(CowScoreTier general, Map<String, CowScoreTier> overrides) {
        return new Pet("albus", null, new CowScore(general, overrides));
    }

    private static WarFlag warFlag(CowScoreTier general, Map<String, CowScoreTier> overrides) {
        return new WarFlag("flag-frost", null, new CowScore(general, overrides));
    }

    private static HeroTeam team(Pet pet, WarFlag warFlag) {
        return new HeroTeam("m1", 0, List.of(new Hero("h1", List.of(Role.TANK))), pet, warFlag, 100_000, null);
    }

    @Test
    @DisplayName("GOOD is worth 0.3 for a pet/war flag, every tier is worth less than for a hero")
    void petWarFlagValues() {
        assertEquals(0.3, CowScoreTier.GOOD.petWarFlagValue(), EPS);
        for (CowScoreTier tier : CowScoreTier.values()) {
            assertTrue(tier.petWarFlagValue() < tier.value(), tier.name());
        }
    }

    @Test
    @DisplayName("sortScore adds the war flag's and the pet's generalScore at their lower value; absent counts 0")
    void sortScore() {
        double heroOnly = 0.8 + 1.0; // one GOOD hero + 100 000 / 100 000
        assertEquals(heroOnly, team(null, null).sortScore(), EPS);
        assertEquals(heroOnly + 0.3, team(pet(CowScoreTier.GOOD, null), null).sortScore(), EPS);
        assertEquals(heroOnly + 0.3 + 0.35,
                team(pet(CowScoreTier.GOOD, null), warFlag(CowScoreTier.GREAT, null)).sortScore(), EPS);
    }

    @Test
    @DisplayName("at a buffed fortification: explicit override if present, otherwise generalScore - never the hero AVERAGE default")
    void buffedFortification() {
        HeroTeam team = team(pet(CowScoreTier.AVERAGE, Map.of("f1", CowScoreTier.GREAT)),
                warFlag(CowScoreTier.MODERATE, Map.of("other-fortification", CowScoreTier.NEGATIVE)));
        assertEquals(List.of(0.25, 0.35), team.petWarFlagScores(BUFFED), "war flag first, then pet");

        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team, BUFFED);
        assertEquals(3, breakdown.memberScores().size());
        assertEquals(0.25, breakdown.memberScores().get(0), EPS);
        assertEquals(0.35, breakdown.memberScores().get(1), EPS);
        assertEquals(0.8, breakdown.memberScores().get(2), EPS); // TANK hero, role matches -> GOOD
        assertEquals(1.0 + 0.25 + 0.35 + 0.8, breakdown.total(), EPS);
    }

    @Test
    @DisplayName("at an unbuffed fortification the generalScore counts, overrides are ignored")
    void unbuffedFortification() {
        HeroTeam team = team(pet(CowScoreTier.AVERAGE, Map.of("f2", CowScoreTier.GREAT)), null);
        TeamScoreCalculator.Breakdown breakdown = TeamScoreCalculator.scoreFor(team, UNBUFFED);
        assertEquals(1.0 + 0.2 + 0.8, breakdown.total(), EPS);
        assertEquals(team.sortScore(), breakdown.total(), EPS);
    }

    @Test
    @DisplayName("the role-match count (buffFitScore) ignores war flag and pet")
    void roleMatchCountIgnoresPetWarFlag() {
        assertEquals(1, team(pet(CowScoreTier.GREAT, null), warFlag(CowScoreTier.GREAT, null)).buffFitScore(BUFFED.buff()));
    }
}
