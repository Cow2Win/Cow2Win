package org.c2w.gui.stage;

import org.c2w.gui.MainMenuBar;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * The common layout of the stage views in the center of the main window (one per process
 * stage, switched by the process bar): on the left the {@linkplain StageActionList action list}
 * of the stage, in the middle the work area, on the right an info panel with details on what
 * is selected - read only. Rule of thumb: actions to the left, information to the right.
 *
 * <p>A subclass provides {@link #stage()}, {@link #createWorkArea()} and
 * {@link #createInfoPanel()} and calls {@link #build()} at the end of its constructor, once
 * its own fields are set. Transparent, so the background image shows through; the two side
 * columns are set off like the context bar (translucent dark fill, thin line).
 */
public abstract class StageView extends JPanel {

    /** Fixed width of the action list on the left. */
    static final int ACTION_LIST_WIDTH = 220;

    /** Fixed width of the info panel on the right. */
    static final int INFO_PANEL_WIDTH = 300;

    /** Translucent dark fill of the side columns - the same darkening as the context bar. */
    private static final Color COLUMN_FILL = new Color(0, 0, 0, 70);

    /** Line between a side column and the work area - the same as under the context bar. */
    private static final Color LINE_COLOR = new Color(255, 255, 255, 40);

    private final MainActions actions;
    private StageActionList actionList;

    protected StageView(MainActions actions) {
        super(new BorderLayout());
        if (actions == null) {
            throw new IllegalArgumentException("StageView needs the main actions");
        }
        this.actions = actions;
        setOpaque(false);
    }

    /** The process stage this view belongs to. */
    public abstract Stage stage();

    /** The panel in the middle, where the actual work happens. */
    protected abstract JComponent createWorkArea();

    /** The read-only details on the right - scrolls vertically if it does not fit. */
    protected abstract JComponent createInfoPanel();

    /** Hook to append stage specific rows to the action list (see {@link StageActionList#addExtra}). */
    protected void addActionListExtras(StageActionList actionList) {
    }

    /** Lays out action list, work area and info panel. Called once by the subclass constructor. */
    protected final void build() {
        actionList = new StageActionList(MainMenuBar.menuFor(stage()), actions);
        addActionListExtras(actionList);
        JPanel actionColumn = new SideColumn(ACTION_LIST_WIDTH, new MatteBorder(0, 0, 0, 1, LINE_COLOR));
        actionColumn.add(actionList, BorderLayout.NORTH);
        add(actionColumn, BorderLayout.WEST);

        add(createWorkArea(), BorderLayout.CENTER);

        JPanel infoContent = new WidthTrackingPanel();
        infoContent.add(createInfoPanel(), BorderLayout.NORTH);
        JScrollPane infoScrollPane = new JScrollPane(infoContent,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        infoScrollPane.setOpaque(false);
        infoScrollPane.getViewport().setOpaque(false);
        infoScrollPane.setBorder(BorderFactory.createEmptyBorder());
        infoScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        JPanel infoColumn = new SideColumn(INFO_PANEL_WIDTH, new MatteBorder(0, 1, 0, 0, LINE_COLOR));
        infoColumn.add(infoScrollPane, BorderLayout.CENTER);
        add(infoColumn, BorderLayout.EAST);
    }

    /** The action list on the left - null before {@link #build()}. */
    public StageActionList actionList() {
        return actionList;
    }

    /** Makes {@code component} and every panel and scroll pane in it transparent, so the column fill shows through. */
    protected static void makeTransparent(Component component) {
        if (component instanceof JPanel || component instanceof JScrollPane || component instanceof JViewport) {
            ((JComponent) component).setOpaque(false);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                makeTransparent(child);
            }
        }
    }

    /** A side column of fixed width with a translucent dark fill. */
    private static final class SideColumn extends JPanel {

        private final int width;

        SideColumn(int width, MatteBorder line) {
            super(new BorderLayout());
            this.width = width;
            setOpaque(false);
            setBorder(line);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, super.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(COLUMN_FILL);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /** Content of the info panel's scroll pane: as wide as the viewport, scrolls vertically only. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
