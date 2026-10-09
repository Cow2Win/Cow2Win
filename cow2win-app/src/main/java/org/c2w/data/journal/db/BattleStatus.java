package org.c2w.data.journal.db;

/** Whether a stored battle is still running (only partial exports so far) or finished. */
public enum BattleStatus {
    /** Only exports with 0 ranking points so far - head data and points are preliminary. */
    RUNNING,
    /** At least one export with a real result. */
    FINISHED
}
