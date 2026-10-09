package org.c2w.data.journal;

/**
 * A fortification row without a single fight.
 *
 * @param fortificationId   catalog id, {@code null} if the name is unknown
 * @param fortificationName raw name from the log
 * @param kind              undefended positions or captured fortification
 * @param freePositions     {@link FortEventKind#UNDEFENDED} only: positions captured without a fight, else {@code null}
 * @param totalPositions    {@link FortEventKind#UNDEFENDED} only: positions of the fortification, else {@code null}
 * @param text              raw text of the result column
 * @param points            points of the row
 * @param lineNumber        1-based line in the file
 */
public record FortEvent(
        String fortificationId,
        String fortificationName,
        FortEventKind kind,
        Integer freePositions,
        Integer totalPositions,
        String text,
        int points,
        int lineNumber
) implements BattleLogEntry {
    public FortEvent {
        if (kind == null) {
            throw new IllegalArgumentException("FortEvent needs a kind");
        }
        fortificationName = fortificationName == null ? "" : fortificationName;
        text = text == null ? "" : text;
    }
}
