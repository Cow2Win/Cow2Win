package org.c2w.gui.cowscore;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.io.IOException;

/**
 * One tab of {@link CowScoreDialog}: edits the {@code FortMarks} of one catalog (heroes,
 * titans, pets or war flags) in a working copy until {@link #save()}.
 */
public interface CowScorePanel {

    /** True once the user changed a mark or restored the defaults, until the next successful {@link #save()}. */
    boolean hasUnsavedChanges();

    /** Writes the working copy through the catalog's repository; afterwards nothing is unsaved. On failure it stays unsaved. */
    void save() throws IOException;

    /** Resets the working copy of every entry to the shipped defaults (not saved yet) - without asking, see {@link #restoreDefaultsConfirmKey()}. */
    void restoreDefaults();

    /** Notified whenever {@link #hasUnsavedChanges()} may have changed. */
    void addChangeListener(ChangeListener listener);

    /** Language file key of the question the dialog asks before {@link #restoreDefaults()}. */
    String restoreDefaultsConfirmKey();

    /** The panel's Swing component, shown as the tab's content. */
    JComponent component();
}
