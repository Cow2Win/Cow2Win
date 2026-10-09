package org.c2w.data.journal;

/**
 * One row of a battle log that carries points: a single {@link Fight} or a
 * {@link FortEvent} (undefended positions, captured fortification). Entries
 * keep the file order, which is the chronological order.
 */
public sealed interface BattleLogEntry permits Fight, FortEvent {

    /** Catalog id of the fortification, {@code null} if the name is unknown. */
    String fortificationId();

    /** Fortification name exactly as in the log (without the position). */
    String fortificationName();

    /** Points of this row (column "Punkte"). */
    int points();

    /** 1-based line of this row in the file. */
    int lineNumber();
}
