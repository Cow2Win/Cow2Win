package org.c2w.eval;

/**
 * An adjustable value an algorithm depends on - see {@link LineupAlgorithm#usedSettings()}. Shown
 * read-only in the algorithm's explanation (strategic concept, "i" next to the algorithm), changed
 * in the settings.
 */
public enum AlgorithmSetting {
    /** {@link org.c2w.domain.HodorSettings#reserve()}. */
    RESERVE,
    /** {@link org.c2w.domain.HodorSettings#weights()}. */
    BUFF_WEIGHTS,
    /** The CowScore bonuses ({@link org.c2w.domain.CowScoreBonuses}). */
    COW_SCORE_BONUSES
}
