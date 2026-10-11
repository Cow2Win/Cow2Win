package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.model.Fortification;

import java.util.*;

/**
 * The visual replay of one log, without Swing: the steps ({@link BattleDetailModel.ReplayStep},
 * one per log row in file order) applied to all fortifications of the catalog. A deliberately
 * simplified model - the game is more complicated.
 *
 * <p>{@link #stateAt(int)} gives the state after a number of steps (0 = start, nothing happened
 * yet): per fortification the beaten positions (a fight the attacker won), the positions captured
 * without a fight, captured / locked / current, plus the running totals. "Good" and "bad" are
 * always from our point of view (attack: the attacker won, defense: the attacker lost); points
 * always belong to the attacker.
 */
public final class BattleReplay {

    /**
     * One fortification at one step.
     *
     * @param fort        the catalog entry
     * @param beaten      positions where the attacker won a fight up to this step
     * @param repelled    positions where the attacker lost a fight up to this step and has not won one yet
     * @param free        positions shown as captured without a fight (see {@link #freePositions})
     * @param captured    captured up to this step
     * @param locked      has prerequisites and none of them is captured yet (never if captured)
     * @param current     the fortification of the current step
     */
    public record FortState(Fortification fort, Set<Integer> beaten, Set<Integer> repelled, Set<Integer> free,
                            boolean captured, boolean locked, boolean current) {
        public int capacity() {
            return fort.capacity();
        }
    }

    /**
     * The replay after {@code step} steps.
     *
     * @param step     number of steps done (0 = start)
     * @param forts    all fortifications of the catalog by id, in catalog order
     * @param points   sum of the points up to and including this step
     * @param good     fights good for us up to this step
     * @param bad      fights bad for us up to this step
     * @param captured fortifications captured up to this step
     * @param current  the current step, {@code null} at the start
     */
    public record State(int step, Map<String, FortState> forts, int points, int good, int bad, int captured,
                        BattleDetailModel.ReplayStep current) {

        /** The fortification of the current step, empty at the start or if it is unknown. */
        public Optional<FortState> currentFort() {
            return forts.values().stream().filter(FortState::current).findFirst();
        }
    }

    private final LogDirection direction;
    private final List<BattleDetailModel.ReplayStep> steps;
    private final Map<String, Fortification> forts = new LinkedHashMap<>();
    /** Per fortification: positions never fought at in the whole log, highest first. */
    private final Map<String, List<Integer>> unfought = new HashMap<>();

    public BattleReplay(LogDirection direction, List<BattleDetailModel.ReplayStep> steps,
                        List<Fortification> fortifications) {
        this.direction = Objects.requireNonNull(direction);
        this.steps = List.copyOf(steps);
        fortifications.forEach(f -> forts.put(f.id(), f));
        Map<String, Set<Integer>> fought = new HashMap<>();
        for (BattleDetailModel.ReplayStep s : this.steps) {
            if (s.kind() == BattleDetailModel.ReplayStep.Kind.FIGHT && s.fortificationId() != null) {
                fought.computeIfAbsent(s.fortificationId(), k -> new HashSet<>()).add(s.position());
            }
        }
        for (Fortification f : forts.values()) {
            Set<Integer> used = fought.getOrDefault(f.id(), Set.of());
            List<Integer> free = new ArrayList<>();
            for (int p = f.capacity(); p >= 1; p--) {
                if (!used.contains(p)) {
                    free.add(p);
                }
            }
            unfought.put(f.id(), free);
        }
    }

    public LogDirection direction() {
        return direction;
    }

    public List<BattleDetailModel.ReplayStep> steps() {
        return steps;
    }

    /** Number of steps. */
    public int size() {
        return steps.size();
    }

    /** All fortifications of the catalog by id. */
    public Map<String, Fortification> fortifications() {
        return Collections.unmodifiableMap(forts);
    }

    /** The catalog entry of a step's fortification, empty if it is unknown. */
    public Optional<Fortification> fortification(BattleDetailModel.ReplayStep step) {
        return Optional.ofNullable(step.fortificationId() == null ? null : forts.get(step.fortificationId()));
    }

    /**
     * The state after {@code step} steps (0 = start; clamped to 0..{@link #size()}).
     */
    public State stateAt(int step) {
        int n = Math.max(0, Math.min(step, steps.size()));
        Map<String, Set<Integer>> beaten = new HashMap<>();
        Map<String, Set<Integer>> repelled = new HashMap<>();
        Map<String, Integer> freeCount = new HashMap<>();
        Set<String> captured = new HashSet<>();
        int points = 0;
        int good = 0;
        int bad = 0;
        int capturedCount = 0;
        for (int i = 0; i < n; i++) {
            BattleDetailModel.ReplayStep s = steps.get(i);
            points += s.points();
            String id = s.fortificationId();
            switch (s.kind()) {
                case FIGHT -> {
                    if (s.good(direction)) {
                        good++;
                    } else {
                        bad++;
                    }
                    if (id != null) {
                        (s.attackerWins() ? beaten : repelled).computeIfAbsent(id, k -> new TreeSet<>()).add(s.position());
                    }
                }
                case UNDEFENDED -> {
                    if (id != null) {
                        freeCount.merge(id, s.freePositions(), Integer::sum);
                    }
                }
                case CAPTURED -> {
                    capturedCount++;
                    if (id != null) {
                        captured.add(id);
                    }
                }
            }
        }
        BattleDetailModel.ReplayStep current = n == 0 ? null : steps.get(n - 1);
        Map<String, FortState> states = new LinkedHashMap<>();
        for (Fortification f : forts.values()) {
            boolean isCaptured = captured.contains(f.id());
            boolean locked = !isCaptured && !f.prerequisites().isEmpty()
                    && f.prerequisites().stream().noneMatch(captured::contains);
            List<Integer> candidates = unfought.get(f.id());
            int count = Math.min(freeCount.getOrDefault(f.id(), 0), Math.min(f.capacity(), candidates.size()));
            Set<Integer> free = new TreeSet<>(candidates.subList(0, count));
            Set<Integer> fortBeaten = beaten.getOrDefault(f.id(), new TreeSet<>());
            Set<Integer> fortRepelled = new TreeSet<>(repelled.getOrDefault(f.id(), Set.of()));
            fortRepelled.removeAll(fortBeaten);
            states.put(f.id(), new FortState(f, Collections.unmodifiableSet(fortBeaten),
                    Collections.unmodifiableSet(fortRepelled),
                    Collections.unmodifiableSet(free), isCaptured, locked,
                    current != null && f.id().equals(current.fortificationId())));
        }
        return new State(n, Collections.unmodifiableMap(states), points, good, bad, capturedCount, current);
    }
}
