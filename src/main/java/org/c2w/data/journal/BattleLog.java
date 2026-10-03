package org.c2w.data.journal;

import java.util.List;

/**
 * A parsed battle log: head data plus all rows with points in file order
 * (which is the chronological order - exports are append-only).
 *
 * @param header  head data (file name + language)
 * @param entries single fights and fortification events in file order
 */
public record BattleLog(BattleLogHeader header, List<BattleLogEntry> entries) {
    public BattleLog {
        if (header == null) {
            throw new IllegalArgumentException("BattleLog needs a header");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** Sum of the points of all entries - the own points in an attack log, the opponent's in a defense log. */
    public int totalPoints() {
        return entries.stream().mapToInt(BattleLogEntry::points).sum();
    }

    /** The single fights in file order. */
    public List<Fight> fights() {
        return entries.stream().filter(Fight.class::isInstance).map(Fight.class::cast).toList();
    }

    /** The fortification events (undefended, captured) in file order. */
    public List<FortEvent> fortEvents() {
        return entries.stream().filter(FortEvent.class::isInstance).map(FortEvent.class::cast).toList();
    }
}
