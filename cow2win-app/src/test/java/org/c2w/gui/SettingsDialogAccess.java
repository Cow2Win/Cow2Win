package org.c2w.gui;

import java.awt.Dimension;

/** Test access to package-private parts of {@link SettingsDialog} from other packages. */
public final class SettingsDialogAccess {

    private SettingsDialogAccess() {
    }

    /** Preferred size of the settings' directory chooser (backup / workspace path). */
    public static Dimension directoryChooserSize() {
        return SettingsDialog.createDirectoryChooser("title").getPreferredSize();
    }
}
