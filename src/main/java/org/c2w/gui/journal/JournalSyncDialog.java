package org.c2w.gui.journal;

import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.JournalSyncService;
import org.c2w.service.journal.SyncPlan;
import org.c2w.service.journal.SyncResult;
import org.c2w.service.journal.SyncRow;
import org.c2w.service.journal.TeamBuildPlan.SkippedPlayer;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Update teams (sync)" (modal): the teams of the newest defense log matched to
 * the stored teams - per row the checkboxes power and units, member (log name),
 * team (selectable), fortification/position, power old → new, certainty, unit
 * changes and hints; filters "only changes" and "only unsure"; below, collapsible,
 * the log teams without a match, skipped players and members not in the log.
 * Reads in the background; the logic is in {@link SyncModel} and the
 * {@link JournalSyncService}. Afterwards a summary and "Save guild now".
 */
public final class JournalSyncDialog extends JDialog {

    private final AppContext context;
    private final GuildService guildService;
    private final JournalSyncService service;
    private final Runnable openTeamBuilder;
    private final JLabel source = new JLabel(" ");
    private final JLabel counts = new JLabel(" ");
    private final JCheckBox onlyChanges = new JCheckBox(JournalTexts.text("journal.sync.onlyChanges"), true);
    private final JCheckBox onlyUnsure = new JCheckBox(JournalTexts.text("journal.sync.onlyUnsure"));
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel problems = new JLabel(" ");
    private final JButton applyButton = new JButton();
    private final JButton allSureButton = new JButton(JournalTexts.text("journal.sync.selectSure"));
    private final JButton clearButton = new JButton(JournalTexts.text("journal.teams.clear"));
    private final JButton cancelButton = new JButton(JournalTexts.text("journal.button.cancel"));
    private final JPanel extraButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private SyncModel model;
    private RowsTableModel tableModel;

    private JournalSyncDialog(Window owner, AppContext context, GuildService guildService, Runnable openTeamBuilder) {
        super(owner, JournalTexts.text("journal.sync.title"), ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.guildService = guildService;
        this.service = new JournalSyncService(context, guildService);
        this.openTeamBuilder = openTeamBuilder;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel top = JournalImportDialog.verticalPanel();
        source.setFont(source.getFont().deriveFont(Font.BOLD));
        top.add(source);
        top.add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.sync.hint")));
        top.add(counts);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.setAlignmentX(LEFT_ALIGNMENT);
        filters.add(onlyChanges);
        filters.add(onlyUnsure);
        top.add(filters);
        onlyChanges.addActionListener(e -> {
            model.setOnlyChanges(onlyChanges.isSelected());
            refresh();
        });
        onlyUnsure.addActionListener(e -> {
            model.setOnlyUnsure(onlyUnsure.isSelected());
            refresh();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(allSureButton);
        buttons.add(clearButton);
        buttons.add(applyButton);
        buttons.add(cancelButton);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        problems.setForeground(Color.RED);
        south.add(problems, BorderLayout.NORTH);
        south.add(extraButtons, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        applyButton.addActionListener(e -> apply());
        allSureButton.addActionListener(e -> {
            model.selectAllSure();
            refresh();
        });
        clearButton.addActionListener(e -> {
            model.clearSelection();
            refresh();
        });
        cancelButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Logger.log("Sync dialog closed");
            }
        });
        setSize(1250, 720);
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog and reads the journal in the background. */
    public static void open(Window owner, AppContext context, GuildService guildService, Runnable openTeamBuilder) {
        JournalSyncDialog dialog = new JournalSyncDialog(owner, context, guildService, openTeamBuilder);
        dialog.load();
        dialog.setVisible(true);
    }

    private void load() {
        source.setText(JournalTexts.text("journal.import.reading"));
        setButtonsEnabled(false);
        JPanel busy = new JPanel(new GridBagLayout());
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        busy.add(progress);
        center.add(busy, BorderLayout.CENTER);
        JournalSwing.background(this, service::prepare, plan -> {
            model = new SyncModel(plan, context.guild(), context.catalog());
            show(plan);
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        applyButton.setEnabled(enabled);
        allSureButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        onlyChanges.setEnabled(enabled);
        onlyUnsure.setEnabled(enabled);
    }

    // --- plan ---

    private void show(SyncPlan plan) {
        center.removeAll();
        if (!plan.hasSource()) {
            source.setText(JournalTexts.text("menu.journal.sync"));
            center.add(JournalImportDialog.wrapLabel(JournalTexts.of("sync.empty", plan.emptyReason())),
                    BorderLayout.NORTH);
            applyButton.setVisible(false);
            allSureButton.setVisible(false);
            clearButton.setVisible(false);
            onlyChanges.setVisible(false);
            onlyUnsure.setVisible(false);
            cancelButton.setText(JournalTexts.text("journal.button.close"));
            center.revalidate();
            return;
        }
        SyncPlan.Source s = plan.source();
        source.setText(JournalTexts.text("journal.sync.source", JournalTexts.date(s.date()),
                s.opponent() == null ? "?" : JournalTexts.opponent(s.opponent()),
                s.status() == null ? "" : JournalTexts.of("battleStatus", s.status())));
        source.setToolTipText(JournalTexts.text("journal.sync.sourceFile", String.valueOf(s.fileName()),
                s.language() == null ? "" : s.language(), JournalTexts.dateTime(s.importedAt())));
        tableModel = new RowsTableModel();
        JTable table = JournalSwing.table(tableModel);
        table.setRowHeight(Math.max(table.getRowHeight(), 22));
        table.setDefaultRenderer(Boolean.class, new CheckRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new TeamEditor());
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int column) {
                SyncRow r = tableModel.row(row);
                String text = teamText(r, (Integer) value);
                if (r.targets().size() > 1) {
                    text += " ▾";
                }
                return super.getTableCellRendererComponent(t, text, selected, focus, row, column);
            }
        });
        int[] widths = {55, 70, 170, 150, 150, 230, 110, 260, 260};
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(hintSections(plan), BorderLayout.SOUTH);
        setButtonsEnabled(true);
        refresh();
    }

    private void refresh() {
        tableModel.reload();
        SyncPlan plan = model.plan();
        counts.setText(JournalTexts.text("journal.sync.counts", String.valueOf(plan.rows().size()),
                String.valueOf(model.changedCount()), String.valueOf(plan.count(SyncRow.Certainty.UNITS)),
                String.valueOf(plan.count(SyncRow.Certainty.SURE)), String.valueOf(plan.count(SyncRow.Certainty.UNSURE)),
                String.valueOf(plan.count(SyncRow.Certainty.AMBIGUOUS)), String.valueOf(plan.unmatched().size())));
        applyButton.setText(JournalTexts.text("journal.sync.apply", String.valueOf(model.selectedCount())));
        List<SyncResult.Error> errors = model.selectedCount() == 0 ? List.of() : model.problems();
        applyButton.setEnabled(model.canApply());
        problems.setText(errors.isEmpty() ? " " : errors.stream().map(this::errorText).distinct()
                .collect(Collectors.joining("  ")));
    }

    private String errorText(SyncResult.Error error) {
        return JournalTexts.text("journal.sync.error." + error.kind().name(),
                error.memberId() == null ? "" : model.memberName(error.memberId()));
    }

    private static String teamText(SyncRow row, int index) {
        return row.target(index).map(t -> JournalTexts.team(row.kind(), index) + " (" + JournalTexts.number(t.storedPower())
                + ")").orElse("?");
    }

    private String hintsText(String rowId) {
        List<String> hints = new ArrayList<>();
        SyncRow.Target t = model.targetTeam(rowId);
        for (SyncModel.Hint hint : model.hints(rowId)) {
            hints.add(switch (hint) {
                case EDITED_SINCE_BATTLE -> JournalTexts.text("journal.sync.hint.EDITED_SINCE_BATTLE",
                        JournalTexts.date(t.lastModified()));
                case LINEUP_OTHER_FORTIFICATION -> JournalTexts.text("journal.sync.hint.LINEUP_OTHER_FORTIFICATION",
                        model.plan().row(rowId).map(r -> JournalTexts.fortification(r.fortificationId(),
                                r.fortificationName())).orElse(""),
                        JournalTexts.fortification(t.lineupHint().lineupFortificationId(),
                                t.lineupHint().lineupFortificationId()));
                default -> JournalTexts.text("journal.sync.hint." + hint.name(), "");
            });
        }
        return String.join(" · ", hints);
    }

    // --- sections below the table ---

    private JComponent hintSections(SyncPlan plan) {
        JPanel panel = JournalImportDialog.verticalPanel();
        if (!plan.unmatched().isEmpty()) {
            List<String> lines = plan.unmatched().stream().map(u -> JournalTexts.text("journal.sync.unmatchedLine",
                    model.memberName(u.memberId()), JournalTexts.visibleSpaces(u.logName()),
                    JournalTexts.of("teamKind", u.kind()), JournalTexts.number(u.power()),
                    JournalTexts.fortification(u.fortificationId(), u.fortificationName()),
                    String.valueOf(u.position()))).toList();
            JButton build = new JButton(JournalTexts.text("menu.journal.buildTeams"));
            build.addActionListener(e -> {
                dispose();
                openTeamBuilder.run();
            });
            panel.add(section(JournalTexts.text("journal.sync.unmatched", String.valueOf(lines.size())), lines, build));
        }
        List<String> conflicts = plan.powerConflicts().stream().map(c -> JournalTexts.text(
                "journal.sync.conflictLine", model.memberName(c.memberId()),
                JournalTexts.fortification(c.fortificationId(), c.fortificationName()), String.valueOf(c.position()),
                JournalTexts.powers(c.powers()))).toList();
        if (!conflicts.isEmpty()) {
            panel.add(section(JournalTexts.text("journal.sync.conflicts", String.valueOf(conflicts.size())), conflicts,
                    null));
        }
        if (!plan.skippedPlayers().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (SkippedPlayer s : plan.skippedPlayers()) {
                lines.add(JournalTexts.text("journal.teams.skippedLine", JournalTexts.visibleSpaces(s.rawName()),
                        JournalTexts.of("skipReason", s.reason())));
            }
            panel.add(section(JournalTexts.text("journal.sync.skipped", String.valueOf(lines.size())), lines, null));
        }
        List<String> notInLog = new ArrayList<>(model.membersNotInLog());
        if (!notInLog.isEmpty()) {
            panel.add(section(JournalTexts.text("journal.sync.membersNotInLog", String.valueOf(notInLog.size())),
                    List.of(String.join(", ", notInLog)), null));
        }
        List<String> teams = plan.teamsNotInLog().stream().map(t -> model.memberName(t.memberId()) + ": "
                + JournalTexts.team(t.kind(), t.index())).toList();
        if (!teams.isEmpty()) {
            panel.add(section(JournalTexts.text("journal.sync.teamsNotInLog", String.valueOf(teams.size())),
                    List.of(String.join(", ", teams)), null));
        }
        return panel;
    }

    private JComponent section(String title, List<String> lines, JButton action) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(LEFT_ALIGNMENT);
        JTextArea text = new JTextArea(String.join("\n", lines), Math.min(5, Math.max(1, lines.size())), 60);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JPanel body = new JPanel(new BorderLayout());
        body.add(new JScrollPane(text), BorderLayout.CENTER);
        if (action != null) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
            actions.add(action);
            body.add(actions, BorderLayout.SOUTH);
        }
        body.setVisible(false);
        JToggleButton toggle = new JToggleButton("▸ " + title);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.addActionListener(e -> {
            body.setVisible(toggle.isSelected());
            toggle.setText((toggle.isSelected() ? "▾ " : "▸ ") + title);
            center.revalidate();
        });
        panel.add(toggle, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    // --- apply and result ---

    private void apply() {
        if (!model.canApply()) {
            return;
        }
        SyncResult result = service.apply(model.plan(), model.selection());
        if (!result.isSuccess()) {
            problems.setText(result.errors().stream().map(this::errorText).distinct().collect(Collectors.joining("  ")));
            return;
        }
        showResult(result);
    }

    private void showResult(SyncResult result) {
        counts.setText(JournalTexts.text("journal.sync.done"));
        onlyChanges.setVisible(false);
        onlyUnsure.setVisible(false);
        center.removeAll();
        JPanel panel = JournalImportDialog.verticalPanel();
        panel.add(new JLabel(JournalTexts.text("journal.sync.result", String.valueOf(result.teamsChanged()),
                String.valueOf(result.powerChanged()), String.valueOf(result.unitsChanged()))));
        for (SyncResult.DroppedPet pet : result.droppedPets()) {
            panel.add(new JLabel(JournalTexts.text("journal.sync.droppedPet", model.memberName(pet.memberId()),
                    JournalTexts.team(pet.kind(), pet.index()), LanguageService.displayName(pet.petId()))));
        }
        for (SyncResult.DroppedTotem totem : result.droppedTotems()) {
            panel.add(new JLabel(JournalTexts.text("journal.sync.droppedTotem", model.memberName(totem.memberId()),
                    JournalTexts.team(totem.kind(), totem.index()), TotemTexts.name(totem.totem()))));
        }
        if (context.isGuildDirty()) {
            panel.add(Box.createVerticalStrut(10));
            JLabel warning = JournalImportDialog.boldLabel(JournalTexts.text("journal.summary.guildChanged"));
            warning.setForeground(new Color(200, 120, 0));
            panel.add(warning);
            JButton save = new JButton(JournalTexts.text("journal.result.saveGuild"));
            save.addActionListener(e -> {
                try {
                    guildService.saveGuild();
                    save.setEnabled(false);
                    warning.setText(JournalTexts.text("journal.result.guildSaved"));
                    warning.setForeground(UIManager.getColor("Label.foreground"));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveGuildError") + "\n"
                            + ex.getMessage(), LanguageService.displayName("common.saveErrorTitle"),
                            JOptionPane.ERROR_MESSAGE);
                }
            });
            extraButtons.add(save);
        }
        center.add(new JScrollPane(panel), BorderLayout.CENTER);
        applyButton.setVisible(false);
        allSureButton.setVisible(false);
        clearButton.setVisible(false);
        problems.setText(" ");
        cancelButton.setText(JournalTexts.text("journal.button.close"));
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    // --- table ---

    private static final String[] COLUMNS = {"power", "units", "member", "team", "where", "powerChange", "certainty",
            "unitsChange", "hints"};

    /** The visible rows of the model; editing goes straight to the model. */
    private final class RowsTableModel extends AbstractTableModel {

        private List<SyncRow> rows = List.of();

        void reload() {
            rows = model.rows();
            fireTableDataChanged();
        }

        SyncRow row(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return JournalTexts.text("journal.sync.col." + COLUMNS[column]);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column <= 1 ? Boolean.class : column == 3 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            String id = rows.get(row).id();
            return switch (column) {
                case 0 -> model.powerSelectable(id);
                case 1 -> model.unitsSelectable(id);
                case 3 -> model.targetChoices(id).size() > 1;
                default -> false;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            SyncRow r = rows.get(row);
            String id = r.id();
            int target = model.target(id);
            return switch (column) {
                case 0 -> model.power(id);
                case 1 -> model.units(id);
                case 2 -> row > 0 && rows.get(row - 1).memberId().equals(r.memberId()) ? ""
                        : r.memberName() + (r.logName().equals(r.memberName()) ? ""
                        : " (" + JournalTexts.visibleSpaces(r.logName()) + ")");
                case 3 -> target;
                case 4 -> JournalTexts.text("journal.sync.where", JournalTexts.of("teamKind", r.kind()),
                        JournalTexts.fortification(r.fortificationId(), r.fortificationName()),
                        String.valueOf(r.position()));
                case 5 -> JournalTexts.powerChange(model.targetTeam(id).storedPower(), r.logPower());
                case 6 -> JournalTexts.certainty(model.certainty(id));
                case 7 -> r.logHasUnits() ? JournalTexts.unitsChange(model.targetTeam(id).unitsChange()) : "";
                case 8 -> hintsText(id);
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            String id = rows.get(row).id();
            switch (column) {
                case 0 -> model.setPower(id, Boolean.TRUE.equals(value));
                case 1 -> model.setUnits(id, Boolean.TRUE.equals(value));
                case 3 -> {
                    if (value instanceof Integer index) {
                        model.setTarget(id, index);
                    }
                }
                default -> {
                    return;
                }
            }
            SwingUtilities.invokeLater(JournalSyncDialog.this::refresh);
        }
    }

    /** Checkboxes, disabled where nothing can be taken over. */
    private final class CheckRenderer extends JCheckBox implements javax.swing.table.TableCellRenderer {
        CheckRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            setSelected(Boolean.TRUE.equals(value));
            setEnabled(table.getModel().isCellEditable(row, column));
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }
    }

    /** The team of a row: a combo box with the member's teams of that kind. */
    private final class TeamEditor extends DefaultCellEditor {
        private final JComboBox<Integer> combo;
        private SyncRow row;

        @SuppressWarnings("unchecked")
        TeamEditor() {
            super(new JComboBox<Integer>());
            combo = (JComboBox<Integer>) getComponent();
            combo.setRenderer(PlayersStepPanel.textRenderer(o -> row == null || o == null ? ""
                    : teamText(row, (Integer) o)));
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int r, int column) {
            row = tableModel.row(r);
            combo.removeAllItems();
            model.targetChoices(row.id()).forEach(combo::addItem);
            combo.setSelectedItem(value);
            return combo;
        }
    }

    /** For tests: the model once loaded. */
    SyncModel model() {
        return model;
    }

    /** Package-visible factory for the smoke test (not shown). */
    static JournalSyncDialog create(Window owner, AppContext context, GuildService guildService) {
        JournalSyncDialog dialog = new JournalSyncDialog(owner, context, guildService, () -> { });
        dialog.load();
        return dialog;
    }
}
