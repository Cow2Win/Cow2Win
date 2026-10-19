package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.domain.FortificationAssignment;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.stage.ConceptInfoModel;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The dialog "Fortification assignment" of the concept stage view (opened by a double-click on a
 * fortification of the map): assign, move, swap and remove the teams of one fortification by
 * hand - see {@link FortificationAssignmentPanel}. Every change goes straight into the open
 * lineup (unsaved, like the team overviews); the dialog only has "Close" (Escape closes it, too).
 * The choices are made in small list dialogs (see {@link ListChooser}).
 */
public class FortificationAssignmentDialog extends JDialog {

    private static final String KEY_TITLE = "fortificationAssignment.title";
    private static final String KEY_CLOSE = "common.close";

    private final FortificationAssignmentPanel panel;

    public FortificationAssignmentDialog(Window owner, AppContext appContext, Fortification fortification) {
        super(owner, LanguageService.displayName(KEY_TITLE, LanguageService.displayName(fortification.id())),
                ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        panel = new FortificationAssignmentPanel(appContext, fortification, new ListChooser(this, appContext));

        JButton close = new JButton(LanguageService.displayName(KEY_CLOSE));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        closeOnEscape(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                panel.detach();
            }
        });
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog for {@code fortification} and waits until it is closed. */
    public static void open(Window owner, AppContext appContext, Fortification fortification) {
        new FortificationAssignmentDialog(owner, appContext, fortification).setVisible(true);
    }

    private static void closeOnEscape(JDialog dialog) {
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // --- choices ---

    /** Makes the choices of the panel in small modal list dialogs. */
    private static final class ListChooser implements FortificationAssignmentPanel.Chooser {

        private final Window owner;
        private final AppContext appContext;

        ListChooser(Window owner, AppContext appContext) {
            this.owner = owner;
            this.appContext = appContext;
        }

        @Override
        public Optional<FortificationAssignment.Candidate> chooseCandidate(
                Fortification fortification, List<FortificationAssignment.Candidate> candidates) {
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(owner, LanguageService.displayName("fortificationAssignment.noCandidates"),
                        LanguageService.displayName("fortificationAssignment.chooseTeam"), JOptionPane.INFORMATION_MESSAGE);
                return Optional.empty();
            }
            List<Object> items = new ArrayList<>();
            boolean headingAdded = false;
            for (FortificationAssignment.Candidate candidate : candidates) {
                if (!candidate.isFree() && !headingAdded) {
                    items.add(new Heading(LanguageService.displayName("fortificationAssignment.assignedElsewhere")));
                    headingAdded = true;
                }
                items.add(candidate);
            }
            return choose(owner, LanguageService.displayName("fortificationAssignment.chooseTeam"), null, items,
                    FortificationAssignment.Candidate.class, candidate -> candidateRow(candidate, fortification));
        }

        @Override
        public Optional<Fortification> chooseTarget(List<FortificationAssignmentPanel.Target> targets) {
            return choose(owner, LanguageService.displayName("fortificationAssignment.chooseTarget"), null,
                    new ArrayList<>(targets), FortificationAssignmentPanel.Target.class, this::targetRow)
                    .map(FortificationAssignmentPanel.Target::fortification);
        }

        @Override
        public Optional<ConceptInfoModel.AssignedTeam> chooseSwapPartner(Fortification target,
                                                                         List<ConceptInfoModel.AssignedTeam> teams) {
            String swapWith = LanguageService.displayName("fortificationAssignment.swapWith",
                    LanguageService.displayName(target.id()));
            return choose(owner, swapWith, swapWith, new ArrayList<>(teams), ConceptInfoModel.AssignedTeam.class,
                    team -> swapRow(team, target));
        }

        /** A candidate for {@code fortification} - its buff cell counts for {@code fortification}. */
        private JComponent candidateRow(FortificationAssignment.Candidate candidate, Fortification fortification) {
            JPanel row = FortificationAssignmentPanel.row();
            List<JLabel> labels = new ArrayList<>();
            labels.add(new JLabel(candidate.memberName()));
            row.add(FortificationAssignmentPanel.cell(labels.get(0), FortificationAssignmentPanel.NAME_WIDTH));
            row.add(FortificationAssignmentPanel.cell(TeamAvatars.of(candidate.entryIn(""), appContext.guild(), FortificationAssignmentPanel.AVATAR_SIZE),
                    FortificationAssignmentPanel.AVATARS_WIDTH));
            labels.add(FortificationAssignmentPanel.rightAligned(FortificationPanel.compactPower(candidate.power())));
            row.add(FortificationAssignmentPanel.cell(labels.get(1), FortificationAssignmentPanel.POWER_WIDTH));
            labels.add(FortificationAssignmentPanel.buffCountLabel(fortification, candidate.entryIn(fortification.id()),
                    appContext.guild()));
            row.add(FortificationAssignmentPanel.cell(labels.get(2), FortificationAssignmentPanel.BUFF_WIDTH));
            labels.add(FortificationAssignmentPanel.rightAligned(FortificationAssignmentPanel.cowScoreText(candidate.cowScore())));
            row.add(FortificationAssignmentPanel.cell(labels.get(3), FortificationAssignmentPanel.COW_SCORE_WIDTH));
            if (!candidate.isFree()) {
                row.add(Box.createHorizontalStrut(12));
                JLabel currentlyIn = new JLabel(LanguageService.displayName("fortificationAssignment.currentlyIn",
                        LanguageService.displayName(candidate.currentFortificationId())));
                labels.add(currentlyIn);
                row.add(currentlyIn);
                // Assigned elsewhere: muted, the free teams come first.
                labels.forEach(label -> label.setForeground(AppColors.TEXT_MUTED));
            }
            return row;
        }

        private JComponent targetRow(FortificationAssignmentPanel.Target target) {
            JPanel row = FortificationAssignmentPanel.row();
            row.add(FortificationAssignmentPanel.cell(new JLabel(LanguageService.displayName(target.fortification().id())),
                    FortificationAssignmentPanel.NAME_WIDTH + 40));
            JLabel filled = FortificationAssignmentPanel.rightAligned(
                    target.filledSlots() + "/" + target.fortification().capacity());
            if (target.isFull()) {
                filled.setForeground(AppColors.TEXT_MUTED);
            }
            row.add(FortificationAssignmentPanel.cell(filled, 50));
            return row;
        }

        /** A team of the full fortification {@code target} - its buff cell counts for {@code target}. */
        private JComponent swapRow(ConceptInfoModel.AssignedTeam team, Fortification target) {
            JPanel row = FortificationAssignmentPanel.row();
            row.add(FortificationAssignmentPanel.cell(new JLabel(team.memberName()), FortificationAssignmentPanel.NAME_WIDTH));
            row.add(FortificationAssignmentPanel.cell(TeamAvatars.of(team.entry(), appContext.guild(), FortificationAssignmentPanel.AVATAR_SIZE),
                    FortificationAssignmentPanel.AVATARS_WIDTH));
            row.add(FortificationAssignmentPanel.cell(
                    FortificationAssignmentPanel.rightAligned(FortificationPanel.compactPower(team.power())),
                    FortificationAssignmentPanel.POWER_WIDTH));
            row.add(FortificationAssignmentPanel.cell(
                    FortificationAssignmentPanel.buffCountLabel(target, team.entry(), appContext.guild()),
                    FortificationAssignmentPanel.BUFF_WIDTH));
            return row;
        }
    }

    /** A section heading in a choice list - cannot be chosen. */
    private record Heading(String text) {
    }

    /**
     * Shows {@code items} (elements of {@code type}, plus {@link Heading}s) in a small modal list
     * dialog with "Apply" and "Cancel"; a double-click or Enter applies, Escape cancels.
     *
     * @param heading optional text above the list, null for none
     * @return the chosen item, empty if cancelled
     */
    private static <T> Optional<T> choose(Window owner, String title, String heading, List<Object> items,
                                          Class<T> type, Function<T, JComponent> rowFactory) {
        JDialog dialog = new JDialog(owner, title, ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        DefaultListModel<Object> model = new DefaultListModel<>();
        items.forEach(model::addElement);
        JList<Object> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionModel(new SkippingSelectionModel(model));
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JComponent component;
            if (value instanceof Heading h) {
                JLabel label = new JLabel(h.text());
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setForeground(AppColors.TEXT_MUTED);
                label.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
                component = label;
            } else {
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                wrapper.setOpaque(true);
                wrapper.setBackground(isSelected ? jList.getSelectionBackground() : jList.getBackground());
                wrapper.add(rowFactory.apply(type.cast(value)), BorderLayout.CENTER);
                component = wrapper;
            }
            return component;
        });
        int first = firstSelectable(model);
        if (first >= 0) {
            list.setSelectedIndex(first);
        }

        Object[] result = new Object[1];
        JButton apply = new JButton(LanguageService.displayName("fortificationAssignment.apply"));
        JButton cancel = new JButton(LanguageService.displayName("common.cancel"));
        Runnable applyAction = () -> {
            Object selected = list.getSelectedValue();
            if (type.isInstance(selected)) {
                result[0] = selected;
                dialog.dispose();
            }
        };
        apply.addActionListener(e -> applyAction.run());
        apply.setEnabled(type.isInstance(list.getSelectedValue()));
        list.addListSelectionListener(e -> apply.setEnabled(type.isInstance(list.getSelectedValue())));
        cancel.addActionListener(e -> dialog.dispose());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    applyAction.run();
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        if (heading != null) {
            content.add(new JLabel(heading), BorderLayout.NORTH);
        }
        JScrollPane scrollPane = new JScrollPane(list);
        Dimension preferred = list.getPreferredSize();
        scrollPane.setPreferredSize(new Dimension(Math.max(preferred.width + 30, 300), Math.min(preferred.height + 6, 420)));
        content.add(scrollPane, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(apply);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(apply);
        closeOnEscape(dialog);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(type.cast(result[0]));
    }

    private static int firstSelectable(DefaultListModel<Object> model) {
        for (int i = 0; i < model.size(); i++) {
            if (!(model.get(i) instanceof Heading)) {
                return i;
            }
        }
        return -1;
    }

    /** Keeps the selection off the {@link Heading}s: it skips them in the direction it moves. */
    private static final class SkippingSelectionModel extends DefaultListSelectionModel {

        private final DefaultListModel<Object> model;

        SkippingSelectionModel(DefaultListModel<Object> model) {
            this.model = model;
            setSelectionMode(SINGLE_SELECTION);
        }

        @Override
        public void setSelectionInterval(int index0, int index1) {
            int index = index1;
            if (index >= 0 && index < model.size() && model.get(index) instanceof Heading) {
                int step = index >= getLeadSelectionIndex() ? 1 : -1;
                index += step;
                if (index < 0 || index >= model.size()) {
                    return;
                }
            }
            super.setSelectionInterval(index, index);
        }
    }
}
