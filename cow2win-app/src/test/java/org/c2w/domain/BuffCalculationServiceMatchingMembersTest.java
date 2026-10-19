package org.c2w.domain;

import org.c2w.data.model.Lineup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link BuffCalculationService#countMatchingMembers}: the buff-matching heroes/titans of one team. */
class BuffCalculationServiceMatchingMembersTest {

    private final CowScoreFixture fixture = new CowScoreFixture();

    private static Lineup.Entry hero(int teamIndex) {
        return new Lineup.Entry("any", "m1", Lineup.TeamType.HERO, teamIndex);
    }

    private static Lineup.Entry titan(int teamIndex) {
        return new Lineup.Entry("any", "m2", Lineup.TeamType.TITAN, teamIndex);
    }

    @Test
    @DisplayName("hero team: counts the heroes with the buff's role")
    void heroTeam() {
        assertEquals(2, BuffCalculationService.countMatchingMembers(hero(0), fixture.guild, fixture.roleFort.buff()));
        assertEquals(0, BuffCalculationService.countMatchingMembers(hero(1), fixture.guild, fixture.roleFort.buff()));
    }

    @Test
    @DisplayName("titan team: counts the titans with the buff's element")
    void titanTeam() {
        assertEquals(1, BuffCalculationService.countMatchingMembers(titan(0), fixture.guild, fixture.elementFort.buff()));
        assertEquals(0, BuffCalculationService.countMatchingMembers(titan(1), fixture.guild, fixture.elementFort.buff()));
    }

    @Test
    @DisplayName("a buff of the other kind never matches")
    void otherKindOfBuff() {
        assertEquals(0, BuffCalculationService.countMatchingMembers(hero(0), fixture.guild, fixture.elementFort.buff()));
        assertEquals(0, BuffCalculationService.countMatchingMembers(titan(0), fixture.guild, fixture.roleFort.buff()));
    }

    @Test
    @DisplayName("no buff or a team that no longer exists: 0")
    void noBuffOrUnknownTeam() {
        assertEquals(0, BuffCalculationService.countMatchingMembers(hero(0), fixture.guild, null));
        assertEquals(0, BuffCalculationService.countMatchingMembers(hero(5), fixture.guild, fixture.roleFort.buff()));
        assertEquals(0, BuffCalculationService.countMatchingMembers(
                new Lineup.Entry("any", "unknown", Lineup.TeamType.HERO, 0), fixture.guild, fixture.roleFort.buff()));
    }

    @Test
    @DisplayName("per fortification: the sum over its teams, unchanged")
    void perFortification() {
        assertEquals(2, BuffCalculationService.countMatchingMembersForFortification(fixture.roleFort.id(),
                fixture.lineup, fixture.guild, fixture.roleFort.buff()));
        assertEquals(1, BuffCalculationService.countMatchingMembersForFortification(fixture.elementFort.id(),
                fixture.lineup, fixture.guild, fixture.elementFort.buff()));
    }
}
