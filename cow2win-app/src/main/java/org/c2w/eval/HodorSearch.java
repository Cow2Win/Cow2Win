package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.HodorSettings;
import org.c2w.infra.Logger;

import java.util.*;

/**
 * One HODOR search (see {@link HodorAlgorithm}): start distribution, improvement by swapping, result.
 * Runs on any subset of the fortifications of one side with the gates given - {@link HodorAlgorithm}
 * passes every fortification of the side and a reserve from the settings, {@link ShieldAlgorithm}
 * one row at a time with exactly the teams of that row and no reserve. {@link MaxBuffAlgorithm} only
 * uses the shared helpers ({@link #freeSlots}, {@link #matchingUnits}).
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
final class HodorSearch<T> {

    /** Safety limit for the improvement rounds - each accepted improvement is one round. */
    static final int MAX_ROUNDS = 10_000;

    /** One team in the search: a pool candidate or a resolvable existing entry; {@code id} indexes the per-fortification caches. */
    private record Unit<T>(int id, T team, String memberId, int teamIndex, int totalPower, double baseStrength) {
    }

    private final TeamSide<T> side;
    private final String algorithmName;
    private final HodorAlgorithm.Variant variant;
    private final List<Fortification> fortifications;
    private final List<Lineup.Entry> updatedEntries;
    private final Guild guild;
    private final Map<String, Integer> indexById = new HashMap<>();
    private final List<HodorGates.Gate> gates;
    private final HodorEvaluation<Unit<T>> evaluation;
    /** matchingUnits[unit][fortification] (-1 = not computed yet), cowScores[unit][fortification] (NaN = not computed yet). */
    private final List<int[]> matchingUnits = new ArrayList<>();
    private final List<double[]> cowScores = new ArrayList<>();
    /** Per fortification: existing entries (never moved, {@code null} = unresolvable team) and teams set here. */
    private final List<List<Unit<T>>> fixed = new ArrayList<>();
    private final List<List<Unit<T>>> placed = new ArrayList<>();
    private final int[] free;
    private final List<Unit<T>> reserve = new ArrayList<>();
    private final HodorEvaluation.FortificationValue[] values;

    /**
     * @param algorithmName  for the log message when the safety limit is hit
     * @param fortifications the fortifications to fill (of {@code side})
     * @param gates          the gates that count in the rating
     * @param settings       the weights (variant POWER)
     * @param pool           the candidates - the strongest {@code free slots + reserve} of them take part
     * @param reserve        how many teams beyond the free slots take part
     * @param updatedEntries every entry so far (mutable, append-only) - the existing ones of these
     *                       fortifications count in the rating, {@link #run} appends the new ones
     */
    HodorSearch(TeamSide<T> side, String algorithmName, HodorAlgorithm.Variant variant,
                List<Fortification> fortifications, List<HodorGates.Gate> gates, HodorSettings settings,
                List<AbstractLineupAlgorithm.Candidate<T>> pool, int reserve,
                List<Lineup.Entry> updatedEntries, Guild guild) {
        this.side = side;
        this.algorithmName = algorithmName;
        this.variant = variant;
        this.fortifications = fortifications;
        this.gates = gates;
        this.updatedEntries = updatedEntries;
        this.guild = guild;
        for (int i = 0; i < fortifications.size(); i++) {
            indexById.put(fortifications.get(i).id(), i);
            fixed.add(new ArrayList<>());
            placed.add(new ArrayList<>());
        }
        HodorEvaluation.Strength<Unit<T>> strength = variant == HodorAlgorithm.Variant.POWER
                ? HodorEvaluation.power(Unit::totalPower,
                (unit, fortification) -> matching(unit, indexById.get(fortification.id())), settings)
                : HodorEvaluation.cowScore(this::cowScore);
        this.evaluation = new HodorEvaluation<>(fortifications, gates, strength);
        this.free = new int[fortifications.size()];
        this.values = new HodorEvaluation.FortificationValue[fortifications.size()];

        collectExistingEntries();
        for (int i = 0; i < free.length; i++) {
            free[i] = Math.max(0, fortifications.get(i).capacity() - fixed.get(i).size());
        }
        this.reserve.addAll(selectPool(pool, reserve));
    }

    /**
     * The free slots of {@code fortification} for {@code teamType}: capacity minus the existing
     * entries of that team type on it (unresolvable ones included) - the same count the search uses.
     */
    static int freeSlots(Fortification fortification, List<Lineup.Entry> entries, Lineup.TeamType teamType) {
        long taken = entries.stream()
                .filter(entry -> entry.teamType() == teamType && entry.fortificationId().equals(fortification.id()))
                .count();
        return (int) Math.max(0, fortification.capacity() - taken);
    }

    void run() {
        startDistribution();
        for (int i = 0; i < values.length; i++) {
            values[i] = value(i, placed.get(i));
        }
        int rounds = 0;
        while (improve()) {
            rounds++;
            if (rounds >= MAX_ROUNDS) {
                Logger.log(algorithmName + " (" + side.teamType() + "): stopped after " + MAX_ROUNDS
                        + " improvement rounds (safety limit)");
                break;
            }
        }
        for (int i = 0; i < fortifications.size(); i++) {
            for (Unit<T> unit : placed.get(i)) {
                updatedEntries.add(new Lineup.Entry(fortifications.get(i).id(), unit.memberId(),
                        side.teamType(), unit.teamIndex()));
            }
        }
    }

    // --- setup ---

    /** The existing entries of this side's team type on these fortifications - they count in the rating. */
    private void collectExistingEntries() {
        for (Lineup.Entry entry : updatedEntries) {
            Integer index = indexById.get(entry.fortificationId());
            if (index == null || entry.teamType() != side.teamType()) {
                continue;
            }
            GuildMember member = AbstractLineupAlgorithm.findMemberById(guild, entry.teamMemberId());
            List<T> teams = member == null ? List.of() : side.teamsOf().apply(member);
            if (entry.teamIndex() < 0 || entry.teamIndex() >= teams.size()) {
                fixed.get(index).add(null);
                continue;
            }
            T team = teams.get(entry.teamIndex());
            fixed.get(index).add(newUnit(team, entry.teamMemberId(), entry.teamIndex()));
        }
    }

    /** The strongest {@code free slots + reserve} candidates, strongest first. */
    private List<Unit<T>> selectPool(List<AbstractLineupAlgorithm.Candidate<T>> pool, int reserve) {
        int size = Arrays.stream(free).sum() + reserve;
        return pool.stream()
                .sorted(Comparator.<AbstractLineupAlgorithm.Candidate<T>>comparingDouble(this::baseStrength).reversed()
                        .thenComparing(AbstractLineupAlgorithm.Candidate::memberId)
                        .thenComparingInt(AbstractLineupAlgorithm.Candidate::teamIndex))
                .limit(size)
                .map(candidate -> newUnit(candidate.team(), candidate.memberId(), candidate.teamIndex()))
                .toList();
    }

    private double baseStrength(AbstractLineupAlgorithm.Candidate<T> candidate) {
        return variant == HodorAlgorithm.Variant.POWER ? candidate.totalPower() : candidate.sortScore();
    }

    private Unit<T> newUnit(T team, String memberId, int teamIndex) {
        int totalPower = side.totalPowerOf().applyAsInt(team);
        double baseStrength = variant == HodorAlgorithm.Variant.POWER
                ? totalPower : side.sortScoreOf().applyAsDouble(team);
        Unit<T> unit = new Unit<>(matchingUnits.size(), team, memberId, teamIndex, totalPower, baseStrength);
        int[] matching = new int[fortifications.size()];
        Arrays.fill(matching, -1);
        matchingUnits.add(matching);
        double[] scores = new double[fortifications.size()];
        Arrays.fill(scores, Double.NaN);
        cowScores.add(scores);
        return unit;
    }

    private int matching(Unit<T> unit, int fortificationIndex) {
        int[] cache = matchingUnits.get(unit.id());
        if (cache[fortificationIndex] < 0) {
            cache[fortificationIndex] = matchingUnits(fortifications.get(fortificationIndex), side.teamType(),
                    unit.memberId(), unit.teamIndex(), guild);
        }
        return cache[fortificationIndex];
    }

    /**
     * The units of the team ({@code memberId}, {@code teamIndex}) that match the buff of
     * {@code fortification} (role or element, no totems) - 0 without a buff. Shared by HODOR and MAX_BUFF.
     */
    static int matchingUnits(Fortification fortification, Lineup.TeamType teamType, String memberId, int teamIndex,
                             Guild guild) {
        Buff buff = fortification.buff();
        return buff == null ? 0 : BuffCalculationService.countMatchingMembers(
                new Lineup.Entry(fortification.id(), memberId, teamType, teamIndex), guild, buff);
    }

    private double cowScore(Unit<T> unit, Fortification fortification) {
        int index = indexById.get(fortification.id());
        double[] cache = cowScores.get(unit.id());
        if (Double.isNaN(cache[index])) {
            cache[index] = side.cowScoreOf().applyAsDouble(unit.team(), fortification);
        }
        return cache[index];
    }

    // --- start distribution ---

    private void startDistribution() {
        Set<Integer> gateFortifications = new LinkedHashSet<>();
        List<List<Integer>> gateGroups = new ArrayList<>();
        for (HodorGates.Gate gate : gates) {
            List<Integer> group = gate.fortificationIds().stream()
                    .map(indexById::get)
                    .filter(Objects::nonNull)
                    .toList();
            gateGroups.add(group);
            gateFortifications.addAll(group);
        }
        // Every gate fortification gets its first team before any gets a second.
        for (int index : gateFortifications) {
            placeStrongest(index);
        }
        // Then gate by gate (points behind descending), round-robin within an OR-group.
        for (List<Integer> group : gateGroups) {
            boolean progress = true;
            while (progress && !reserve.isEmpty()) {
                progress = false;
                for (int index : group) {
                    if (placeStrongest(index)) {
                        progress = true;
                    }
                }
            }
        }
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < fortifications.size(); i++) {
            if (!gateFortifications.contains(i)) {
                rest.add(i);
            }
        }
        rest.sort(Comparator.<Integer>comparingInt(i -> fortifications.get(i).captureBonus()).reversed()
                .thenComparing(i -> fortifications.get(i).id()));
        // Buffed fortifications: the teams with the most matching units.
        for (int index : rest) {
            if (fortifications.get(index).buff() != null) {
                while (free[index] > 0 && !reserve.isEmpty()) {
                    Unit<T> best = reserve.stream()
                            .sorted(Comparator.<Unit<T>>comparingInt(unit -> matching(unit, index)).reversed()
                                    .thenComparing(Comparator.comparingDouble(Unit<T>::baseStrength).reversed())
                                    .thenComparing(Unit::memberId)
                                    .thenComparingInt(Unit::teamIndex))
                            .findFirst()
                            .orElseThrow();
                    place(best, index);
                }
            }
        }
        // The rest by capture bonus, strongest first.
        for (int index : rest) {
            while (placeStrongest(index)) {
                // fills the fortification
            }
        }
    }

    /** Places the strongest reserve team (the reserve is kept strongest first) on {@code index}, if there is room. */
    private boolean placeStrongest(int index) {
        if (free[index] <= 0 || reserve.isEmpty()) {
            return false;
        }
        place(reserve.get(0), index);
        return true;
    }

    private void place(Unit<T> unit, int index) {
        reserve.remove(unit);
        placed.get(index).add(unit);
        free[index]--;
    }

    // --- improvement ---

    /** Looks for the first improvement in the fixed order and applies it; false if there is none. */
    private boolean improve() {
        double current = evaluation.total(values);
        double threshold = current + 1e-9 * Math.max(1, Math.abs(current));
        int count = fortifications.size();
        // 1. Swap two placed teams of different fortifications.
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                List<Unit<T>> first = placed.get(i);
                List<Unit<T>> second = placed.get(j);
                for (int a = 0; a < first.size(); a++) {
                    for (int b = 0; b < second.size(); b++) {
                        List<Unit<T>> newFirst = new ArrayList<>(first);
                        List<Unit<T>> newSecond = new ArrayList<>(second);
                        newFirst.set(a, second.get(b));
                        newSecond.set(b, first.get(a));
                        if (acceptIfBetter(i, newFirst, j, newSecond, threshold)) {
                            return true;
                        }
                    }
                }
            }
        }
        // 2. Swap a placed team with a reserve team.
        for (int i = 0; i < count; i++) {
            List<Unit<T>> teams = placed.get(i);
            for (int a = 0; a < teams.size(); a++) {
                for (int r = 0; r < reserve.size(); r++) {
                    List<Unit<T>> newTeams = new ArrayList<>(teams);
                    Unit<T> out = teams.get(a);
                    newTeams.set(a, reserve.get(r));
                    if (acceptIfBetter(i, newTeams, -1, null, threshold)) {
                        reserve.set(r, out);
                        return true;
                    }
                }
            }
        }
        // 3. Move a placed team to a free slot (only if teams are missing).
        for (int i = 0; i < count; i++) {
            for (int a = 0; a < placed.get(i).size(); a++) {
                for (int j = 0; j < count; j++) {
                    if (j == i || free[j] <= 0) {
                        continue;
                    }
                    List<Unit<T>> newFrom = new ArrayList<>(placed.get(i));
                    Unit<T> moved = newFrom.remove(a);
                    List<Unit<T>> newTo = new ArrayList<>(placed.get(j));
                    newTo.add(moved);
                    if (acceptIfBetter(i, newFrom, j, newTo, threshold)) {
                        free[i]++;
                        free[j]--;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Applies the new teams of fortification {@code i} (and {@code j}, if not -1) if the rating then exceeds {@code threshold}. */
    private boolean acceptIfBetter(int i, List<Unit<T>> newI, int j, List<Unit<T>> newJ, double threshold) {
        HodorEvaluation.FortificationValue oldI = values[i];
        HodorEvaluation.FortificationValue oldJ = j < 0 ? null : values[j];
        values[i] = value(i, newI);
        if (j >= 0) {
            values[j] = value(j, newJ);
        }
        if (evaluation.total(values) > threshold) {
            placed.set(i, newI);
            if (j >= 0) {
                placed.set(j, newJ);
            }
            return true;
        }
        values[i] = oldI;
        if (j >= 0) {
            values[j] = oldJ;
        }
        return false;
    }

    /** The value of fortification {@code index} with its existing entries plus {@code teams}. */
    private HodorEvaluation.FortificationValue value(int index, List<Unit<T>> teams) {
        List<Unit<T>> all = new ArrayList<>(fixed.get(index));
        all.addAll(teams);
        return evaluation.valueOf(fortifications.get(index), all);
    }
}
