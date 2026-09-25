package org.c2w.eval;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

import java.util.Objects;

/**
 * "No algorithm" for one side: {@link #run} returns the lineup unchanged.
 * Offered in every algorithm combo box (see {@link LineupAlgorithms#HERO}/
 * {@link LineupAlgorithms#TITAN}) so one side can be filled automatically
 * while the other is left entirely to manual picks - e.g. heroes by
 * {@link BestPossibleLineupAlgorithm}, titans by hand. Also leaves {@link
 * Lineup#algorithmName()} untouched, since nothing was assigned.
 */
public class ManualLineupAlgorithm implements LineupAlgorithm {

    /** Display name of the manual option - also the {@link AlgorithmDescriptions} lookup key. */
    public static final String DISPLAY_NAME = "Manual";

    private final Lineup.TeamType teamType;

    public ManualLineupAlgorithm(Lineup.TeamType teamType) {
        this.teamType = Objects.requireNonNull(teamType, "teamType");
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Lineup.TeamType teamType() {
        return teamType;
    }

    @Override
    public Lineup run(Lineup lineup, Guild guild) {
        return lineup;
    }
}
