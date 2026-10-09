package org.c2w.data.journal;

/** Kind of one unit row of a team in a battle log. */
public enum UnitKind {
    HERO,
    /** The pet of a hero team (usually the 6th row). */
    PET,
    TITAN,
    /** A titan team's totem - its row has no power value. */
    TOTEM
}
