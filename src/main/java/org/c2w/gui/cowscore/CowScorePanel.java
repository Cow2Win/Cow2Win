package org.c2w.gui.cowscore;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.io.IOException;
import java.util.List;

/**
 * One tab of {@link CowScoreDialog}: edits the {@code FortMarks} of one catalog (heroes,
 * titans, pets or war flags) - or the hero combos - in a working copy until {@link #save()}.
 * Every tab has the same layout: a list on the left (equally wide in all tabs, see
 * {@link #setListWidth}) and the detail area of the selected entry on the right.
 */
public interface CowScorePanel {

    /** True once the user changed something or restored the defaults, until the next successful {@link #save()}. */
    boolean hasUnsavedChanges();

    /** Writes the working copy through the repository; afterwards nothing is unsaved. On failure it stays unsaved. */
    void save() throws IOException;

    /** Resets the working copy to the shipped defaults (not saved yet) - without asking, see {@link #restoreDefaultsConfirmKey()}. */
    void restoreDefaults();

    /** Notified whenever {@link #hasUnsavedChanges()} may have changed. */
    void addChangeListener(ChangeListener listener);

    /** Language file key of the question the dialog asks before {@link #restoreDefaults()}. */
    String restoreDefaultsConfirmKey();

    /** The panel's Swing component, shown as the tab's content. */
    JComponent component();

    /** The texts of the list on the left in the current language - to size the list for all tabs alike. */
    List<String> listLabels();

    /** Sets the width of the list on the left (the same for every tab, see {@link CowScoreLayout#listWidth}). */
    void setListWidth(int width);

    /** The scroll pane around the detail area on the right. */
    JScrollPane detailScrollPane();
}
