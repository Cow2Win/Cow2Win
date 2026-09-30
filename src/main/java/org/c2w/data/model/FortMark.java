package org.c2w.data.model;

/**
 * A manually curated mark on the relation between one hero (or pet/war flag)
 * and one fortification - the successor of the {@link CowScoreTier}-based
 * {@code generalScore}/{@code buffFitScores} for heroes, pets and war flags
 * (CowScore concept of 2026-09-30, see {@code cowscore-formel-konzept.md}).
 * Titans still use {@link CowScore}/{@link CowScoreTier} for now.
 *
 * <p>There is deliberately no third "neutral" constant: an unmarked
 * (hero, fortification) pair simply has no entry in {@link FortMarks}.
 *
 * <ul>
 *     <li>{@link #POSITIVE} - the hero performs better than usual at this
 *     fortification (heroes), or the pet/war flag is a good fit for it
 *     (pets/war flags - the only mark they can carry).</li>
 *     <li>{@link #NEGATIVE} - the hero performs worse than usual at this
 *     fortification. Heroes only.</li>
 * </ul>
 */
public enum FortMark {
    POSITIVE,
    NEGATIVE
}
