package org.c2w.gui.stage;

import org.c2w.gui.MainMenuBar;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The left column of a {@link StageView}: the stage's actions as a list, built from the
 * stage's menu in {@link MainMenuBar#MENUS} - the same actions as the menu, no second list to
 * maintain. An action becomes a clickable row (icon and text), a submenu a group (small
 * heading, its entries indented), a separator a small gap with a thin line. Rows follow their
 * action (enabled state, text, icon). A stage view can append rows of its own, see
 * {@link #addExtra}.
 */
public class StageActionList extends JPanel {

    /** Left padding of a row, plus this much per group level. */
    private static final int INDENT = 14;

    private static final Color HEADING_COLOR = new Color(255, 255, 255, 120);
    private static final Color LINE_COLOR = new Color(255, 255, 255, 40);
    private static final Color HOVER_FILL = new Color(255, 255, 255, 30);

    private final List<ActionRow> rows = new ArrayList<>();

    public StageActionList(MainMenuBar.MenuSpec menu, MainActions actions) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        addEntries(menu.entries(), actions, 0);
    }

    private void addEntries(List<MainMenuBar.Entry> entries, MainActions actions, int level) {
        for (MainMenuBar.Entry entry : entries) {
            switch (entry) {
                case MainMenuBar.Item item -> {
                    ActionRow row = new ActionRow(actions.get(item.id()), level);
                    rows.add(row);
                    addRow(row);
                }
                case MainMenuBar.Separator separator -> addRow(new SeparatorRow());
                case MainMenuBar.MenuSpec submenu -> {
                    addRow(new GroupHeading(LanguageService.displayName(submenu.titleKey()), level));
                    addEntries(submenu.entries(), actions, level + 1);
                }
            }
        }
    }

    /** Appends a stage specific row (e.g. a toggle) below the actions. */
    public void addExtra(JComponent component) {
        addRow(new SeparatorRow());
        component.setBorder(BorderFactory.createEmptyBorder(4, INDENT - 4, 4, 8));
        addRow(component);
    }

    private void addRow(JComponent row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(row);
    }

    /** The action rows, in display order. */
    List<ActionRow> rows() {
        return List.copyOf(rows);
    }

    /** A row for one action: icon and text, hand cursor and hover highlight while enabled, a click performs the action. */
    static final class ActionRow extends JLabel {

        private final Action action;
        private boolean hover;

        ActionRow(Action action, int level) {
            this.action = action;
            setOpaque(false);
            setIconTextGap(8);
            setBorder(BorderFactory.createEmptyBorder(5, INDENT + level * INDENT, 5, 8));
            update();
            // Follows the action: e.g. the team overview's text and icon change with the fortification type.
            PropertyChangeListener listener = e -> update();
            action.addPropertyChangeListener(listener);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    perform();
                }
            });
        }

        Action action() {
            return action;
        }

        /** Performs the action - nothing while it is disabled. */
        void perform() {
            if (action.isEnabled()) {
                action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                        (String) action.getValue(Action.ACTION_COMMAND_KEY)));
            }
        }

        private void update() {
            setText((String) action.getValue(Action.NAME));
            // The column is narrow - the tooltip shows the full text of a cut row.
            setToolTipText((String) action.getValue(Action.SHORT_DESCRIPTION));
            setIcon((Icon) action.getValue(Action.SMALL_ICON));
            setEnabled(action.isEnabled());
            setCursor(action.isEnabled() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hover && isEnabled()) {
                g.setColor(HOVER_FILL);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }
    }

    /** The small, muted heading of a group (a submenu). */
    static final class GroupHeading extends JLabel {

        GroupHeading(String title, int level) {
            super(title.toUpperCase(JournalTexts.locale()));
            setForeground(HEADING_COLOR);
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2f));
            setBorder(BorderFactory.createEmptyBorder(8, INDENT + level * INDENT, 2, 8));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** A small gap with a thin line. */
    static final class SeparatorRow extends JComponent {

        private static final int HEIGHT = 11;

        SeparatorRow() {
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(LINE_COLOR);
            g.drawLine(INDENT, HEIGHT / 2, getWidth() - INDENT, HEIGHT / 2);
        }
    }
}
