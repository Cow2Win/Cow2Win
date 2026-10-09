package org.c2w.datatool.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Small selection dialog for an id list: valid ids on the left, the selection on the right.
 * Newly added ids go to the end, so the existing order is kept; for orderable lists (combos,
 * templates) the selection can also be moved up and down.
 */
final class IdListDialog extends JDialog {

    private final DefaultListModel<String> available = new DefaultListModel<>();
    private final DefaultListModel<String> selected = new DefaultListModel<>();
    private final JList<String> availableList = new JList<>(available);
    private final JList<String> selectedList = new JList<>(selected);
    private List<String> result;

    private IdListDialog(Component parent, String title, List<String> options, List<String> current,
                         Function<String, String> label, boolean orderable, String countHint) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        current.forEach(selected::addElement);
        options.stream().filter(id -> !current.contains(id)).forEach(available::addElement);
        javax.swing.ListCellRenderer<Object> renderer = new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                String id = value.toString();
                String name = label.apply(id);
                return super.getListCellRendererComponent(list, name.equals(id) ? id : name + "  (" + id + ")",
                        index, isSelected, cellHasFocus);
            }
        };
        availableList.setCellRenderer(renderer);
        selectedList.setCellRenderer(renderer);
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        availableList.addMouseListener(doubleClick(this::add));
        selectedList.addMouseListener(doubleClick(this::remove));

        JPanel moves = new JPanel();
        moves.setLayout(new BoxLayout(moves, BoxLayout.Y_AXIS));
        moves.add(Box.createVerticalGlue());
        moves.add(button("Add >", this::add));
        moves.add(button("< Remove", this::remove));
        if (orderable) {
            moves.add(Box.createVerticalStrut(12));
            moves.add(button("Up", () -> move(-1)));
            moves.add(button("Down", () -> move(1)));
        }
        moves.add(Box.createVerticalGlue());

        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        lists.add(titled("Available", availableList));
        lists.add(titled("Selected" + (countHint.isEmpty() ? "" : " (" + countHint + ")"), selectedList));
        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.add(lists, BorderLayout.CENTER);
        center.add(moves, BorderLayout.EAST);
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = button("OK", () -> {
            result = Collections.list(selected.elements());
            dispose();
        });
        buttons.add(ok);
        buttons.add(button("Cancel", this::dispose));
        getRootPane().setDefaultButton(ok);

        add(center, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(new Dimension(620, 420));
        setLocationRelativeTo(parent);
    }

    /** Shows the dialog; returns the new selection, or null if cancelled. */
    static List<String> show(Component parent, String title, List<String> options, List<String> current,
                             Function<String, String> label, boolean orderable, String countHint) {
        IdListDialog dialog = new IdListDialog(parent, title, options, current, label, orderable, countHint);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void add() {
        for (String id : availableList.getSelectedValuesList()) {
            available.removeElement(id);
            selected.addElement(id);
        }
    }

    private void remove() {
        for (String id : selectedList.getSelectedValuesList()) {
            selected.removeElement(id);
            available.addElement(id);
        }
    }

    private void move(int delta) {
        int index = selectedList.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= selected.size()) {
            return;
        }
        List<String> items = new ArrayList<>(Collections.list(selected.elements()));
        Collections.swap(items, index, target);
        selected.clear();
        items.forEach(selected::addElement);
        selectedList.setSelectedIndex(target);
    }

    private static JPanel titled(String title, JList<String> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(110, button.getPreferredSize().height));
        button.addActionListener(e -> action.run());
        return button;
    }

    private static MouseAdapter doubleClick(Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    action.run();
                }
            }
        };
    }
}
