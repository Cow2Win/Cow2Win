package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.HodorSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** {@link HodorAlgorithm} (both variants) against the real catalog with small test guilds. */
class HodorAlgorithmTest {

    private static final Set<String> TITAN_GATES = Set.of("bridge", "bastion-of-fire", "gates-of-nature",
            "bastion-of-ice", "sun-temple", "moon-temple");
    private static final List<String> BASTIONS = List.of("bastion-of-fire", "gates-of-nature", "bastion-of-ice");

    // --- fixtures ----------------------------------------------------------

    /** A titan team of three titans of {@code element}, {@code fire} of them FIRE instead. */
    private static TitanTeam titanTeam(String memberId, int index, int power, int fire) {
        List<Titan> titans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            titans.add(new Titan(memberId + "-" + index + "-" + i, i < fire ? TitanElement.FIRE : TitanElement.DISTORTION));
        }
        return new TitanTeam(memberId, index, titans, power);
    }

    private static HeroTeam heroTeam(String memberId, int power) {
        return new HeroTeam(memberId, 0, List.of(new Hero(memberId + "-hero", List.of(Role.MAGE))), power);
    }

    /** A titan team to build: power and number of fire titans. */
    private record Spec(int power, int fire) {
    }

    /** Two titan teams per member (as many members as needed), each member also has one hero team. */
    private static Guild titanGuild(List<Spec> specs) {
        List<GuildMember> members = new ArrayList<>();
        for (int i = 0; i < specs.size(); i += 2) {
            String id = String.format("m%03d", i / 2);
            List<TitanTeam> teams = new ArrayList<>();
            for (int index = 0; index < 2 && i + index < specs.size(); index++) {
                Spec spec = specs.get(i + index);
                teams.add(titanTeam(id, index, spec.power(), spec.fire()));
            }
            members.add(new GuildMember(id, id, List.of(heroTeam(id, specs.get(i).power())), teams));
        }
        return new Guild("g", "Guild", members);
    }

    /** Titan teams of the given powers, without fire titans - see {@link #titanGuild}. */
    private static Guild guild(int... powers) {
        return titanGuild(Arrays.stream(powers).mapToObj(power -> new Spec(power, 0)).toList());
    }

    /** {@code count} teams 10_000, 9_990, ... without fire, then one team of {@code firePower} with three fire titans. */
    private static List<Spec> withFireTeam(int count, int firePower) {
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            specs.add(new Spec(10_000 - i * 10, 0));
        }
        specs.add(new Spec(firePower, 3));
        return specs;
    }

    private static int[] descending(int count, int strongest) {
        int[] powers = new int[count];
        for (int i = 0; i < count; i++) {
            powers[i] = strongest - i * 100;
        }
        return powers;
    }

    private static Lineup emptyLineup(Guild guild) {
        return new Lineup(guild.id(), guild.name(), "", null, List.of());
    }

    private static HodorAlgorithm<TitanTeam> titans(HodorAlgorithm.Variant variant, HodorSettings settings) {
        return new HodorAlgorithm<>(TeamSide.TITAN, variant, () -> settings);
    }

    private static HodorSettings withReserve(int reserve) {
        return new HodorSettings(reserve, HodorSettings.DEFAULTS.weights());
    }

    private static HodorSettings withHealthWeight(double weight) {
        Map<BuffEffect, Double> weights = new EnumMap<>(HodorSettings.DEFAULTS.weights());
        weights.put(BuffEffect.HEALTH_INCREASE, weight);
        return new HodorSettings(HodorSettings.DEFAULT_RESERVE, weights);
    }

    private static int titanCapacity() {
        return FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == FortificationType.TITAN)
                .mapToInt(Fortification::capacity)
                .sum();
    }

    private static List<Lineup.Entry> ofType(Lineup lineup, Lineup.TeamType teamType) {
        return lineup.entries().stream().filter(entry -> entry.teamType() == teamType).toList();
    }

    private static int power(Guild guild, Lineup.Entry entry) {
        return guild.members().stream().filter(member -> member.id().equals(entry.teamMemberId())).findFirst()
                .orElseThrow().titanTeams().get(entry.teamIndex()).totalPower();
    }

    private static Map<String, List<Lineup.Entry>> byFortification(Lineup lineup, Lineup.TeamType teamType) {
        return ofType(lineup, teamType).stream().collect(Collectors.groupingBy(Lineup.Entry::fortificationId));
    }

    // --- tests -------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(HodorAlgorithm.Variant.class)
    @DisplayName("Additive: existing entries stay, the other side stays untouched")
    void additive(HodorAlgorithm.Variant variant) {
        Guild guild = guild(descending(10, 5000));
        Lineup.Entry manualTitan = new Lineup.Entry("spring-of-elements", "m000", Lineup.TeamType.TITAN, 0);
        Lineup.Entry manualHero = new Lineup.Entry("barracks", "m001", Lineup.TeamType.HERO, 0);
        Lineup start = new Lineup(guild.id(), guild.name(), "", null, List.of(manualTitan, manualHero));

        Lineup result = titans(variant, HodorSettings.DEFAULTS).run(start, guild);

        assertEquals(manualTitan, result.entries().get(0));
        assertEquals(manualHero, result.entries().get(1));
        assertEquals(List.of(manualHero), ofType(result, Lineup.TeamType.HERO));
        assertEquals(10, ofType(result, Lineup.TeamType.TITAN).size(), "every titan team exactly once");
        assertEquals(1, ofType(result, Lineup.TeamType.TITAN).stream()
                .filter(entry -> entry.teamMemberId().equals("m000") && entry.teamIndex() == 0).count());
        assertEquals("Titans: " + variant.displayName(), result.algorithmName());
    }

    @ParameterizedTest
    @EnumSource(HodorAlgorithm.Variant.class)
    @DisplayName("Enough teams: every slot full, exactly as many teams as slots, the rest stays out")
    void allSlotsFull(HodorAlgorithm.Variant variant) {
        int capacity = titanCapacity();
        Guild guild = guild(descending(capacity + 8, 10_000));

        Lineup result = titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        assertEquals(capacity, ofType(result, Lineup.TeamType.TITAN).size());
        Map<String, List<Lineup.Entry>> byFortification = byFortification(result, Lineup.TeamType.TITAN);
        for (Fortification fortification : FortificationRepository.findAll()) {
            if (fortification.type() == FortificationType.TITAN) {
                assertEquals(fortification.capacity(), byFortification.get(fortification.id()).size(), fortification.id());
            }
        }
        assertEquals(capacity, new HashSet<>(ofType(result, Lineup.TeamType.TITAN).stream()
                .map(entry -> entry.teamMemberId() + "/" + entry.teamIndex()).toList()).size(), "no team twice");
    }

    @Test
    @DisplayName("Reserve: a slightly weaker team with fire titans displaces a stronger one without")
    void reserveWorks() {
        int capacity = titanCapacity();
        int firePower = 10_000 - capacity * 10;
        Guild guild = titanGuild(withFireTeam(capacity, firePower));

        Lineup withReserve = titans(HodorAlgorithm.Variant.POWER, withReserve(3)).run(emptyLineup(guild), guild);
        Lineup withoutReserve = titans(HodorAlgorithm.Variant.POWER, withReserve(0)).run(emptyLineup(guild), guild);

        Optional<Lineup.Entry> fire = ofType(withReserve, Lineup.TeamType.TITAN).stream()
                .filter(entry -> power(guild, entry) == firePower).findFirst();
        assertTrue(fire.isPresent(), "the fire team comes in from the reserve");
        assertEquals("bastion-of-fire", fire.get().fortificationId());
        assertEquals(capacity, ofType(withReserve, Lineup.TeamType.TITAN).size());
        assertTrue(ofType(withoutReserve, Lineup.TeamType.TITAN).stream()
                .noneMatch(entry -> power(guild, entry) == firePower), "without reserve it is not in the pool");
    }

    @ParameterizedTest
    @EnumSource(HodorAlgorithm.Variant.class)
    @DisplayName("The strongest teams hold the gates; every bastion has a strong team")
    void strongestOnGates(HodorAlgorithm.Variant variant) {
        int capacity = titanCapacity();
        Guild guild = guild(descending(capacity + 3, 10_000));

        Lineup result = titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        Map<String, List<Lineup.Entry>> byFortification = byFortification(result, Lineup.TeamType.TITAN);
        Set<Integer> gateMaxima = new HashSet<>();
        for (String gate : TITAN_GATES) {
            gateMaxima.add(byFortification.get(gate).stream().mapToInt(entry -> power(guild, entry)).max().orElseThrow());
        }
        Set<Integer> topSix = Arrays.stream(descending(6, 10_000)).boxed().collect(Collectors.toSet());
        assertEquals(topSix, gateMaxima, "the six strongest teams are the gate strengths of the six gate fortifications");
        for (String bastion : BASTIONS) {
            int strongest = byFortification.get(bastion).stream().mapToInt(entry -> power(guild, entry)).max().orElseThrow();
            assertTrue(strongest >= 10_000 - 5 * 100, bastion + ": " + strongest);
        }
    }

    @ParameterizedTest
    @EnumSource(HodorAlgorithm.Variant.class)
    @DisplayName("Too few teams: no exception, every gate fortification gets a team first")
    void tooFewTeams(HodorAlgorithm.Variant variant) {
        Guild guild = guild(descending(TITAN_GATES.size(), 10_000));

        Lineup result = titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        assertEquals(TITAN_GATES, ofType(result, Lineup.TeamType.TITAN).stream()
                .map(Lineup.Entry::fortificationId).collect(Collectors.toSet()));

        Guild twoTeams = guild(1000, 900);
        assertEquals(2, ofType(titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(twoTeams), twoTeams),
                Lineup.TeamType.TITAN).size());
        Guild noTeams = new Guild("g", "Guild", List.of());
        assertEquals(List.of(), titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(noTeams), noTeams).entries());
    }

    @Test
    @DisplayName("Heroes: one team goes to the Heroes' Bridge")
    void heroGate() {
        Guild guild = guild(1000);

        Lineup result = new HodorAlgorithm<>(TeamSide.HERO, HodorAlgorithm.Variant.POWER, () -> HodorSettings.DEFAULTS)
                .run(emptyLineup(guild), guild);

        assertEquals(List.of(new Lineup.Entry("heros-bridge", "m000", Lineup.TeamType.HERO, 0)), result.entries());
    }

    @ParameterizedTest
    @EnumSource(HodorAlgorithm.Variant.class)
    @DisplayName("The same data gives the same result")
    void deterministic(HodorAlgorithm.Variant variant) {
        Guild guild = guild(4000, 7000, 7000, 3000, 9000, 5000, 5000, 8000, 1000, 6000, 2000, 7000);

        Lineup first = titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);
        Lineup second = titans(variant, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        assertEquals(first.entries(), second.entries());
    }

    @Test
    @DisplayName("A weight change only affects HODOR, not HODOR (CowScore)")
    void weightsOnlyForHodor() {
        int capacity = titanCapacity();
        int firePower = 10_000 - capacity * 10;
        Guild guild = titanGuild(withFireTeam(capacity, firePower));

        Lineup hodorNoHealth = titans(HodorAlgorithm.Variant.POWER, withHealthWeight(0)).run(emptyLineup(guild), guild);
        Lineup hodorHealth = titans(HodorAlgorithm.Variant.POWER, withHealthWeight(2)).run(emptyLineup(guild), guild);
        assertNotEquals(hodorNoHealth.entries(), hodorHealth.entries());
        assertTrue(hodorNoHealth.entries().stream().noneMatch(entry -> power(guild, entry) == firePower));

        Lineup cowScoreNoHealth = titans(HodorAlgorithm.Variant.COW_SCORE, withHealthWeight(0)).run(emptyLineup(guild), guild);
        Lineup cowScoreHealth = titans(HodorAlgorithm.Variant.COW_SCORE, withHealthWeight(2)).run(emptyLineup(guild), guild);
        assertEquals(cowScoreNoHealth.entries(), cowScoreHealth.entries());
    }

    @Test
    @DisplayName("HODOR (CowScore) rates with cowScoreOf: with an inverted CowScore the reserve (weakest) teams win")
    void cowScoreVariantUsesCowScoreOf() {
        TeamSide<TitanTeam> invertedCowScore = new TeamSide<>(Lineup.TeamType.TITAN, FortificationType.TITAN,
                GuildMember::titanTeams, TitanTeam::totalPower, TitanTeam::sortScore,
                (team, fortification) -> 100_000 - team.totalPower());
        int capacity = titanCapacity();
        Guild guild = guild(descending(capacity + 3, 10_000));

        Lineup result = new HodorAlgorithm<>(invertedCowScore, HodorAlgorithm.Variant.COW_SCORE, () -> withReserve(3))
                .run(emptyLineup(guild), guild);

        Set<Integer> placed = ofType(result, Lineup.TeamType.TITAN).stream().map(entry -> power(guild, entry))
                .collect(Collectors.toSet());
        assertFalse(placed.contains(10_000), "the strongest team is swapped for a reserve team");
        assertTrue(placed.contains(10_000 - (capacity + 2) * 100), "the weakest team comes in");
    }

    @Test
    @DisplayName("LineupAlgorithms: HODOR (the default), HODOR (CowScore), Manual")
    void registration() {
        for (Lineup.TeamType teamType : Lineup.TeamType.values()) {
            List<String> names = LineupAlgorithms.forType(teamType).stream().map(LineupAlgorithm::displayName).toList();
            assertEquals(List.of("HODOR", "HODOR (CowScore)", ManualLineupAlgorithm.DISPLAY_NAME), names);
        }
    }
}
