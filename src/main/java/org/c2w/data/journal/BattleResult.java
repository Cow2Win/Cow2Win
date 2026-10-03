package org.c2w.data.journal;

/**
 * Result of a Clash of Worlds battle from the point of view of the exporting
 * (own) guild, derived from the ranking points in the log's file name - see
 * {@link #fromRankingPoints}.
 */
public enum BattleResult {
    WIN,
    LOSS,
    /** A real draw: both guilds get 375 ranking points. */
    DRAW,
    /** Exported while the battle was still running (0 ranking points) - only a partial state. */
    RUNNING;

    /** Ranking points of a real draw. */
    public static final int DRAW_RANKING_POINTS = 375;

    /** Minimum ranking points of a win (750 plus the point difference). */
    public static final int WIN_BASE_RANKING_POINTS = 750;

    /**
     * {@code 0} = {@link #RUNNING}, {@code 375} = {@link #DRAW},
     * {@code >= 750} = {@link #WIN}, {@code < 0} = {@link #LOSS};
     * {@code null} for any other value (not a possible result).
     */
    public static BattleResult fromRankingPoints(int rankingPoints) {
        if (rankingPoints == 0) {
            return RUNNING;
        }
        if (rankingPoints == DRAW_RANKING_POINTS) {
            return DRAW;
        }
        if (rankingPoints >= WIN_BASE_RANKING_POINTS) {
            return WIN;
        }
        if (rankingPoints < 0) {
            return LOSS;
        }
        return null;
    }
}
