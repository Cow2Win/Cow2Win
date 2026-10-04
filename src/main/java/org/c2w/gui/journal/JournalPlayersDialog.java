package org.c2w.gui.journal;

import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.model.GuildMember;
import org.c2w.service.AppContext;
import org.c2w.service.JournalMaintenanceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The player assignments of the own guild (not modal): every journal player with
 * its name exactly as in the log, status, assigned member, defenses, last seen
 * and last team power(s); filters "only open" / "only problems"; changing the
 * status or the assigned member. Below, hints about members of the open guild
 * without a journal player or missing from the latest defense logs. The guild
 * itself is never changed here - no renaming, no new members.
 */
public final class JournalPlayersDialog extends JDialog {

    private final AppContext context;
    private final JournalMaintenanceService service;
    private final Runnable onChanged;
    private final PlayerTableModel tableModel = new PlayerTableModel();
    private final JTable table;
    private final JCheckBox onlyOpen = new JCheckBox(JournalTexts.text("journal.players.onlyOpen"));
    private final JCheckBox onlyProblems = new JCheckBox(JournalTexts.text("journal.players.onlyProblems"));
    private final JComboBox<AssignmentStatus> status = new JComboBox<>(AssignmentStatus.values());
    private final JComboBox<Object> member = new JComboBox<>();
    private final JButton apply = new JButton(JournalTexts.text("journal.players.edit.apply"));
    private final JTextArea hints = new JTextArea(6, 60);
    private final JLabel message = new JLabel(" ");
    private JournalPlayersModel model = new JournalPlayersModel(JournalPlayersModel.Data.EMPTY, null);
    private final AppContext.Listener listener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            reload();
        }
    };

    public JournalPlayersDialog(Window owner, AppContext context, JournalMaintenanceService service, Runnable onChanged) {
        super(owner, ModalityType.MODELESS);
        this.context = context;
        this.service = service;
        this.onChanged = onChanged;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table = JournalSwing.table(tableModel);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelection();
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(onlyOpen);
        top.add(onlyProblems);
        top.add(message);
        onlyOpen.addActionListener(e -> applyFilter());
        onlyProblems.addActionListener(e -> applyFilter());

        status.setRenderer(PlayersStepPanel.textRenderer(v -> JournalTexts.of("assignmentStatus", (Enum<?>) v)));
        member.setRenderer(PlayersStepPanel.textRenderer(v -> v instanceof GuildMember m ? memberName(m) : ""));
        status.addActionListener(e -> member.setEnabled(status.getSelectedItem() == AssignmentStatus.ASSIGNED));
        apply.addActionListener(e -> applyEdit());
        JPanel edit = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        edit.add(new JLabel(JournalTexts.text("journal.players.edit.status")));
        edit.add(status);
        edit.add(new JLabel(JournalTexts.text("journal.players.edit.member")));
        edit.add(member);
        edit.add(apply);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        JPanel editBox = new JPanel(new BorderLayout());
        editBox.add(edit, BorderLayout.NORTH);
        editBox.add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.players.edit.hint")), BorderLayout.SOUTH);
        south.add(editBox, BorderLayout.NORTH);
        hints.setEditable(false);
        hints.setLineWrap(true);
        hints.setWrapStyleWord(true);
        JScrollPane hintScroll = new JScrollPane(hints);
        hintScroll.setBorder(BorderFactory.createTitledBorder(JournalTexts.text("journal.players.hints")));
        south.add(hintScroll, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        context.addListener(listener);
        setSize(980, 640);
        setLocationRelativeTo(owner);
        showSelection();
        reload();
    }

    @Override
    public void dispose() {
        context.removeListener(listener);
        super.dispose();
    }

    /** Reads the players again (background). */
    public void reload() {
        setTitle(JournalTexts.text("journal.players.title", context.guild() == null ? "" : context.guild().name()));
        Integer selected = selectedRow() == null ? null : selectedRow().stats().player().id();
        JournalSwing.background(this, () -> JournalPlayersModel.load(service.repository()), data -> {
            model = new JournalPlayersModel(data, context.guild());
            model.setOnlyOpen(onlyOpen.isSelected());
            model.setOnlyProblems(onlyProblems.isSelected());
            DefaultComboBoxModel<Object> members = new DefaultComboBoxModel<>();
            model.members().forEach(members::addElement);
            member.setModel(members);
            message.setText(data.hasJournal() ? " " : JournalTexts.text("journal.noJournal"));
            hints.setText(hintText());
            hints.setCaretPosition(0);
            tableModel.setRows(model.rows());
            if (selected != null) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (tableModel.row(i).stats().player().id() == selected) {
                        int view = table.convertRowIndexToView(i);
                        table.setRowSelectionInterval(view, view);
                    }
                }
            }
            showSelection();
        });
    }

    private void applyFilter() {
        model.setOnlyOpen(onlyOpen.isSelected());
        model.setOnlyProblems(onlyProblems.isSelected());
        tableModel.setRows(model.rows());
        showSelection();
    }

    private String hintText() {
        List<JournalPlayersModel.MemberHint> list = model.memberHints();
        if (list.isEmpty()) {
            return JournalTexts.text("journal.players.noHints");
        }
        return list.stream().map(h -> JournalTexts.text("journal.players.memberHint." + h.kind().name(),
                        memberName(h.member()), String.valueOf(model.data().recentLogs())))
                .collect(Collectors.joining("\n"));
    }

    private static String memberName(GuildMember m) {
        return m.name() == null || m.name().isBlank() ? m.id() : m.name();
    }

    private JournalPlayersModel.Row selectedRow() {
        int view = table == null ? -1 : table.getSelectedRow();
        return view < 0 ? null : tableModel.row(table.convertRowIndexToModel(view));
    }

    private void showSelection() {
        JournalPlayersModel.Row row = selectedRow();
        status.setEnabled(row != null);
        apply.setEnabled(row != null);
        if (row == null) {
            member.setEnabled(false);
            return;
        }
        status.setSelectedItem(row.status());
        member.setSelectedItem(null);
        for (int i = 0; i < member.getItemCount(); i++) {
            if (member.getItemAt(i) instanceof GuildMember m && m.id().equals(row.stats().memberId())) {
                member.setSelectedIndex(i);
            }
        }
        member.setEnabled(row.status() == AssignmentStatus.ASSIGNED);
    }

    private void applyEdit() {
        JournalPlayersModel.Row row = selectedRow();
        if (row == null) {
            return;
        }
        AssignmentStatus newStatus = (AssignmentStatus) status.getSelectedItem();
        String memberId = member.getSelectedItem() instanceof GuildMember m ? m.id() : null;
        if (newStatus == AssignmentStatus.ASSIGNED && memberId == null) {
            JOptionPane.showMessageDialog(this, JournalTexts.text("journal.players.edit.chooseMember"),
                    getTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int playerId = row.stats().player().id();
        JournalSwing.background(this, () -> service.setAssignment(playerId, newStatus, memberId, context.guild()),
                done -> {
                    reload();
                    onChanged.run();
                });
    }

    /** Rows: log name, status, member, defenses, last seen, last team powers. */
    private static final class PlayerTableModel extends AbstractTableModel {
        private static final String[] KEYS = {"name", "status", "member", "defenses", "lastSeen", "lastPowers"};
        private List<JournalPlayersModel.Row> rows = List.of();

        void setRows(List<JournalPlayersModel.Row> newRows) {
            rows = newRows;
            fireTableDataChanged();
        }

        JournalPlayersModel.Row row(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            return JournalTexts.text("journal.players.col." + KEYS[column]);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 3 -> Integer.class;
                case 4 -> LocalDate.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            JournalPlayersModel.Row r = rows.get(row);
            return switch (column) {
                case 0 -> JournalTexts.visibleSpaces(r.stats().player().name());
                case 1 -> JournalTexts.of("assignmentStatus", r.status());
                case 2 -> memberText(r);
                case 3 -> r.stats().defenses();
                case 4 -> r.stats().lastSeen();
                default -> JournalTexts.powers(r.stats().lastTeamPowers());
            };
        }

        private static String memberText(JournalPlayersModel.Row r) {
            if (r.stats().memberId() == null) {
                return "";
            }
            String name = r.memberMissing()
                    ? JournalTexts.text("journal.players.memberMissing", r.stats().memberId())
                    : r.memberName();
            return r.sameMember() > 1 ? name + " " + JournalTexts.text("journal.players.sameMember",
                    String.valueOf(r.sameMember())) : name;
        }
    }
}
