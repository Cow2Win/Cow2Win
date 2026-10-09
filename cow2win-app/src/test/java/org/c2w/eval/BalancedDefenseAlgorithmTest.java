package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link BalancedDefenseAlgorithm}'s choice of WHICH fortification gets
 * the next team: the one with the lowest CowScore per slot (sum divided by the
 * fortification's capacity), not the lowest plain sum.
 *
 * <p>To make the expected order traceable by hand, the tests use a synthetic
 * {@link TeamSide} on the hero side whose "CowScore" is simply the team's
 * totalPower (fortification-independent) and whose sortScore/buff-fit make
 * {@link AbstractLineupAlgorithm#assignOne} always pick the strongest
 * remaining team - so teams leave the pool strictly strongest first, on
 * buffed and unbuffed fortifications alike.
 */
class BalancedDefenseAlgorithmTest {

    // --- fixtures ----------------------------------------------------------

    /** Hero side with power as score and a strictly strongest-first assignOne(). */
    private static final TeamSide<HeroTeam> POWER_SIDE = new TeamSide<>(
            Lineup.TeamType.HERO, FortificationType.HERO, "heros-bridge",
            GuildMember::heroTeams, HeroTeam::totalPower,
            (team, buff) -> team.totalPower(),           // buffed forts: highest power "fits" best
            team -> -team.totalPower(),                  // unbuffed forts: lowest sortScore = highest power
            (team, fortification) -> team.totalPower());

    private static Fortification fort(String id, int capacity, int strategicImportance) {
        return new Fortification(id, FortificationType.HERO, capacity, 100, 0, 0, null, List.of(), strategicImportance);
    }

    /** One member per team, so that member id identifies the team. */
    private static GuildMember member(String id, int power) {
        HeroTeam team = new HeroTeam(id, 0, List.of(new Hero(id + "-hero", List.of(Role.MAGE))), power);
        return new GuildMember(id, id, List.of(team), List.of());
    }

    private static Guild guild(List<GuildMember> members) {
        return new Guild("g", "Guild", members);
    }

    /** Runs fillFortifications directly on synthetic fortifications (no bridge). */
    private static List<Lineup.Entry> fill(List<Fortification> forts, Guild guild, List<Lineup.Entry> existing) {
        BalancedDefenseAlgorithm<HeroTeam> algorithm = new BalancedDefenseAlgorithm<>(POWER_SIDE);
        List<Lineup.Entry> entries = new ArrayList<>(existing);
        List<AbstractLineupAlgorithm.Candidate<HeroTeam>> pool =
                AbstractLineupAlgorithm.buildCandidatePool(POWER_SIDE, guild, entries);
        algorithm.fillFortifications(null, forts, pool, entries, guild, Map.of());
        return entries;
    }

    /** Powers of the teams on {@code fortificationId}, in assignment order (member id = "p" + power). */
    private static List<Integer> powersOn(List<Lineup.Entry> entries, String fortificationId) {
        return entries.stream()
                .filter(entry -> entry.fortificationId().equals(fortificationId))
                .map(entry -> Integer.parseInt(entry.teamMemberId().substring(1)))
                .toList();
    }

    // --- tests -------------------------------------------------------------

    @Test
    @DisplayName("Small vs. big fortification: the next team goes to the lowest score PER SLOT, not the lowest sum")
    void smallVersusBigFortification() {
        Fortification small = fort("a-small", 2, 5);   // wins the tiebreak (higher importance)
        Fortification big = fort("b-big", 4, 1);
        Guild guild = guild(List.of(member("p10", 10), member("p9", 9), member("p8", 8),
                member("p7", 7), member("p6", 6), member("p5", 5)));

        List<Lineup.Entry> entries = fill(List.of(small, big), guild, List.of());

        // Per slot: A gets 10 (5.0); B 9 (2.25), 8 (4.25), 7 (6.0); A 6 (8.0, full); B 5.
        // (The former sum-based order would have produced A = {10, 7}, B = {9, 8, 6, 5}.)
        assertEquals(List.of(10, 6), powersOn(entries, "a-small"));
        assertEquals(List.of(9, 8, 7, 5), powersOn(entries, "b-big"));
    }

    @Test
    @DisplayName("strengthPerSlot divides the CowScore sum by the capacity, not by the occupied slots")
    void strengthPerSlotDividesByCapacity() {
        Fortification big = fort("b-big", 4, 1);
        Guild guild = guild(List.of(member("p10", 10), member("p6", 6)));
        List<Lineup.Entry> entries = List.of(
                new Lineup.Entry("b-big", "p10", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("b-big", "p6", Lineup.TeamType.HERO, 0));

        double strength = new BalancedDefenseAlgorithm<>(POWER_SIDE).strengthPerSlot(big, entries, guild);

        assertEquals(4.0, strength, 1e-9, "(10 + 6) / capacity 4, not / 2 occupied slots");
    }

    @Test
    @DisplayName("A fortification already reinforced by hand gets the next team later than an empty one of equal size")
    void preAssignedTeamCountsTowardsStartStrength() {
        Fortification reinforced = fort("a-reinforced", 3, 5);   // would win the tiebreak if both were empty
        Fortification empty = fort("b-empty", 3, 5);
        Guild guild = guild(List.of(member("p100", 100), member("p10", 10), member("p9", 9), member("p8", 8)));
        List<Lineup.Entry> manual = List.of(new Lineup.Entry("a-reinforced", "p100", Lineup.TeamType.HERO, 0));

        List<Lineup.Entry> entries = fill(List.of(reinforced, empty), guild, manual);

        // 100/3 = 33.3 per slot is never undercut by the empty fortification's 10, 9, 8 (max 9.0),
        // so all three pool teams go to the empty one - starting with the very first.
        assertEquals("b-empty", entries.get(1).fortificationId(), "first new team goes to the empty fortification");
        assertEquals(List.of(100), powersOn(entries, "a-reinforced"));
        assertEquals(List.of(10, 9, 8), powersOn(entries, "b-empty"));
    }

    @Test
    @DisplayName("run(): every fortification gets one team before any gets a second (real catalog)")
    void runGivesEveryFortificationOneTeamFirst() {
        List<Fortification> heroForts = heroFortsWithoutBridge();
        int bridgeCapacity = FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.id().equals("heros-bridge"))
                .findFirst().orElseThrow().capacity();

        // Exactly enough for the bridge plus one team per other fortification.
        Guild guild = descendingGuild(bridgeCapacity + heroForts.size());
        Lineup result = new BalancedDefenseAlgorithm<>(POWER_SIDE)
                .run(new Lineup(guild.id(), guild.name(), "", null, List.of()), guild);

        Map<String, Long> counts = countsPerFortification(result);
        assertEquals(bridgeCapacity, counts.getOrDefault("heros-bridge", 0L));
        for (Fortification fortification : heroForts) {
            assertEquals(1L, counts.getOrDefault(fortification.id(), 0L),
                    fortification.id() + " must get exactly one team");
        }
    }

    @Test
    @DisplayName("run(): no self-reinforcement - equally sized fortifications differ by at most one team")
    void runHasNoSelfReinforcement() {
        List<Fortification> heroForts = heroFortsWithoutBridge();
        // Narrow, strictly falling scores: any k + 1 teams outweigh any k teams, so among equally
        // sized fortifications per-slot balancing must keep the team counts within one of each other.
        Guild guild = descendingGuild(36);
        Lineup result = new BalancedDefenseAlgorithm<>(POWER_SIDE)
                .run(new Lineup(guild.id(), guild.name(), "", null, List.of()), guild);

        Map<String, Long> counts = countsPerFortification(result);
        Map<Integer, List<Fortification>> byCapacity = heroForts.stream()
                .collect(Collectors.groupingBy(Fortification::capacity));
        for (Map.Entry<Integer, List<Fortification>> group : byCapacity.entrySet()) {
            LongSummaryStatistics stats = group.getValue().stream()
                    .mapToLong(fortification -> counts.getOrDefault(fortification.id(), 0L))
                    .summaryStatistics();
            assertTrue(stats.getMax() - stats.getMin() <= 1,
                    "capacity " + group.getKey() + ": team counts " + stats.getMin() + ".." + stats.getMax());
            assertTrue(stats.getMin() >= 1, "every fortification of capacity " + group.getKey() + " gets a team");
        }
    }

    // --- helpers -----------------------------------------------------------

    private static List<Fortification> heroFortsWithoutBridge() {
        return FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == FortificationType.HERO)
                .filter(fortification -> !fortification.id().equals("heros-bridge"))
                .toList();
    }

    /** {@code count} teams with powers 1000, 999, 998, ..., three per member (guilds cap at 30 members). */
    private static Guild descendingGuild(int count) {
        List<GuildMember> members = new ArrayList<>();
        for (int first = 0; first < count; first += 3) {
            String memberId = "m" + members.size();
            List<HeroTeam> teams = new ArrayList<>();
            for (int i = first; i < Math.min(first + 3, count); i++) {
                teams.add(new HeroTeam(memberId, teams.size(),
                        List.of(new Hero(memberId + "-hero" + teams.size(), List.of(Role.MAGE))), 1000 - i));
            }
            members.add(new GuildMember(memberId, memberId, teams, List.of()));
        }
        return guild(members);
    }

    private static Map<String, Long> countsPerFortification(Lineup lineup) {
        return lineup.entries().stream()
                .collect(Collectors.groupingBy(Lineup.Entry::fortificationId, Collectors.counting()));
    }
}
