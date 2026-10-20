package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.HodorSettings;
import org.c2w.infra.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Lineup strategies "HODOR" and "HODOR (CowScore)" - "Hold the door": the gates (bridges,
 * bastions, temples, see {@link HodorGates}) come first. Unlike the older strategies, which pick
 * team by team, HODOR rates a WHOLE lineup of this side with {@link HodorEvaluation} and improves
 * it until the rating no longer rises:
 * <ol>
 *     <li>Existing entries are kept and count in the rating, but are never moved (additive).</li>
 *     <li>Pool: the still-unassigned teams of this side by base strength (HODOR: totalPower,
 *     HODOR (CowScore): sortScore) - as many as there are free slots plus
 *     {@link HodorSettings#reserve()}.</li>
 *     <li>Start distribution: the strongest teams on the gate fortifications (gates by points behind,
 *     every gate fortification gets one team before any gets a second, OR-groups round-robin), then
 *     the buffed fortifications with the teams with the most matching units, then the rest by
 *     capture bonus.</li>
 *     <li>Improvement: swap two placed teams of different fortifications, swap a placed team with a
 *     reserve team, move a placed team to a free slot - always in the same order, taking the first
 *     improvement, so the same data always gives the same result.</li>
 * </ol>
 * The reserve teams left over stay out. HODOR rates a team by power and the real fortification buff
 * (weighted per {@link BuffEffect}, see {@link HodorSettings}); HODOR (CowScore) by its CowScore,
 * which replaces the real buff there.
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class HodorAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    /** The two variants - {@link #displayName()} is the stable algorithm ID. */
    public enum Variant {
        POWER("HODOR"),
        COW_SCORE("HODOR (CowScore)");

        private final String displayName;

        Variant(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** Safety limit for the improvement rounds - each accepted improvement is one round. */
    static final int MAX_ROUNDS = 10_000;

    /** The values set in the settings - see {@link #setSettings}. */
    private static volatile HodorSettings registeredSettings = HodorSettings.DEFAULTS;

    private final Variant variant;
    private final Supplier<HodorSettings> settings;

    public HodorAlgorithm(TeamSide<T> side, Variant variant) {
        this(side, variant, HodorAlgorithm::settings);
    }

    /** With its own settings - for tests. */
    HodorAlgorithm(TeamSide<T> side, Variant variant, Supplier<HodorSettings> settings) {
        super(side);
        this.variant = Objects.requireNonNull(variant, "variant");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Registers the values of the settings - at startup and whenever they are saved. */
    public static void setSettings(HodorSettings newSettings) {
        registeredSettings = newSettings.normalized();
    }

    /** The values currently registered via {@link #setSettings}. */
    public static HodorSettings settings() {
        return registeredSettings;
    }

    public Variant variant() {
        return variant;
    }

    @Override
    public String displayName() {
        return variant.displayName();
    }

    @Override
    protected void fillFortifications(List<Fortification> fortifications, List<Candidate<T>> pool,
                                      List<Lineup.Entry> updatedEntries, Guild guild) {
        if (fortifications.isEmpty()) {
            return;
        }
        List<HodorGates.Gate> gates = HodorGates.fromCatalog(FortificationRepository.findAll()).stream()
                .filter(gate -> gate.type() == side().fortificationType())
                .toList();
        new Search(fortifications, gates, pool, updatedEntries, guild).run();
    }

    /** One team in the search: a pool candidate or a resolvable existing entry, with cached values per fortification. */
    private record Unit<T>(int id, T team, String memberId, int teamIndex, int totalPower, double baseStrength) {
    }

    /** The state of one run: start distribution, improvement, result. */
    private final class Search {

        private final List<Fortification> fortifications;
        private final List<Lineup.Entry> updatedEntries;
        private final Guild guild;
        private final Map<String, Integer> indexById = new HashMap<>();
        private final List<HodorGates.Gate> gates;
        private final HodorEvaluation<Unit<T>> evaluation;
        /** matchingUnits[unit][fortification], cowScores[unit][fortification] (NaN = not computed yet). */
        private final List<int[]> matchingUnits = new ArrayList<>();
        private final List<double[]> cowScores = new ArrayList<>();
        /** Per fortification: existing entries (never moved, {@code null} = unresolvable team) and teams set here. */
        private final List<List<Unit<T>>> fixed = new ArrayList<>();
        private final List<List<Unit<T>>> placed = new ArrayList<>();
        private final int[] free;
        private final List<Unit<T>> reserve = new ArrayList<>();
        private final HodorEvaluation.FortificationValue[] values;

        Search(List<Fortification> fortifications, List<HodorGates.Gate> gates, List<Candidate<T>> pool,
               List<Lineup.Entry> updatedEntries, Guild guild) {
            this.fortifications = fortifications;
            this.gates = gates;
            this.updatedEntries = updatedEntries;
            this.guild = guild;
            for (int i = 0; i < fortifications.size(); i++) {
                indexById.put(fortifications.get(i).id(), i);
                fixed.add(new ArrayList<>());
                placed.add(new ArrayList<>());
            }
            HodorEvaluation.Strength<Unit<T>> strength = variant == Variant.POWER
                    ? HodorEvaluation.power(Unit::totalPower,
                    (unit, fortification) -> matching(unit, indexById.get(fortification.id())), settings.get())
                    : HodorEvaluation.cowScore(this::cowScore);
            this.evaluation = new HodorEvaluation<>(fortifications, gates, strength);
            this.free = new int[fortifications.size()];
            this.values = new HodorEvaluation.FortificationValue[fortifications.size()];

            collectExistingEntries();
            for (int i = 0; i < free.length; i++) {
                free[i] = Math.max(0, fortifications.get(i).capacity() - fixed.get(i).size());
            }
            reserve.addAll(selectPool(pool));
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
                    Logger.log(displayName() + " (" + side().teamType() + "): stopped after " + MAX_ROUNDS
                            + " improvement rounds (safety limit)");
                    break;
                }
            }
            for (int i = 0; i < fortifications.size(); i++) {
                for (Unit<T> unit : placed.get(i)) {
                    updatedEntries.add(new Lineup.Entry(fortifications.get(i).id(), unit.memberId(),
                            side().teamType(), unit.teamIndex()));
                }
            }
        }

        // --- setup ---

        /** The existing entries of this side's team type on this side's fortifications - they count in the rating. */
        private void collectExistingEntries() {
            for (Lineup.Entry entry : updatedEntries) {
                Integer index = indexById.get(entry.fortificationId());
                if (index == null || entry.teamType() != side().teamType()) {
                    continue;
                }
                GuildMember member = findMemberById(guild, entry.teamMemberId());
                List<T> teams = member == null ? List.of() : side().teamsOf().apply(member);
                if (entry.teamIndex() < 0 || entry.teamIndex() >= teams.size()) {
                    fixed.get(index).add(null);
                    continue;
                }
                T team = teams.get(entry.teamIndex());
                fixed.get(index).add(newUnit(team, entry.teamMemberId(), entry.teamIndex()));
            }
        }

        /** The strongest {@code free slots + reserve} candidates, strongest first. */
        private List<Unit<T>> selectPool(List<Candidate<T>> pool) {
            int size = Arrays.stream(free).sum() + settings.get().reserve();
            return pool.stream()
                    .sorted(Comparator.<Candidate<T>>comparingDouble(this::baseStrength).reversed()
                            .thenComparing(Candidate::memberId)
                            .thenComparingInt(Candidate::teamIndex))
                    .limit(size)
                    .map(candidate -> newUnit(candidate.team(), candidate.memberId(), candidate.teamIndex()))
                    .toList();
        }

        private double baseStrength(Candidate<T> candidate) {
            return variant == Variant.POWER ? candidate.totalPower() : candidate.sortScore();
        }

        private Unit<T> newUnit(T team, String memberId, int teamIndex) {
            int totalPower = side().totalPowerOf().applyAsInt(team);
            double baseStrength = variant == Variant.POWER ? totalPower : side().sortScoreOf().applyAsDouble(team);
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
                Buff buff = fortifications.get(fortificationIndex).buff();
                cache[fortificationIndex] = buff == null ? 0 : BuffCalculationService.countMatchingMembers(
                        new Lineup.Entry(fortifications.get(fortificationIndex).id(), unit.memberId(),
                                side().teamType(), unit.teamIndex()), guild, buff);
            }
            return cache[fortificationIndex];
        }

        private double cowScore(Unit<T> unit, Fortification fortification) {
            int index = indexById.get(fortification.id());
            double[] cache = cowScores.get(unit.id());
            if (Double.isNaN(cache[index])) {
                cache[index] = side().cowScoreOf().applyAsDouble(unit.team(), fortification);
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
}
