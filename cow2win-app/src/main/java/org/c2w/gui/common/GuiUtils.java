package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class GuiUtils {
    public static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.GERMANY);

    public static final Color VALUE_HIGHLIGHT_BACKGROUND = IconLoader.GREEN;

    /**
     * Sizes {@code window} to {@code height} and to at least {@code minWidth},
     * but wide enough that its content's preferred width fits - plus room for
     * a vertical scroll bar, since the entry dialogs keep their rows in a
     * JScrollPane whose vertical bar only appears once the rows outgrow
     * {@code height} and would otherwise force a horizontal scroll bar. Never
     * larger than the usable screen area. Call once all content is added.
     */
    public static void sizeToContent(Window window, int minWidth, int height) {
        window.pack(); // makes the window displayable, so getWidth() includes the frame insets
        int scrollBarWidth = new JScrollBar(JScrollBar.VERTICAL).getPreferredSize().width;
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.max(minWidth, window.getWidth() + scrollBarWidth);
        window.setSize(Math.min(width, screen.width), Math.min(height, screen.height));
    }

    /**
     * Runs {@code task} on the Swing event thread and waits for it - directly if this
     * already is the event thread. Used as {@code AppContext}'s event dispatcher, so
     * listener notifications from background tasks reach Swing components safely.
     */
    public static void runOnEdtAndWait(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }
}
