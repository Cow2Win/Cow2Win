package org.c2w.gui;

import javax.swing.*;
import java.awt.Dimension;
import java.io.File;

/**
 * The one place where Cow2Win (app and DataTool) creates its file choosers: every chooser is
 * {@link #SIZE_FACTOR} times as wide and high as the default {@link JFileChooser} - room for
 * long paths and many files. Every new file chooser in Cow2Win should be created here, so
 * changing the factor changes all of them together.
 */
public final class FileChoosers {

    /** Every file chooser is this much larger (width and height) than the default size. */
    public static final double SIZE_FACTOR = 1.5;

    private FileChoosers() {
    }

    /**
     * A chooser of the uniform size, opening in {@code startDirectory} ({@code null}: the
     * default directory of {@link JFileChooser}).
     */
    public static JFileChooser create(File startDirectory) {
        JFileChooser chooser = new JFileChooser(startDirectory);
        enlarge(chooser);
        return chooser;
    }

    /** Sets the preferred size of {@code chooser} to {@link #SIZE_FACTOR} times its current one (rounded). */
    public static void enlarge(JFileChooser chooser) {
        Dimension size = chooser.getPreferredSize();
        chooser.setPreferredSize(new Dimension((int) Math.round(size.width * SIZE_FACTOR),
                (int) Math.round(size.height * SIZE_FACTOR)));
    }
}
