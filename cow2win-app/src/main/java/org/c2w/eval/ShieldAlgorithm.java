package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.HodorSettings;

import java.util.*;
import java.util.function.Supplier;

/**
 * Lineup strategy "S.H.I.E.L.D" - forward defense. The fortifications of this side are split into
 * {@link Row rows} by their {@link FortificationDepths unlock depth}: the bridge, then everything up
 * to depth 1 (up to the titan gates), the middle (depth 2, with the Heroes' Bridge) and the back
 * (depth 3 and more - the temples and the citadel). The strongest teams by totalPower fill the
 * front-most row, the next ones the following row and so on:
 * <ol>
 *     <li>Existing entries are kept and count in their row (additive).</li>
 *     <li>Pool: exactly as many teams as there are free slots, the strongest by totalPower - there
 *     is no reserve, the weakest teams stay out ({@link HodorSettings#reserve()} does not apply).</li>
 *     <li>The pool is cut row by row, front first; if teams are missing, the back rows stay (partly) empty.</li>
 *     <li>Per row one {@link HodorSearch} (variant POWER, weights from {@link HodorSettings}) with only
 *     the fortifications, the teams and the gates of that row - a team never changes its row.</li>
 * </ol>
 * Every gate lies completely in one row, so the overall rating is the sum of the rows and separate
 * searches are exactly "swap only within a row".
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class ShieldAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    /** The stable algorithm ID. */
    public static final String DISPLAY_NAME = "S.H.I.E.L.D";

    /** The rows, front first. */
    enum Row {
        /** Depth 0 and part of a gate (today only the titan "bridge"). */
        BRIDGE,
        /** The other fortifications up to depth 1. */
        FRONT,
        /** Depth 2. */
        MIDDLE,
        /** Depth 3 and more. */
        BACK
    }

    private final Supplier<HodorSettings> settings;

    public ShieldAlgorithm(TeamSide<T> side) {
        this(side, HodorAlgorithm::settings);
    }

    /** With its own settings - for tests. */
    ShieldAlgorithm(TeamSide<T> side, Supplier<HodorSettings> settings) {
        super(side);
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    /** The buff weights - no reserve. */
    @Override
    public Set<AlgorithmSetting> usedSettings() {
        return EnumSet.of(AlgorithmSetting.BUFF_WEIGHTS);
    }

    /**
     * The fortifications of {@code type} in {@code catalog} by row, front first, each in catalog
     * order; a row without fortifications is missing.
     */
    static Map<Row, List<Fortification>> rows(List<Fortification> catalog, FortificationType type) {
        Map<String, Integer> depths = FortificationDepths.fromCatalog(catalog);
        Set<String> gateFortifications = new HashSet<>();
        for (HodorGates.Gate gate : HodorGates.fromCatalog(catalog)) {
            gateFortifications.addAll(gate.fortificationIds());
        }
        Map<Row, List<Fortification>> rows = new EnumMap<>(Row.class);
        for (Fortification fortification : catalog) {
            if (fortification.type() == type) {
                rows.computeIfAbsent(rowOf(fortification, depths.get(fortification.id()), gateFortifications),
                        row -> new ArrayList<>()).add(fortification);
            }
        }
        return rows;
    }

    private static Row rowOf(Fortification fortification, int depth, Set<String> gateFortifications) {
        if (depth == 0 && gateFortifications.contains(fortification.id())) {
            return Row.BRIDGE;
        }
        if (depth <= 1) {
            return Row.FRONT;
        }
        return depth == 2 ? Row.MIDDLE : Row.BACK;
    }

    @Override
    protected void fillFortifications(List<Fortification> fortifications, List<Candidate<T>> pool,
                                      List<Lineup.Entry> updatedEntries, Guild guild) {
        if (fortifications.isEmpty()) {
            return;
        }
        List<Fortification> catalog = FortificationRepository.findAll();
        List<HodorGates.Gate> gates = HodorGates.fromCatalog(catalog).stream()
                .filter(gate -> gate.type() == side().fortificationType())
                .toList();
        Set<String> ofThisRun = new HashSet<>();
        fortifications.forEach(fortification -> ofThisRun.add(fortification.id()));
        Map<Row, List<Fortification>> rows = new EnumMap<>(Row.class);
        rows(catalog, side().fortificationType()).forEach((row, ofRow) -> {
            List<Fortification> filtered = ofRow.stream().filter(f -> ofThisRun.contains(f.id())).toList();
            if (!filtered.isEmpty()) {
                rows.put(row, filtered);
            }
        });

        Map<Row, Integer> freeByRow = new EnumMap<>(Row.class);
        rows.forEach((row, ofRow) -> freeByRow.put(row, ofRow.stream()
                .mapToInt(fortification -> HodorSearch.freeSlots(fortification, updatedEntries, side().teamType()))
                .sum()));
        List<Candidate<T>> strongest = pool.stream()
                .sorted(Comparator.<Candidate<T>>comparingInt(Candidate::totalPower).reversed()
                        .thenComparing(Candidate::memberId)
                        .thenComparingInt(Candidate::teamIndex))
                .limit(freeByRow.values().stream().mapToInt(Integer::intValue).sum())
                .toList();

        HodorSettings current = settings.get();
        int next = 0;
        for (Map.Entry<Row, List<Fortification>> row : rows.entrySet()) {
            int end = Math.min(strongest.size(), next + freeByRow.get(row.getKey()));
            List<Candidate<T>> teams = strongest.subList(next, end);
            next = end;
            Set<String> ids = new HashSet<>();
            row.getValue().forEach(fortification -> ids.add(fortification.id()));
            List<HodorGates.Gate> rowGates = gates.stream()
                    .filter(gate -> ids.containsAll(gate.fortificationIds()))
                    .toList();
            new HodorSearch<>(side(), displayName(), HodorAlgorithm.Variant.POWER, row.getValue(), rowGates,
                    current, teams, 0, updatedEntries, guild).run();
        }
    }
}
