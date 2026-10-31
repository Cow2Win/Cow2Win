package org.c2w.gui.manual;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

/**
 * F1 opens the manual ({@link ManualWindow}) at the chapter of a window. Not for a window that
 * already uses the F keys - e.g. the team entry dialog, where F1 - F5 load team templates.
 */
public final class ManualHelp {

    /** The key that opens the manual. */
    public static final KeyStroke KEY = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);

    private ManualHelp() {
    }

    /** F1 anywhere in the window of {@code rootPane} opens the manual at {@code chapterId}. */
    public static void bindF1(JRootPane rootPane, String chapterId) {
        bindF1(rootPane, () -> chapterId);
    }

    /** F1 anywhere in the window of {@code rootPane} opens the manual at the chapter {@code chapter} names then. */
    public static void bindF1(JRootPane rootPane, Supplier<String> chapter) {
        rootPane.registerKeyboardAction(e -> ManualWindow.open(SwingUtilities.getWindowAncestor(rootPane), chapter.get()),
                KEY, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
