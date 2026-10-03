package org.c2w.data.journal;

import java.util.List;

/**
 * One side (attacker or defender) of a single fight: {@code Name (Level-TeamPower)}
 * plus - if the log contains them - the team's units.
 *
 * @param playerName raw player name exactly as in the log, never trimmed (names
 *                   may end with or contain doubled spaces)
 * @param level      player level
 * @param teamPower  power of the team
 * @param buff       defender only: fortification buff, {@code null} if none
 * @param units      the team's units in log order; empty if the log has none for this
 *                   fight (usual in defense logs, but possible in both directions)
 */
public record FightSide(String playerName, int level, long teamPower, DefenseBuff buff, List<FightUnit> units) {
    public FightSide {
        playerName = playerName == null ? "" : playerName;
        units = units == null ? List.of() : List.copyOf(units);
    }
}
