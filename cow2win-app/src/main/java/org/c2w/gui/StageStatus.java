package org.c2w.gui;

import org.c2w.gui.common.AppColors;

import java.awt.*;

/**
 * The traffic light of a process stage tile in {@link ProcessBar} (see
 * {@code ProcessBar.StageTile#setStatus}), set from the data status by {@link DataStatusController}.
 */
public enum StageStatus {

    /** No light at all (default). */
    NONE,
    /** Everything is up to date - {@link AppColors#SUCCESS}. */
    OK,
    /** Something should be looked at - {@link AppColors#WARNING}. */
    ATTENTION,
    /** Something has to be done - {@link AppColors#ERROR}. */
    ACTION_NEEDED;

    /** The light's color, or {@code null} for {@link #NONE} (no light). */
    public Color color() {
        return switch (this) {
            case NONE -> null;
            case OK -> AppColors.SUCCESS;
            case ATTENTION -> AppColors.WARNING;
            case ACTION_NEEDED -> AppColors.ERROR;
        };
    }
}
