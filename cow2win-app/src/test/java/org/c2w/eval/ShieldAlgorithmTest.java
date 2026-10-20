package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.HodorSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ShieldAlgorithm} against the real catalog with small test guilds. */
class ShieldAlgorithmTest {

    private static final List<String> BASTIONS = List.of("bastion-of-fire", "gates-of-nature", "bastion-of-ice");

    // --- fixtures ----------------------------------------------------------

    /** A titan team of three titans, {@code fire} of them FIRE, the others DISTORTION. */
    private static TitanTeam titanTeam(String memberId, int index, int power, int fire) {
        List<Titan> titans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            titans.add(new Titan(memberId + "-" + index + "-" + i, i < fire ? TitanElement.FIRE : TitanElement.DISTORTION));
        }
        return new TitanTeam(memberId, index, titans, power);
    }

    private static HeroTeam heroTeam(String memberId, int index, int power) {
        return new HeroTeam(memberId, index, List.of(new Hero(memberId + "-hero-" + index, List.of(Role.MAGE))), power);
    }

    /** A titan team to build: power and number of fire titans. */
    private record Spec(int power, int fire) {
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
                titans.add(titanTeam(id, index, spec.power(), spec.fire()));
                heroes.add(heroTeam(id, index, spec.power()));
            }
            members.add(new GuildMember(id, id, heroes, titans));
        }
        return new Guild("g", "Guild", members);
    }

    /** {@code count} teams of distinct power, strongest first, without fire titans. */
    private static Guild guild(int count) {
        return guild(specs(count));
    }

    private static List<Spec> specs(int count) {
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            specs.add(new Spec(10_000 - i * 100, 0));
        }
        return specs;
    }

    private static Lineup emptyLineup(Guild guild) {
        return new Lineup(guild.id(), guild.name(), "", null, List.of());
    }

    private static <T> ShieldAlgorithm<T> shield(TeamSide<T> side, HodorSettings settings) {
        return new ShieldAlgorithm<>(side, () -> settings);
    }

    private static ShieldAlgorithm<TitanTeam> titans() {
        return shield(TeamSide.TITAN, HodorSettings.DEFAULTS);
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

    private static Map<ShieldAlgorithm.Row, List<String>> rowIds(FortificationType type) {
        Map<ShieldAlgorithm.Row, List<String>> ids = new EnumMap<>(ShieldAlgorithm.Row.class);
        ShieldAlgorithm.rows(FortificationRepository.findAll(), type).forEach((row, fortifications) ->
                ids.put(row, fortifications.stream().map(Fortification::id).sorted().toList()));
        return ids;
    }

    // --- tests -------------------------------------------------------------

    @Test
    @DisplayName("Rows and slots of the real catalog")
    void rows() {
        assertEquals(Map.of(
                ShieldAlgorithm.Row.BRIDGE, List.of("bridge"),
                ShieldAlgorithm.Row.FRONT, List.of("bastion-of-fire", "bastion-of-ice", "gates-of-nature", "spring-of-elements"),
                ShieldAlgorithm.Row.MIDDLE, List.of("altar-of-life", "ether-prism"),
                ShieldAlgorithm.Row.BACK, List.of("moon-temple", "sun-temple")), rowIds(FortificationType.TITAN));
        assertEquals(Map.of(
                ShieldAlgorithm.Row.FRONT, List.of("barracks", "engineerium", "foundry", "lighthouse", "mage-academy"),
                ShieldAlgorithm.Row.MIDDLE, List.of("bastion", "heros-bridge", "shooting-range"),
                ShieldAlgorithm.Row.BACK, List.of("alchemy-tower", "citadel", "city-hall")), rowIds(FortificationType.HERO));

        Map<ShieldAlgorithm.Row, Integer> titanSlots = slots(FortificationType.TITAN);
        assertEquals(Map.of(ShieldAlgorithm.Row.BRIDGE, 6, ShieldAlgorithm.Row.FRONT, 16,
                ShieldAlgorithm.Row.MIDDLE, 10, ShieldAlgorithm.Row.BACK, 8), titanSlots);
        assertEquals(Map.of(ShieldAlgorithm.Row.FRONT, 21, ShieldAlgorithm.Row.MIDDLE, 16,
                ShieldAlgorithm.Row.BACK, 18), slots(FortificationType.HERO));
    }

    private static Map<ShieldAlgorithm.Row, Integer> slots(FortificationType type) {
        Map<ShieldAlgorithm.Row, Integer> slots = new EnumMap<>(ShieldAlgorithm.Row.class);
        ShieldAlgorithm.rows(FortificationRepository.findAll(), type).forEach((row, fortifications) ->
                slots.put(row, fortifications.stream().mapToInt(Fortification::capacity).sum()));
        return slots;
    }

    @Test
    @DisplayName("Additive: existing entries stay, the other side stays untouched")
    void additive() {
        Guild guild = guild(10);
        Lineup.Entry manualTitan = new Lineup.Entry("spring-of-elements", "m000", Lineup.TeamType.TITAN, 0);
        Lineup.Entry manualHero = new Lineup.Entry("barracks", "m001", Lineup.TeamType.HERO, 0);
        Lineup start = new Lineup(guild.id(), guild.name(), "", null, List.of(manualTitan, manualHero));

        Lineup result = titans().run(start, guild);

        assertEquals(manualTitan, result.entries().get(0));
        assertEquals(manualHero, result.entries().get(1));
        assertEquals(List.of(manualHero), ofType(result, Lineup.TeamType.HERO));
        assertEquals(10, ofType(result, Lineup.TeamType.TITAN).size(), "every titan team exactly once");
        assertEquals("Titans: S.H.I.E.L.D", result.algorithmName());
    }

    @Test
    @DisplayName("Rows in order: every placed team of a front row is at least as strong as every one behind it")
    void rowOrder() {
        for (TeamSide<?> side : List.of(TeamSide.TITAN, TeamSide.HERO)) {
            FortificationType type = side.fortificationType();
            Guild guild = guild(capacity(type) + 4); // heroes: 59 teams, 30 members (the maximum)

            Lineup result = shield(side, HodorSettings.DEFAULTS).run(emptyLineup(guild), guild);

            Map<String, List<Integer>> powers = powersByFortification(result, guild, side.teamType());
            List<IntSummaryStatistics> rows = new ArrayList<>();
            ShieldAlgorithm.rows(FortificationRepository.findAll(), type).values().forEach(fortifications ->
                    rows.add(fortifications.stream()
                            .flatMap(fortification -> powers.getOrDefault(fortification.id(), List.of()).stream())
                            .mapToInt(Integer::intValue).summaryStatistics()));
            for (int front = 0; front < rows.size(); front++) {
                for (int back = front + 1; back < rows.size(); back++) {
                    assertTrue(rows.get(front).getMin() >= rows.get(back).getMax(),
                            type + ": row " + front + " vs. row " + back);
                }
            }
        }
    }

    @Test
    @DisplayName("Titans: the bridge gets the six strongest teams")
    void bridgeStrongest() {
        Guild guild = guild(capacity(FortificationType.TITAN) + 5);

        Lineup result = titans().run(emptyLineup(guild), guild);

        Set<Integer> bridge = new HashSet<>(powersByFortification(result, guild, Lineup.TeamType.TITAN).get("bridge"));
        assertEquals(Set.of(10_000, 9_900, 9_800, 9_700, 9_600, 9_500), bridge);
    }

    @Test
    @DisplayName("Exactly as many teams as free slots, the weakest stay out - whatever the reserve setting")
    void noReserve() {
        int capacity = capacity(FortificationType.TITAN);
        Guild guild = guild(capacity + 8);
        HodorSettings bigReserve = new HodorSettings(HodorSettings.MAX_RESERVE, HodorSettings.DEFAULTS.weights());
        HodorSettings noReserve = new HodorSettings(0, HodorSettings.DEFAULTS.weights());

        Lineup withBigReserve = shield(TeamSide.TITAN, bigReserve).run(emptyLineup(guild), guild);
        Lineup withoutReserve = shield(TeamSide.TITAN, noReserve).run(emptyLineup(guild), guild);

        assertEquals(withoutReserve.entries(), withBigReserve.entries());
        List<Lineup.Entry> placed = ofType(withBigReserve, Lineup.TeamType.TITAN);
        assertEquals(capacity, placed.size());
        int weakestPlaced = placed.stream().mapToInt(entry -> power(guild, entry)).min().orElseThrow();
        assertEquals(10_000 - (capacity - 1) * 100, weakestPlaced, "the eight weakest stay out");
    }

    @Test
    @DisplayName("Buff within a row: fire titans of the front row go to the Bastion of Fire, not to the Spring")
    void buffWithinRow() {
        List<Spec> specs = new ArrayList<>(specs(capacity(FortificationType.TITAN)));
        // Ranks 18-20 are in the front row (ranks 6-21), in its weaker half.
        for (int rank = 18; rank <= 20; rank++) {
            specs.set(rank, new Spec(specs.get(rank).power(), 3));
        }
        Guild guild = guild(specs);

        Lineup result = titans().run(emptyLineup(guild), guild);

        Set<Integer> firePowers = Set.of(specs.get(18).power(), specs.get(19).power(), specs.get(20).power());
        for (Lineup.Entry entry : ofType(result, Lineup.TeamType.TITAN)) {
            if (firePowers.contains(power(guild, entry))) {
                assertEquals("bastion-of-fire", entry.fortificationId(), "fire team " + power(guild, entry));
            }
        }
    }

    @Test
    @DisplayName("The three bastions are balanced: each has one of the three strongest teams of the front row")
    void bastionsBalanced() {
        Guild guild = guild(capacity(FortificationType.TITAN));

        Lineup result = titans().run(emptyLineup(guild), guild);

        Map<String, List<Integer>> powers = powersByFortification(result, guild, Lineup.TeamType.TITAN);
        Set<Integer> strongestOfFront = Set.of(9_400, 9_300, 9_200);
        Set<Integer> bastionMaxima = BASTIONS.stream()
                .map(bastion -> Collections.max(powers.get(bastion)))
                .collect(Collectors.toSet());
        assertEquals(strongestOfFront, bastionMaxima);
    }

    @Test
    @DisplayName("Too few teams: front rows full, back rows empty, no exception")
    void tooFewTeams() {
        Guild guild = guild(6 + 16 + 3);

        Lineup result = titans().run(emptyLineup(guild), guild);

        Map<String, List<Integer>> powers = powersByFortification(result, guild, Lineup.TeamType.TITAN);
        Map<ShieldAlgorithm.Row, List<String>> rows = rowIds(FortificationType.TITAN);
        assertEquals(6, powers.get("bridge").size());
        assertEquals(16, rows.get(ShieldAlgorithm.Row.FRONT).stream().mapToInt(id -> powers.get(id).size()).sum());
        assertEquals(3, rows.get(ShieldAlgorithm.Row.MIDDLE).stream()
                .mapToInt(id -> powers.getOrDefault(id, List.of()).size()).sum());
        assertTrue(rows.get(ShieldAlgorithm.Row.BACK).stream().noneMatch(powers::containsKey));

        Guild noTeams = new Guild("g", "Guild", List.of());
        assertEquals(List.of(), titans().run(emptyLineup(noTeams), noTeams).entries());
    }

    @Test
    @DisplayName("The same data gives the same result")
    void deterministic() {
        List<Spec> specs = new ArrayList<>();
        int[] powers = {4000, 7000, 7000, 3000, 9000, 5000, 5000, 8000, 1000, 6000, 2000, 7000};
        for (int i = 0; i < 4; i++) {
            for (int power : powers) {
                specs.add(new Spec(power + i, i % 2 == 0 ? 0 : 2));
            }
        }
        Guild guild = guild(specs);

        Lineup first = titans().run(emptyLineup(guild), guild);
        Lineup second = titans().run(emptyLineup(guild), guild);

        assertEquals(first.entries(), second.entries());
    }
}
