package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class GuiUtils {
    public static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.GERMANY);

    public static final Color VALUE_HIGHLIGHT_BACKGROUND = IconLoader.GREEN;

    /**
     * Custom UIManager key - not a built-in Swing/L&amp;F key, namespaced with
     * "Cow2Win." so it can never collide with one - for the background color
     * of the hero/titan team table cells in {@code HeroValueOverviewDialog}/
     * {@code TitanValueOverviewDialog} (see their cell renderers). Registered once below in {@link #setGUIConstants()},
     * the same way "TabbedPane.selected" already is, instead of hardcoding the
     * color into each of those renderers.
     */
    public static final String KEY_TEAM_TABLE_CELL_BACKGROUND = "Cow2Win.teamTableCellBackground";

    public static final void setGUIConstants(){
        //UIManager.put("TabbedPane.selected", Color.GRAY.darker());
        //UIManager.put(KEY_TEAM_TABLE_CELL_BACKGROUND, Color.GRAY.darker());
        //UIManager.put("TabbedPane.focus", Color.BLACK);
        //UIManager.put("TabbedPane.selectHighlight", Color.GRAY);
        //UIManager.put("Table.background",Color.GRAY.darker());
       // UIManager.put("Table.selectionBackground",Color.BLACK);
        //UIManager.put("TextArea.background",Color.GRAY.darker());
        //UIManager.put("TextArea.foreground",Color.WHITE);
        //UIManager.put("List.background",Color.GRAY.darker());
        //UIManager.put("List.foreground",Color.WHITE);
        //UIManager.put("List.selectionBackground",Color.BLACK);
        //UIManager.put("List.selectionForeground",Color.WHITE);

    }

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
     * Widens {@code window} (never narrows it) after {@code content} - a
     * component inside it without its own scroll pane - was replaced, so that
     * {@code content}'s preferred width fits. Never wider than the usable
     * screen area.
     */
    public static void widenToFit(Window window, Component content) {
        int needed = window.getWidth() - content.getWidth() + content.getPreferredSize().width;
        if (needed > window.getWidth()) {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            window.setSize(Math.min(needed, screen.width), window.getHeight());
        }
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
