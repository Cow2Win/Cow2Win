package org.c2w.data.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persisted result of ONE assignment run (see
 * org.tdi.cow2.eval.LineupAlgorithm, added 2026-09-03): which team was
 * assigned to which fortification. Purely data-holding like {@link Guild} -
 * references teams/fortifications only via ids (see Entry), no object
 * references, so that this file stays independent of a particular Guild
 * instance in memory and can later be read back in by other modules (see
 * org.tdi.cow2.repository.LineupRepository).
 *
 * A team is identified via teamMemberId + teamType + teamIndex - per the
 * user's requirements (see {@link GuildMember}), teams have no name of their
 * own; their position in the member's respective list (heroTeams/titanTeams)
 * is enough (see {@link GuildMember#teamLabel(int)}).
 *
 * algorithmName is the displayName() of the algorithm that produced this
 * result (see org.tdi.cow2.eval.LineupAlgorithm#displayName()) - additionally
 * feeds into the file name on save (see org.tdi.cow2.data.LineupRepository).
 * Empty ("") means no algorithm has (yet) produced this lineup - e.g. the
 * default lineup created together with a new guild (see
 * org.tdi.cow2.Cow2App#createInitialLineupFile) or one built purely from
 * manual picks in org.tdi.cow2.gui.TeamsOverviewPanel's "Fortification"
 * column, never run through an algorithm. Since an algorithm (see
 * org.tdi.cow2.eval.LineupAlgorithm#run) only ADDS entries for teams that
 * had none yet, algorithmName really means "produced/last extended by",
 * not "every entry in here came from this one algorithm" - a lineup can mix
 * manual picks and algorithm-added entries; this field just names whichever
 * algorithm ran most recently. createdAt is the time this was created
 * (informational).
 */
public record Lineup(
        String guildId,
        String guildName,
        String algorithmName,
        LocalDateTime createdAt,
        List<Entry> entries
) {
    public Lineup {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("Lineup needs a guildId");
        }
        algorithmName = algorithmName == null ? "" : algorithmName;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** Team kind of an assignment - determines whether teamIndex refers to the member's heroTeams or titanTeams. */
    public enum TeamType {
        HERO, TITAN
    }

    /**
     * A single team-to-fortification assignment (see
     * org.tdi.cow2.eval.LineupAssigner.Assignment, but referenced here only
     * via ids/index instead of object references - see the class comment).
     * totalPower/buffFitScore/weightedScore are the values computed at the
     * time of assignment (see org.tdi.cow2.eval.FortificationFitScore/
     * org.tdi.cow2.eval.LineupAssigner.Assignment#weightedScore()), carried
     * over unchanged - purely informational, no recomputation needed when
     * reading this back in.
     */
    public record Entry(
            String fortificationId,
            String teamMemberId,
            TeamType teamType,
            int teamIndex,
            int totalPower,
            int buffFitScore,
            double weightedScore
    ) {
    }
}
