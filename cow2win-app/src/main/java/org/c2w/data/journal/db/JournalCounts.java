package org.c2w.data.journal.db;

/**
 * How much of the journal an action affects - for confirmations such as
 * "battle of 24.09.2026 against Das Schwarze Auge - 2 logs, 124 single fights".
 *
 * @param battles number of battles
 * @param logs    number of stored logs (attack and defense) of these battles
 * @param fights  number of single fights in these logs
 */
public record JournalCounts(int battles, int logs, int fights) {

    /** Nothing affected. */
    public static final JournalCounts NONE = new JournalCounts(0, 0, 0);
}
