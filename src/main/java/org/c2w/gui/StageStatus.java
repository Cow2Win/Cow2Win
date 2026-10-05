package org.c2w.gui;

import org.c2w.gui.common.IconLoader;

import java.awt.*;

/**
 * The traffic light of a process stage tile in {@link ProcessBar} (see
 * {@code ProcessBar.StageTile#setStatus}). Not set from real data yet - every tile stays
 * {@link #NONE} until the data status of the stages is evaluated (GUI rework M4).
 */
public enum StageStatus {

    /** No light at all (default). */
    NONE,
    /** Everything is up to date - green. */
    OK,
    /** Something should be looked at - orange. */
    ATTENTION,
    /** Something has to be done - red. */
    ACTION_NEEDED;

    /** The light's color, or {@code null} for {@link #NONE} (no light). */
    public Color color() {
        return switch (this) {
            case NONE -> null;
            case OK -> IconLoader.GREEN;
            case ATTENTION -> ContextBar.UNSAVED_COLOR;
            case ACTION_NEEDED -> IconLoader.RED;
        };
    }
}
