package org.c2w.eval;

import org.c2w.data.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the HERO-only and the TITAN-only variant of every lineup
 * strategy: each algorithm only ever adds entries for
 * its own side, the two sides can be combined freely via {@link
 * LineupAlgorithms#runBoth} (including {@link ManualLineupAlgorithm} for a
 * side filled by hand), and {@link Lineup#algorithmName()} records both
 * sides' algorithms.
 */
class SideSpecificLineupAlgorithmsTest {

    // --- fixtures ----------------------------------------------------------

    private static Hero hero(String id) {
        return new Hero(id, List.of(Role.MAGE));
    }

    private static Titan titan(String id) {
        return new Titan(id, TitanElement.WATER);
    }

    /** A member with one hero team and one titan team, both of the given power. */
    private static GuildMember mixedMember(String id, int power) {
        HeroTeam heroTeam = new HeroTeam(id, 0, List.of(hero(id + "-hero")), power);
        TitanTeam titanTeam = new TitanTeam(id, 0, List.of(titan(id + "-titan")), power);
        return new GuildMember(id, id, List.of(heroTeam), List.of(titanTeam));
    }

    private static Guild mixedGuild() {
        return new Guild("g", "Guild", List.of(
                mixedMember("m1", 900), mixedMember("m2", 800), mixedMember("m3", 700), mixedMember("m4", 600)));
    }

    private static Lineup emptyLineup(Guild guild) {
        return new Lineup(guild.id(), guild.name(), "", null, List.of());
    }

    private static long countOfType(Lineup lineup, Lineup.TeamType teamType) {
        return lineup.entries().stream().filter(entry -> entry.teamType() == teamType).count();
    }

    // --- tests -------------------------------------------------------------

    @Nested
    @DisplayName("Each algorithm only fills its own side")
    class SideIsolationTests {

        @Test
        @DisplayName("Every HERO algorithm adds only HERO entries, every TITAN algorithm only TITAN entries")
        void everyAlgorithmStaysOnItsSide() {
            Guild guild = mixedGuild();
            for (LineupAlgorithm algorithm : LineupAlgorithms.HERO) {
                assertEquals(Lineup.TeamType.HERO, algorithm.teamType(), algorithm.displayName());
                Lineup result = algorithm.run(emptyLineup(guild), guild);
                assertEquals(0, countOfType(result, Lineup.TeamType.TITAN),
                        algorithm.displayName() + " (hero) must not touch titan teams");
            }
            for (LineupAlgorithm algorithm : LineupAlgorithms.TITAN) {
                assertEquals(Lineup.TeamType.TITAN, algorithm.teamType(), algorithm.displayName());
                Lineup result = algorithm.run(emptyLineup(guild), guild);
                assertEquals(0, countOfType(result, Lineup.TeamType.HERO),
                        algorithm.displayName() + " (titan) must not touch hero teams");
            }
        }

        @Test
        @DisplayName("The automatic HERO strategies place every hero team (well below total capacity)")
        void heroStrategiesPlaceAllHeroTeams() {
            Guild guild = mixedGuild();
            for (LineupAlgorithm algorithm : List.of(
                    new HodorAlgorithm<>(TeamSide.HERO, HodorAlgorithm.Variant.POWER),
                    new HodorAlgorithm<>(TeamSide.HERO, HodorAlgorithm.Variant.COW_SCORE))) {
                Lineup result = algorithm.run(emptyLineup(guild), guild);
                assertEquals(4, countOfType(result, Lineup.TeamType.HERO), algorithm.displayName());
            }
        }

        @Test
        @DisplayName("Entries of the other side are passed through unchanged")
        void otherSideEntriesArePreserved() {
            Guild guild = mixedGuild();
            Lineup.Entry manualTitan = new Lineup.Entry("bridge", "m1", Lineup.TeamType.TITAN, 0);
            Lineup start = new Lineup(guild.id(), guild.name(), "", null, List.of(manualTitan));

            Lineup result = new HodorAlgorithm<>(TeamSide.HERO, HodorAlgorithm.Variant.POWER).run(start, guild);

            assertTrue(result.entries().contains(manualTitan));
            assertEquals(1, countOfType(result, Lineup.TeamType.TITAN), "no titan entry may be added or removed");
        }
    }

    @Nested
    @DisplayName("Combining hero and titan algorithms")
    class CombinationTests {

        @Test
        @DisplayName("Manual for titans + automatic for heroes: only hero teams get placed")
        void manualTitansAutomaticHeroes() {
            Guild guild = mixedGuild();
            Lineup result = LineupAlgorithms.runBoth(
                    LineupAlgorithms.findOrDefault(Lineup.TeamType.HERO, "HODOR"),
                    LineupAlgorithms.findOrDefault(Lineup.TeamType.TITAN, ManualLineupAlgorithm.DISPLAY_NAME),
                    emptyLineup(guild), guild);

            assertEquals(4, countOfType(result, Lineup.TeamType.HERO));
            assertEquals(0, countOfType(result, Lineup.TeamType.TITAN));
            assertEquals("Heroes: HODOR", result.algorithmName(),
                    "the manual side leaves no trace in algorithmName");
        }

        @Test
        @DisplayName("Different strategies per side are recorded in algorithmName")
        void differentStrategiesPerSide() {
            Guild guild = mixedGuild();
            Lineup result = LineupAlgorithms.runBoth(
                    new HodorAlgorithm<>(TeamSide.HERO, HodorAlgorithm.Variant.COW_SCORE),
                    new HodorAlgorithm<>(TeamSide.TITAN, HodorAlgorithm.Variant.POWER),
                    emptyLineup(guild), guild);

            assertEquals(4, countOfType(result, Lineup.TeamType.HERO));
            assertEquals(4, countOfType(result, Lineup.TeamType.TITAN));
            assertEquals("Heroes: HODOR (CowScore); Titans: HODOR", result.algorithmName());
        }

        @Test
        @DisplayName("runBoth rejects an algorithm passed for the wrong side")
        void runBothRejectsWrongSide() {
            Guild guild = mixedGuild();
            assertThrows(IllegalArgumentException.class, () -> LineupAlgorithms.runBoth(
                    new HodorAlgorithm<>(TeamSide.TITAN, HodorAlgorithm.Variant.POWER),
                    new HodorAlgorithm<>(TeamSide.TITAN, HodorAlgorithm.Variant.POWER),
                    emptyLineup(guild), guild));
        }

        @Test
        @DisplayName("findOrDefault falls back to the side's first algorithm for an unknown name")
        void findOrDefaultFallsBack() {
            LineupAlgorithm fallback = LineupAlgorithms.findOrDefault(Lineup.TeamType.TITAN, "does not exist");
            assertSame(LineupAlgorithms.TITAN.get(0), fallback);
        }
    }

    @Nested
    @DisplayName("Combined algorithmName format")
    class AlgorithmNameTests {

        @Test
        @DisplayName("Replacing one side keeps the other side's part")
        void replacingOneSideKeepsTheOther() {
            String name = LineupAlgorithms.combinedAlgorithmName("", Lineup.TeamType.TITAN, "HODOR");
            assertEquals("Titans: HODOR", name);
            name = LineupAlgorithms.combinedAlgorithmName(name, Lineup.TeamType.HERO, "HODOR (CowScore)");
            assertEquals("Heroes: HODOR (CowScore); Titans: HODOR", name);
            name = LineupAlgorithms.combinedAlgorithmName(name, Lineup.TeamType.TITAN, "HODOR (CowScore)");
            assertEquals("Heroes: HODOR (CowScore); Titans: HODOR (CowScore)", name);
        }

        @Test
        @DisplayName("Parsing splits a combined name per side; blank and legacy names parse to nothing")
        void parsing() {
            assertEquals(Map.of(Lineup.TeamType.HERO, "HODOR", Lineup.TeamType.TITAN, "HODOR (CowScore)"),
                    LineupAlgorithms.parseAlgorithmName("Heroes: HODOR; Titans: HODOR (CowScore)"));
            assertTrue(LineupAlgorithms.parseAlgorithmName("").isEmpty());
            assertTrue(LineupAlgorithms.parseAlgorithmName("HODOR").isEmpty(),
                    "a pre-split, single-algorithm name belongs to neither side");
        }

        @Test
        @DisplayName("A legacy single-algorithm name is dropped once a side is run")
        void legacyNameIsReplaced() {
            assertEquals("Heroes: HODOR (CowScore)",
                    LineupAlgorithms.combinedAlgorithmName("HODOR", Lineup.TeamType.HERO, "HODOR (CowScore)"));
        }
    }
}
