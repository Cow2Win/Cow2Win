package org.c2w.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** {@link BuffAssignment} - the exact phase 1 of MAX_BUFF, without guild or catalog. */
class BuffAssignmentTest {

    private static BuffAssignment.Team team(String memberId, int power, int... matches) {
        return new BuffAssignment.Team(memberId, 0, power, matches);
    }

    private static int sum(List<BuffAssignment.Team> teams, int[] assigned) {
        int sum = 0;
        for (int i = 0; i < teams.size(); i++) {
            if (assigned[i] >= 0) {
                sum += teams.get(i).matches()[assigned[i]];
            }
        }
        return sum;
    }

    @Test
    @DisplayName("Mixed teams: 3 fire + 2 light goes to the Sun Temple, the pure fire team to the Bastion of Fire")
    void mixedTeams() {
        // Fortifications: 0 = Bastion of Fire, 1 = Sun Temple, one free slot each.
        BuffAssignment.Team mixed = team("a", 9_000, 3, 2);
        BuffAssignment.Team fire = team("b", 5_000, 3, 0);
        List<BuffAssignment.Team> teams = List.of(mixed, fire);

        int[] assigned = BuffAssignment.assign(teams, new int[]{1, 1});

        assertArrayEquals(new int[]{1, 0}, assigned);
        assertEquals(5, sum(teams, assigned));
    }

    @Test
    @DisplayName("Two roles: a hero team that fits two role fortifications goes where the total is maximal")
    void twoRoles() {
        // The strong team fits both (1 unit each), the weak one only fortification 0 (2 units).
        BuffAssignment.Team twoRoles = team("a", 9_000, 1, 1);
        BuffAssignment.Team oneRole = team("b", 1_000, 2, 0);
        List<BuffAssignment.Team> teams = List.of(twoRoles, oneRole);

        int[] assigned = BuffAssignment.assign(teams, new int[]{1, 1});

        assertArrayEquals(new int[]{1, 0}, assigned);
        assertEquals(3, sum(teams, assigned));
    }

    @Test
    @DisplayName("Tie in matching units: the stronger team gets the slot")
    void tie() {
        List<BuffAssignment.Team> teams = List.of(team("a", 4_000, 2), team("b", 6_000, 2), team("c", 5_000, 2));

        int[] assigned = BuffAssignment.assign(teams, new int[]{1});

        assertArrayEquals(new int[]{-1, 0, -1}, assigned);
    }

    @Test
    @DisplayName("More matching units beat more power")
    void matchesBeforePower() {
        List<BuffAssignment.Team> teams = List.of(team("a", 10_000, 1), team("b", 1_000, 2));

        assertArrayEquals(new int[]{-1, 0}, BuffAssignment.assign(teams, new int[]{1}));
    }

    @Test
    @DisplayName("Teams without a matching unit stay out, capacities are kept")
    void noMatchAndCapacities() {
        List<BuffAssignment.Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            teams.add(team("m" + i, 10_000 - i, 1, 0));
        }
        teams.add(team("none", 20_000, 0, 0));

        int[] assigned = BuffAssignment.assign(teams, new int[]{2, 3});

        assertEquals(-1, assigned[6], "no matching unit");
        assertEquals(2, Arrays.stream(assigned).filter(f -> f == 0).count());
        assertEquals(0, Arrays.stream(assigned).filter(f -> f == 1).count(), "nobody matches fortification 1");
        assertArrayEquals(new int[]{0, 0, -1, -1, -1, -1, -1}, assigned, "the two strongest");
        assertArrayEquals(new int[0], BuffAssignment.assign(List.of(), new int[]{3}));
        assertArrayEquals(new int[]{-1}, BuffAssignment.assign(List.of(team("x", 1, 2)), new int[]{0}));
    }

    @Test
    @DisplayName("Optimal on a case where greedy fails")
    void exact() {
        // Greedy by matches would put a (3) on 0 and lose c (3 + 2 = 5); the optimum is c on 0, a on 1, b on 2 (7).
        List<BuffAssignment.Team> teams = List.of(team("a", 1, 3, 2, 0), team("b", 1, 0, 1, 2), team("c", 1, 3, 0, 0));

        int[] assigned = BuffAssignment.assign(teams, new int[]{1, 1, 1});

        assertArrayEquals(new int[]{1, 2, 0}, assigned);
        assertEquals(7, sum(teams, assigned));
    }

    @Test
    @DisplayName("Deterministic: same result twice and for a shuffled input order")
    void deterministic() {
        List<BuffAssignment.Team> teams = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 40; i++) {
            // Many exact ties on purpose: few distinct powers and matches.
            teams.add(new BuffAssignment.Team(String.format("m%02d", i / 2), i % 2, 1_000 * (i % 3),
                    new int[]{random.nextInt(3), random.nextInt(3), random.nextInt(3)}));
        }
        int[] capacities = {4, 4, 5};

        int[] first = BuffAssignment.assign(teams, capacities);
        assertArrayEquals(first, BuffAssignment.assign(teams, capacities));

        Map<BuffAssignment.Team, Integer> expected = new IdentityHashMap<>();
        for (int i = 0; i < teams.size(); i++) {
            expected.put(teams.get(i), first[i]);
        }
        List<BuffAssignment.Team> shuffled = new ArrayList<>(teams);
        Collections.shuffle(shuffled, new Random(7));
        int[] second = BuffAssignment.assign(shuffled, capacities);
        for (int i = 0; i < shuffled.size(); i++) {
            assertEquals(expected.get(shuffled.get(i)), second[i], shuffled.get(i).memberId());
        }
    }
}
