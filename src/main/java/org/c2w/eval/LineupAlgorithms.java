package org.c2w.eval;

import java.util.List;

public final class LineupAlgorithms {

    /** Every available strategy, in the order the combo box should list them. */
    public static final List<LineupAlgorithm> ALL = List.of(
            new BestPossibleLineupAlgorithm(),
            new BalancedDefenseAlgorithm(),
            new CowScoreMaximizerAlgorithm(),
            new ConsensusAlgorithm()
    );

    private LineupAlgorithms() {
        // Utility class, no instantiation
    }
}
