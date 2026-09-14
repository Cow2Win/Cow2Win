package org.c2w.data.model;

/**
 * The shared 5-step rating grid (0.5-1.1) used to score how good a fit a
 * hero (or, later, titan) is for something - deliberately a small, closed
 * set of values rather than a free {@code double}, so ratings stay
 * comparable across the whole catalog and rebalancing later means changing
 * the numbers here once, not touching every JSON entry that references a
 * tier. Persisted in JSON by tier NAME (not by number), for the same
 * reason: a raw number in the JSON would invite values outside this grid,
 * defeating the point of having a grid at all.
 *
 * Two intended uses (introduced incrementally - see
 * cow2win-verbesserungsvorschlaege.md):
 *  - {@link Hero#generalScore()} (added first): a hero's general quality,
 *    independent of any specific fortification/buff - {@link #STANDARD} is
 *    the default for heroes without an explicit assessment.
 *  - a later, per-buff hero/titan fit score on the fortification catalog,
 *    replacing the earlier, removed {@code buffProfits} list.
 *
 * The tier names are deliberately generic (not e.g. "ROLE_MATCH") because
 * the same 5 values are reused for both uses above with a different
 * meaning per context - see the using field's own javadoc for what a given
 * tier means there.
 */
public enum ScoreTier {
    NEGATIVE(0.4),
    NORMAL(0.6),
    MODERATE(0.7),
    STANDARD(0.8),
    ELEVATED(0.9);

    private final double value;

    ScoreTier(double value) {
        this.value = value;
    }

    /** This tier's numeric value (0.5-1.1), e.g. for summing scores across a team. */
    public double value() {
        return value;
    }
}
