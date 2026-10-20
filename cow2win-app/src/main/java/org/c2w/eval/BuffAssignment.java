package org.c2w.eval;

import java.util.*;

/**
 * Phase 1 of {@link MaxBuffAlgorithm}: distributes teams on the free slots of the buffed
 * fortifications so that the sum of matching units is maximal - solved exactly as a min-cost flow
 * (source → team (capacity 1) → fortification (capacity = free slots) → sink), not by swapping.
 *
 * <p>The objective is lexicographic: first the sum of matching units, then the sum of the power of
 * the teams set. Both are folded into one integer weight per edge, {@code matches × FACTOR + power}
 * with {@code FACTOR} greater than the sum of all powers. There is an edge only for
 * {@code matches > 0}; a slot may stay free. The teams are sorted by {@code memberId}/{@code teamIndex}
 * first and the fortifications keep the given order, so the same data always gives the same result,
 * whatever the order of {@code teams}.
 */
final class BuffAssignment {

    /**
     * One team.
     *
     * @param matches the matching units per fortification, in the order of the capacities
     */
    record Team(String memberId, int teamIndex, int totalPower, int[] matches) {
    }

    private BuffAssignment() {
    }

    /**
     * The fortification index per team (same order as {@code teams}), {@code -1} for a team that is
     * not set.
     *
     * @param teams      the candidates
     * @param capacities the free slots per buffed fortification
     */
    static int[] assign(List<Team> teams, int[] capacities) {
        int teamCount = teams.size();
        int fortificationCount = capacities.length;
        Integer[] order = new Integer[teamCount];
        for (int i = 0; i < teamCount; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.<Integer, String>comparing(i -> teams.get(i).memberId())
                .thenComparingInt(i -> teams.get(i).teamIndex()));

        long factor = 1;
        for (Team team : teams) {
            factor += Math.max(0, team.totalPower());
        }

        // Nodes: 0 = source, 1..n = teams (sorted), n+1..n+m = fortifications, n+m+1 = sink.
        Graph graph = new Graph(teamCount + fortificationCount + 2);
        int source = 0;
        int sink = teamCount + fortificationCount + 1;
        int[][] teamEdges = new int[teamCount][fortificationCount];
        for (int position = 0; position < teamCount; position++) {
            Team team = teams.get(order[position]);
            graph.addEdge(source, 1 + position, 1, 0);
            Arrays.fill(teamEdges[position], -1);
            for (int f = 0; f < fortificationCount; f++) {
                int matches = f < team.matches().length ? team.matches()[f] : 0;
                if (matches > 0 && capacities[f] > 0) {
                    long weight = matches * factor + Math.max(0, team.totalPower());
                    teamEdges[position][f] = graph.addEdge(1 + position, 1 + teamCount + f, 1, -weight);
                }
            }
        }
        for (int f = 0; f < fortificationCount; f++) {
            if (capacities[f] > 0) {
                graph.addEdge(1 + teamCount + f, sink, capacities[f], 0);
            }
        }

        graph.maxWeightFlow(source, sink);

        int[] result = new int[teamCount];
        Arrays.fill(result, -1);
        for (int position = 0; position < teamCount; position++) {
            for (int f = 0; f < fortificationCount; f++) {
                if (teamEdges[position][f] >= 0 && graph.flow(teamEdges[position][f]) > 0) {
                    result[order[position]] = f;
                }
            }
        }
        return result;
    }

    /** A small residual graph; costs are {@code long}, the edges are kept in insertion order. */
    private static final class Graph {

        private final int nodes;
        private final List<int[]> ends = new ArrayList<>(); // {from, to}
        private final List<Long> costs = new ArrayList<>();
        private final List<Integer> residual = new ArrayList<>();
        private final List<Integer> capacity = new ArrayList<>();

        Graph(int nodes) {
            this.nodes = nodes;
        }

        /** Adds the edge and its reverse edge; returns the index of the edge. */
        int addEdge(int from, int to, int cap, long cost) {
            int index = ends.size();
            ends.add(new int[]{from, to});
            costs.add(cost);
            residual.add(cap);
            capacity.add(cap);
            ends.add(new int[]{to, from});
            costs.add(-cost);
            residual.add(0);
            capacity.add(0);
            return index;
        }

        int flow(int edge) {
            return capacity.get(edge) - residual.get(edge);
        }

        /**
         * Successive shortest paths (Bellman-Ford, the residual graph has negative costs): the path
         * costs never fall, so augmenting stops at the first path that no longer gains anything -
         * the result is the flow of maximal weight over every flow value.
         */
        void maxWeightFlow(int source, int sink) {
            long[] distance = new long[nodes];
            int[] via = new int[nodes];
            while (true) {
                Arrays.fill(distance, Long.MAX_VALUE);
                Arrays.fill(via, -1);
                distance[source] = 0;
                for (int round = 0; round < nodes - 1; round++) {
                    boolean changed = false;
                    for (int edge = 0; edge < ends.size(); edge++) {
                        int from = ends.get(edge)[0];
                        int to = ends.get(edge)[1];
                        if (residual.get(edge) > 0 && distance[from] != Long.MAX_VALUE
                                && distance[from] + costs.get(edge) < distance[to]) {
                            distance[to] = distance[from] + costs.get(edge);
                            via[to] = edge;
                            changed = true;
                        }
                    }
                    if (!changed) {
                        break;
                    }
                }
                if (distance[sink] == Long.MAX_VALUE || distance[sink] >= 0) {
                    return;
                }
                // Every path starts with a source edge of capacity 1, so one unit per path.
                for (int node = sink; node != source; node = ends.get(via[node])[0]) {
                    int edge = via[node];
                    residual.set(edge, residual.get(edge) - 1);
                    residual.set(edge ^ 1, residual.get(edge ^ 1) + 1);
                }
            }
        }
    }
}
