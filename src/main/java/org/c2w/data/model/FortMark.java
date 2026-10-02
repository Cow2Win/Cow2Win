package org.c2w.data.model;

/**
 * A manually curated mark on the relation between one hero (or titan, pet,
 * war flag) and one fortification - the successor of the former tier-based
 * {@code generalScore}/{@code buffFitScores} (see {@code
 * cowscore-formel-konzept.md}), now the only assessment model.
 *
 * <p>There is deliberately no third "neutral" constant: an unmarked
 * (hero, fortification) pair simply has no entry in {@link FortMarks}.
 *
 * <ul>
 *     <li>{@link #POSITIVE} - the hero/titan performs better than usual at
 *     this fortification (heroes/titans), or the pet/war flag is a good fit
 *     for it (pets/war flags - the only mark they can carry).</li>
 *     <li>{@link #NEGATIVE} - the hero/titan performs worse than usual at
 *     this fortification. Heroes and titans only.</li>
 * </ul>
 */
public enum FortMark {
    POSITIVE,
    NEGATIVE
}
