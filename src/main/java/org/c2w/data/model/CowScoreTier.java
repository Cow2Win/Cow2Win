package org.c2w.data.model;

/**
 * The shared 5-step rating grid (0.4-0.9) used to score how good a fit a
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
 *    independent of any specific fortification/buff - {@link #GOOD} is
 *    the default for heroes without an explicit assessment.
 *  - a later, per-buff hero/titan fit score on the fortification catalog,
 *    replacing the earlier, removed {@code buffProfits} list.
 *
 * The tier names are deliberately generic (not e.g. "ROLE_MATCH") because
 * the same 5 values are reused for both uses above with a different
 * meaning per context - see the using field's own javadoc for what a given
 * tier means there.
 *
 * Pets and war flags use the same 5 tiers, but each tier is worth less for
 * them than for a hero - see {@link #petWarFlagValue()} (per the user's
 * decision, 2026-09-29: they are weaker than heroes and get values of their
 * own, GOOD = 0.3).
 */
// Since 2026-09-30 only used by titans - heroes, pets and war flags use FortMark/FortMarks.
public enum CowScoreTier {
    // PLACEHOLDER pet/war flag values: only GOOD = 0.3 is the user's own
    // decision (2026-09-29), the other four are provisional - same order,
    // roughly half the hero spacing - until the user settles on real ones.
    NEGATIVE(0.4, 0.1),
    AVERAGE(0.6, 0.2),
    MODERATE(0.7, 0.25),
    GOOD(0.8, 0.3),
    GREAT(0.9, 0.35);

    private final double value;
    private final double petWarFlagValue;

    CowScoreTier(double value, double petWarFlagValue) {
        this.value = value;
        this.petWarFlagValue = petWarFlagValue;
    }

    /**
     * This tier's numeric value for a {@link Pet} or {@link WarFlag} - lower
     * than {@link #value()} for the same tier, since pets and war flags
     * contribute less to a team than a hero does (see {@link
     * HeroTeam#petWarFlagScores(Fortification)}).
     */
    public double petWarFlagValue() {
        return petWarFlagValue;
    }

    /** This tier's numeric value (0.4-0.9), e.g. for summing scores across a team. */
    public double value() {
        return value;
    }
}
