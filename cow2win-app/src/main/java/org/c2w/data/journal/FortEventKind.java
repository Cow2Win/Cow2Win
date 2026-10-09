package org.c2w.data.journal;

/** Kind of a fortification row without a single fight. */
public enum FortEventKind {
    /** Positions that were not defended and got captured without a fight. */
    UNDEFENDED,
    /** The fortification was captured (capture bonus). */
    CAPTURED
}
