package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.HodorSettings;

import java.util.*;
import java.util.function.Supplier;

/**
 * Lineup strategy "MAX_BUFF" - the biggest buff first: the buffed fortifications get the teams so
 * that as many units as possible match their buff (role or element), no matter how strong the
 * teams are; only then counts the strength.
 * <ol>
 *     <li>Existing entries are kept (additive).</li>
 *     <li>Pool like HODOR: the strongest teams by totalPower, {@code free slots + }{@link HodorSettings#reserve()}.</li>
 *     <li>Phase 1 - buff: every buffed fortification counts the same. The pool is distributed on their
 *     free slots so that the sum of matching units is maximal, on a tie the stronger teams win -
 *     solved exactly by {@link BuffAssignment}. Teams without a matching unit are not set here.</li>
 *     <li>Phase 2 - rest: first the bridges (fortifications that alone form a {@link HodorGates gate},
 *     today {@code bridge} and {@code heros-bridge}) with the strongest remaining teams, then every
 *     other free slot - fortifications without buff and buffed slots left over - by capture bonus,
 *     strongest team first.</li>
 * </ol>
 * The pool teams left over stay out. No CowScore, no buff weights.
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class MaxBuffAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    /** The stable algorithm ID. */
    public static final String DISPLAY_NAME = "MAX_BUFF";

    private static final Comparator<Candidate<?>> STRONGEST_FIRST = Comparator
            .<Candidate<?>>comparingInt(Candidate::totalPower).reversed()
            .thenComparing(Candidate::memberId)
            .thenComparingInt(Candidate::teamIndex);

    private final Supplier<HodorSettings> settings;

    public MaxBuffAlgorithm(TeamSide<T> side) {
        this(side, HodorAlgorithm::settings);
    }

    /** With its own settings - for tests. */
    MaxBuffAlgorithm(TeamSide<T> side, Supplier<HodorSettings> settings) {
        super(side);
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    /** Only the reserve - the buff weights do not count. */
    @Override
    public Set<AlgorithmSetting> usedSettings() {
        return EnumSet.of(AlgorithmSetting.RESERVE);
    }

    @Override
    protected void fillFortifications(List<Fortification> fortifications, List<Candidate<T>> pool,
                                      List<Lineup.Entry> updatedEntries, Guild guild) {
        if (fortifications.isEmpty()) {
            return;
        }
        Lineup.TeamType teamType = side().teamType();
        Map<String, Integer> free = new HashMap<>();
        for (Fortification fortification : fortifications) {
            free.put(fortification.id(), HodorSearch.freeSlots(fortification, updatedEntries, teamType));
        }
        int freeTotal = free.values().stream().mapToInt(Integer::intValue).sum();
        List<Candidate<T>> remaining = new ArrayList<>(pool.stream()
                .sorted(STRONGEST_FIRST)
                .limit(freeTotal + (long) settings.get().reserve())
                .toList());

        // Phase 1: the buffed fortifications, exactly.
        List<Fortification> buffed = fortifications.stream()
                .filter(fortification -> fortification.buff() != null)
                .toList();
        int[] capacities = buffed.stream().mapToInt(fortification -> free.get(fortification.id())).toArray();
        List<BuffAssignment.Team> teams = new ArrayList<>();
        for (Candidate<T> candidate : remaining) {
            int[] matches = buffed.stream()
                    .mapToInt(fortification -> HodorSearch.matchingUnits(fortification, teamType,
                            candidate.memberId(), candidate.teamIndex(), guild))
                    .toArray();
            teams.add(new BuffAssignment.Team(candidate.memberId(), candidate.teamIndex(), candidate.totalPower(),
                    matches));
        }
        int[] assigned = BuffAssignment.assign(teams, capacities);
        List<Candidate<T>> setInPhase1 = new ArrayList<>();
        for (int f = 0; f < buffed.size(); f++) {
            Fortification fortification = buffed.get(f);
            for (int i = 0; i < remaining.size(); i++) {
                if (assigned[i] == f) {
                    place(fortification, remaining.get(i), free, updatedEntries);
                    setInPhase1.add(remaining.get(i));
                }
            }
        }
        remaining.removeAll(setInPhase1);

        // Phase 2: the bridges, then every other free slot by capture bonus.
        Map<String, Fortification> byId = new LinkedHashMap<>();
        fortifications.forEach(fortification -> byId.put(fortification.id(), fortification));
        List<Fortification> order = new ArrayList<>();
        for (HodorGates.Gate gate : HodorGates.fromCatalog(FortificationRepository.findAll())) {
            if (gate.type() == side().fortificationType() && gate.fortificationIds().size() == 1
                    && byId.containsKey(gate.fortificationIds().get(0))) {
                order.add(byId.get(gate.fortificationIds().get(0)));
            }
        }
        fortifications.stream()
                .filter(fortification -> !order.contains(fortification))
                .sorted(Comparator.comparingInt(Fortification::captureBonus).reversed()
                        .thenComparing(Fortification::id))
                .forEach(order::add);
        for (Fortification fortification : order) {
            while (free.get(fortification.id()) > 0 && !remaining.isEmpty()) {
                place(fortification, remaining.remove(0), free, updatedEntries);
            }
        }
    }

    private void place(Fortification fortification, Candidate<T> candidate, Map<String, Integer> free,
                       List<Lineup.Entry> updatedEntries) {
        updatedEntries.add(new Lineup.Entry(fortification.id(), candidate.memberId(), side().teamType(),
                candidate.teamIndex()));
        free.merge(fortification.id(), -1, Integer::sum);
    }
}
