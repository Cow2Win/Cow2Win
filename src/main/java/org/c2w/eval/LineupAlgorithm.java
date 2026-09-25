package org.c2w.eval;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

/**
 * One strategy for assigning teams to fortifications - for exactly ONE side
 * of the map (see {@link #teamType()}): a hero algorithm only ever places
 * hero teams on HERO fortifications, a titan algorithm only titan teams on
 * TITAN fortifications. The other side's entries are passed through
 * untouched, so the hero part and the titan part of a lineup can be handled
 * by different strategies, or one of them by hand (see {@link
 * ManualLineupAlgorithm}). {@link LineupAlgorithms#HERO}/{@link
 * LineupAlgorithms#TITAN} list the available instances per side.
 */
public interface LineupAlgorithm {

    /** Strategy name shown in combo boxes and stored in {@link Lineup#algorithmName()} - the same for the hero and the titan variant of a strategy. */
    String displayName();

    /** The side this algorithm assigns teams for. */
    Lineup.TeamType teamType();

    /**
     * Returns a copy of {@code lineup} with entries ADDED for still-unassigned
     * teams of {@link #teamType()} - never removes or changes an existing
     * entry, and never adds an entry for the other side.
     */
    Lineup run(Lineup lineup, Guild guild);
}
