package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.HodorSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** {@link MaxBuffAlgorithm} against the real catalog with small test guilds. */
class MaxBuffAlgorithmTest {

    // --- fixtures ----------------------------------------------------------

    /** A team to build: power, the elements of its three titans and the roles of its single hero. */
    private record Spec(int power, List<TitanElement> elements, List<Role> roles) {
    }

    private static Spec spec(int power, int fire) {
        List<TitanElement> elements = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            elements.add(i < fire ? TitanElement.FIRE : TitanElement.DISTORTION);
        }
        return new Spec(power, elements, List.of(Role.MAGE));
    }

    /** {@code count} teams 10_000, 9_900, ... without fire titans, every hero a mage. */
    private static List<Spec> specs(int count) {
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            specs.add(spec(10_000 - i * 100, 0));
        }
        return specs;
    }

    /** Per member two titan teams and two hero teams of the same powers. */
    private static Guild guild(List<Spec> specs) {
        List<GuildMember> members = new ArrayList<>();
        for (int i = 0; i < specs.size(); i += 2) {
            String id = String.format("m%03d", i / 2);
            List<TitanTeam> titans = new ArrayList<>();
            List<HeroTeam> heroes = new ArrayList<>();
            for (int index = 0; index < 2 && i + index < specs.size(); index++) {
                Spec spec = specs.get(i + index);
                List<Titan> titanList = new ArrayList<>();
                for (int t = 0; t < spec.elements().size(); t++) {
                    titanList.add(new Titan(id + "-" + index + "-" + t, spec.elements().get(t)));
                }
                titans.add(new TitanTeam(id, index, titanList, spec.power()));
                heroes.add(new HeroTeam(id, index, List.of(new Hero(id + "-hero-" + index, spec.roles())), spec.power()));
            }
            members.add(new GuildMember(id, id, heroes, titans));
        }
        return new Guild("g", "Guild", members);
    }

    /** {@code count} teams of distinct power with random titan elements and random hero roles (one or two each). */
    private static List<Spec> mixedSpecs(int count, long seed) {
        Random random = new Random(seed);
        TitanElement[] elements = TitanElement.values();
        Role[] roles = Role.values();
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<TitanElement> titanElements = new ArrayList<>();
            for (int t = 0; t < 3; t++) {
                titanElements.add(elements[random.nextInt(elements.length)]);
            }
            Set<Role> heroRoles = new LinkedHashSet<>();
            heroRoles.add(roles[random.nextInt(roles.length)]);
            if (random.nextBoolean()) {
                heroRoles.add(roles[random.nextInt(roles.length)]);
            }
            specs.add(new Spec(5_000 + random.nextInt(5_000) * 2 + i % 2, titanElements, List.copyOf(heroRoles)));
        }
        return specs;
    }

    private static Lineup emptyLineup(Guild guild) {
        return new Lineup(guild.id(), guild.name(), "", null, List.of());
    }

    private static HodorSettings withReserve(int reserve) {
        return new HodorSettings(reserve, HodorSettings.DEFAULTS.weights());
    }

    private static <T> MaxBuffAlgorithm<T> maxBuff(TeamSide<T> side, HodorSettings settings) {
        return new MaxBuffAlgorithm<>(side, () -> settings);
    }

    private static int capacity(FortificationType type) {
        return FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == type)
                .mapToInt(Fortification::capacity)
                .sum();
    }

    private static List<Lineup.Entry> ofType(Lineup lineup, Lineup.TeamType teamType) {
        return lineup.entries().stream().filter(entry -> entry.teamType() == teamType).toList();
    }

    private static int power(Guild guild, Lineup.Entry entry) {
        GuildMember member = guild.members().stream().filter(m -> m.id().equals(entry.teamMemberId()))
                .findFirst().orElseThrow();
        return entry.teamType() == Lineup.TeamType.TITAN
                ? member.titanTeams().get(entry.teamIndex()).totalPower()
                : member.heroTeams().get(entry.teamIndex()).totalPower();
    }

    private static Map<String, List<Integer>> powersByFortification(Lineup lineup, Guild guild, Lineup.TeamType teamType) {
        return ofType(lineup, teamType).stream().collect(Collectors.groupingBy(Lineup.Entry::fortificationId,
                Collectors.mapping(entry -> power(guild, entry), Collectors.toList())));
    }

    /** Sum of the matching units over every buffed fortification of {@code type}. */
    private static int buffSum(Lineup lineup, Guild guild, FortificationType type) {
        return FortificationRepository.findAll().stream()
                .filter(fortification -> fortification.type() == type && fortification.buff() != null)
                .mapToInt(fortification -> BuffCalculationService.countMatchingMembersForFortification(
                        fortification.id(), lineup, guild, fortification.buff()))
                .sum();
    }

    // --- tests -------------------------------------------------------------

    @Test
    @DisplayName("Additive: existing entries stay, the other side stays untouched")
    void additive() {
        Guild guild = guild(specs(10));
        Lineup.Entry manualTitan = new Lineup.Entry("spring-of-elements", "m000", Lineup.TeamType.TITAN, 0);
        Lineup.Entry manualHero = new Lineup.Entry("barracks", "m001", Lineup.TeamType.HERO, 0);
        Lineup start = new Lineup(guild.id(), guild.name(), "", null, List.of(manualTitan, manualHero));

        Lineup result = maxBuff(TeamSide.TITAN, HodorSettings.DEFAULTS).run(start, guild);

        assertEquals(manualTitan, result.entries().get(0));
        assertEquals(manualHero, result.entries().get(1));
        assertEquals(List.of(manualHero), ofType(result, Lineup.TeamType.HERO));
        assertEquals(10, ofType(result, Lineup.TeamType.TITAN).size(), "every titan team exactly once");
        assertEquals(10, new HashSet<>(ofType(result, Lineup.TeamType.TITAN).stream()
                .map(entry -> entry.teamMemberId() + "/" + entry.teamIndex()).toList()).size());
        assertEquals("Titans: MAX_BUFF", result.algorithmName());
    }

    @Test
    @DisplayName("At least as many matching units in the buffed fortifications as HODOR with the same data")
    void atLeastHodor() {
        for (long seed = 1; seed <= 5; seed++) {
            Guild guild = guild(mixedSpecs(60, seed));
            for (TeamSide<?> side : List.of(TeamSide.TITAN, TeamSide.HERO)) {
                FortificationType type = side.fortificationType();
                Lineup maxBuff = maxBuff(side, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);
                Lineup hodor = new HodorAlgorithm<>(side, HodorAlgorithm.Variant.POWER, () -> HodorSettings.DEFAULTS)
                        .run(emptyLineup(guild), guild);

                assertTrue(buffSum(maxBuff, guild, type) >= buffSum(hodor, guild, type),
                        type + " seed " + seed + ": " + buffSum(maxBuff, guild, type) + " < " + buffSum(hodor, guild, type));
                assertEquals(capacity(type), ofType(maxBuff, side.teamType()).size(), type + " seed " + seed);
            }
        }
    }

    @Test
    @DisplayName("Titans: fire teams to the Bastion of Fire, the bridge gets the strongest teams left after phase 1")
    void bridgeTitans() {
        List<Spec> specs = specs(capacity(FortificationType.TITAN) + 5);
        for (int rank = 0; rank < 3; rank++) {
            specs.set(rank, spec(specs.get(rank).power(), 3));
        }
        Guild guild = guild(specs);

        Lineup result = maxBuff(TeamSide.TITAN, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        Map<String, List<Integer>> powers = powersByFortification(result, guild, Lineup.TeamType.TITAN);
        assertTrue(powers.get("bastion-of-fire").containsAll(List.of(10_000, 9_900, 9_800)));
        assertEquals(Set.of(9_700, 9_600, 9_500, 9_400, 9_300, 9_200), new HashSet<>(powers.get("bridge")));
    }

    @Test
    @DisplayName("Heroes: the mages fill a buffed fortification first, the Heroes' Bridge gets the strongest left")
    void bridgeHeroes() {
        Guild guild = guild(specs(capacity(FortificationType.HERO) + 4)); // 59 teams, 30 members (the maximum)

        Lineup result = maxBuff(TeamSide.HERO, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        Map<String, List<Integer>> powers = powersByFortification(result, guild, Lineup.TeamType.HERO);
        assertEquals(Set.of(10_000, 9_900, 9_800, 9_700, 9_600), new HashSet<>(powers.get("alchemy-tower")),
                "the only mage fortification gets the five strongest mages");
        assertEquals(Set.of(9_500, 9_400, 9_300, 9_200, 9_100, 9_000), new HashSet<>(powers.get("heros-bridge")));
    }

    @Test
    @DisplayName("Exactly as many teams as free slots; the reserve brings weak matching teams in")
    void reserve() {
        int capacity = capacity(FortificationType.TITAN);
        List<Spec> specs = specs(capacity + 8);
        int weakFire = specs.get(capacity + 7).power();
        specs.set(capacity + 7, spec(weakFire, 3));
        Guild guild = guild(specs);

        Lineup withReserve = maxBuff(TeamSide.TITAN, withReserve(8)).run(emptyLineup(guild), guild);
        Lineup withoutReserve = maxBuff(TeamSide.TITAN, withReserve(0)).run(emptyLineup(guild), guild);

        assertEquals(capacity, ofType(withReserve, Lineup.TeamType.TITAN).size());
        assertEquals(capacity, ofType(withoutReserve, Lineup.TeamType.TITAN).size());
        assertTrue(powersByFortification(withReserve, guild, Lineup.TeamType.TITAN).get("bastion-of-fire")
                .contains(weakFire), "the reserve brings the fire team in");
        assertTrue(ofType(withoutReserve, Lineup.TeamType.TITAN).stream().noneMatch(entry -> power(guild, entry) == weakFire),
                "without reserve only the strongest");
    }

    @Test
    @DisplayName("Too few teams: no exception, the bridge is filled first")
    void tooFewTeams() {
        Guild guild = guild(specs(9));

        Lineup result = maxBuff(TeamSide.TITAN, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

        Map<String, List<Integer>> powers = powersByFortification(result, guild, Lineup.TeamType.TITAN);
        assertEquals(Set.of(10_000, 9_900, 9_800, 9_700, 9_600, 9_500), new HashSet<>(powers.get("bridge")));
        assertEquals(9, ofType(result, Lineup.TeamType.TITAN).size());

        Guild noTeams = new Guild("g", "Guild", List.of());
        assertEquals(List.of(), maxBuff(TeamSide.TITAN, HodorSettings.DEFAULTS).run(emptyLineup(noTeams), noTeams).entries());
    }

    @Test
    @DisplayName("The same data gives the same result")
    void deterministic() {
        Guild guild = guild(mixedSpecs(50, 3));
        for (TeamSide<?> side : List.of(TeamSide.TITAN, TeamSide.HERO)) {
            Lineup first = maxBuff(side, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);
            Lineup second = maxBuff(side, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);
            assertEquals(first.entries(), second.entries());
        }
    }
}
