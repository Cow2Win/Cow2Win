package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Lineup;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.infra.Config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The algorithm "Run assignment" uses, per fortification type - chosen in the
 * "algorithm" combo box of the strategic concept's action list and used by the
 * menu entry as well (one instance per main window, shared by {@link ActionBar}
 * and the concept stage view).
 *
 * <p>Preselected from the configured default algorithms when created ("Manual"
 * or an unknown name preselects the first algorithm of the type, see {@link
 * LineupAlgorithms#preselected}). A choice lasts for the running session only:
 * it is never written to the config and survives guild and lineup switches.
 * Swing event thread only.
 */
public class AlgorithmSelection {

    private final Map<Lineup.TeamType, LineupAlgorithm> selected = new EnumMap<>(Lineup.TeamType.class);
    private final List<Consumer<Lineup.TeamType>> listeners = new ArrayList<>();

    public AlgorithmSelection() {
        selected.put(Lineup.TeamType.HERO,
                LineupAlgorithms.preselected(Lineup.TeamType.HERO, Config.getDefaultHeroAlgorithm()));
        selected.put(Lineup.TeamType.TITAN,
                LineupAlgorithms.preselected(Lineup.TeamType.TITAN, Config.getDefaultTitanAlgorithm()));
    }

    /** The algorithm chosen for {@code teamType}. */
    public LineupAlgorithm get(Lineup.TeamType teamType) {
        return selected.get(teamType);
    }

    /**
     * Chooses {@code algorithm} for its own type ({@link LineupAlgorithm#teamType()})
     * and notifies the listeners if that changed the choice.
     *
     * @throws IllegalArgumentException for a {@link ManualLineupAlgorithm} (it assigns nothing)
     */
    public void set(LineupAlgorithm algorithm) {
        if (algorithm == null || algorithm instanceof ManualLineupAlgorithm) {
            throw new IllegalArgumentException("Not an assigning algorithm: " + algorithm);
        }
        Lineup.TeamType teamType = algorithm.teamType();
        if (selected.put(teamType, algorithm) != algorithm) {
            listeners.forEach(listener -> listener.accept(teamType));
        }
    }

    /** The algorithms that can be chosen for {@code teamType}, see {@link LineupAlgorithms#forAssignment}. */
    public List<LineupAlgorithm> choices(Lineup.TeamType teamType) {
        return LineupAlgorithms.forAssignment(teamType);
    }

    /** The team type that defends fortifications of {@code fortificationType}. */
    public static Lineup.TeamType teamTypeOf(FortificationType fortificationType) {
        return fortificationType == FortificationType.HERO ? Lineup.TeamType.HERO : Lineup.TeamType.TITAN;
    }

    /** {@code listener} is told the type whose choice changed. */
    public void addListener(Consumer<Lineup.TeamType> listener) {
        listeners.add(listener);
    }
}
