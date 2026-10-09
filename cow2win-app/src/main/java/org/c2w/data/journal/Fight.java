package org.c2w.data.journal;

/**
 * A single fight: {@code <Fort> (Position: n),<result>,<points>,<attacker>,...,<defender>,<buff>}.
 *
 * @param fortificationId   catalog id, {@code null} if the name is unknown
 * @param fortificationName raw name from the log (without the position)
 * @param position          position in the fortification
 * @param teamKind          hero or titan fight; {@code null} if the log has no units for this fight
 * @param attackerWins      the result column - always from the attacker's point of view,
 *                          also in a defense log
 * @param resultText        raw text of the result column
 * @param points            points of the row (partial points on a defeat)
 * @param attacker          attacking side
 * @param defender          defending side (carries the fortification buff)
 * @param lineNumber        1-based line in the file
 */
public record Fight(
        String fortificationId,
        String fortificationName,
        int position,
        TeamKind teamKind,
        boolean attackerWins,
        String resultText,
        int points,
        FightSide attacker,
        FightSide defender,
        int lineNumber
) implements BattleLogEntry {
    public Fight {
        if (attacker == null || defender == null) {
            throw new IllegalArgumentException("Fight needs both sides");
        }
        fortificationName = fortificationName == null ? "" : fortificationName;
        resultText = resultText == null ? "" : resultText;
    }

    /** True if the log contains the teams' units for this fight. */
    public boolean hasUnits() {
        return !attacker.units().isEmpty() || !defender.units().isEmpty();
    }
}
