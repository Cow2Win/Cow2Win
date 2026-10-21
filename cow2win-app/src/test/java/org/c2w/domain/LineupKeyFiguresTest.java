package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.domain.LineupKeyFigures.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LineupKeyFigures}: the key figures of one lineup per fortification type. */
class LineupKeyFiguresTest {

    private final CowScoreFixture fixture = new CowScoreFixture();

    @BeforeEach
    @AfterEach
    void defaultBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    /** The fixture's guild plus a member with one hero and one titan team that are not placed. */
    private Guild guildWithBench() {
        List<GuildMember> members = new ArrayList<>(fixture.guild.members());
        members.add(new GuildMember("m3", "Bench", List.of(new HeroTeam("m3", 0,
                List.of(new Hero("h9", List.of(Role.MAGE))), null, null, 300_000, null)),
                List.of(new TitanTeam("m3", 0, List.of(new Titan("t9", TitanElement.FIRE)), 400_000))));
        return new Guild("g1", "Guild", members);
    }

    private List<Fortification> catalog() {
        return List.of(fixture.roleFort, fixture.plainHeroFort, fixture.elementFort);
    }

    @Test
    @DisplayName("Heroes: power, CowScore, buff matches, occupied slots and teams left out")
    void heroes() {
        Guild guild = guildWithBench();
        LineupKeyFigures figures = LineupKeyFigures.of(fixture.lineup, guild, FortificationType.HERO, catalog());

        assertEquals(1_000_000 + CowScoreFixture.PLAIN_HERO_POWER, figures.power());
        assertEquals(fixture.expectedHeroCowScore(), figures.cowScore(), 0.0001);
        assertEquals(BuffCalculationService.countHeroesIncreasingBuff(fixture.lineup, guild), figures.buffHits());
        assertEquals(2, figures.buffHits(), "two heroes match the role buff");
        assertEquals(2, figures.occupiedSlots());
        assertEquals(fixture.roleFort.capacity() + fixture.plainHeroFort.capacity(), figures.capacity());
        assertEquals(1, figures.teamsOutside(), "3 hero teams, 2 placed");
    }

    @Test
    @DisplayName("Titans: the same figures for the titan teams only")
    void titans() {
        Guild guild = guildWithBench();
        LineupKeyFigures figures = LineupKeyFigures.of(fixture.lineup, guild, FortificationType.TITAN, catalog());

        assertEquals(2_500_000, figures.power());
        assertEquals(fixture.expectedTitanCowScore(), figures.cowScore(), 0.0001);
        assertEquals(BuffCalculationService.countTitansIncreasingBuff(fixture.lineup, guild), figures.buffHits());
        assertEquals(2, figures.occupiedSlots());
        assertEquals(fixture.elementFort.capacity(), figures.capacity());
        assertEquals(1, figures.teamsOutside(), "3 titan teams, 2 placed");
    }

    @Test
    @DisplayName("An empty lineup: nothing placed, every team left out")
    void emptyLineup() {
        Lineup empty = new Lineup("g1", "Guild", "", null, List.of());
        LineupKeyFigures figures = LineupKeyFigures.of(empty, guildWithBench(), FortificationType.HERO, catalog());

        assertEquals(new LineupKeyFigures(0, 0, 0, 0,
                fixture.roleFort.capacity() + fixture.plainHeroFort.capacity(), 3), figures);
    }

    @Test
    @DisplayName("More is better, except where less is better (teams left out)")
    void direction() {
        assertEquals(Direction.BETTER, LineupKeyFigures.direction(10, 12, false));
        assertEquals(Direction.WORSE, LineupKeyFigures.direction(10, 8, false));
        assertEquals(Direction.EQUAL, LineupKeyFigures.direction(10, 10, false));
        assertEquals(Direction.BETTER, LineupKeyFigures.direction(5, 3, true));
        assertEquals(Direction.WORSE, LineupKeyFigures.direction(5, 7, true));
        assertEquals(Direction.EQUAL, LineupKeyFigures.direction(5, 5, true));
    }
}
