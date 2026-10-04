package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.ParseProblem;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.service.journal.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Overview step of the import assistant: the battles of the plan (one row each),
 * files that are not imported with the reason, parse problems per file, and -
 * if nothing can be imported - the plan errors (with "switch to guild ..." for
 * logs of another Cow2Win guild).
 */
final class OverviewStepPanel extends JPanel {

    OverviewStepPanel(ImportPlan plan, Consumer<PlanError> onSwitchGuild) {
        super(new BorderLayout(0, 8));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        if (!plan.errors().isEmpty()) {
            top.add(JournalImportDialog.boldLabel(JournalTexts.text("journal.overview.errors")));
            for (PlanError error : plan.errors()) {
                top.add(JournalImportDialog.wrapLabel(JournalTexts.planError(error)));
                if (error.kind() == PlanError.Kind.LOGS_OF_OTHER_GUILD) {
                    JButton switchButton = new JButton(JournalTexts.text("journal.overview.switchGuild", error.guildName()));
                    switchButton.addActionListener(e -> onSwitchGuild.accept(error));
                    switchButton.setAlignmentX(LEFT_ALIGNMENT);
                    top.add(switchButton);
                }
            }
        }
        add(top, BorderLayout.NORTH);

        if (!plan.battles().isEmpty()) {
            add(new JScrollPane(battleTable(plan.battles())), BorderLayout.CENTER);
        }

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        List<PlannedFile> notImported = plan.files().stream()
                .filter(f -> f.status() != PlannedFile.Status.READY).toList();
        if (!notImported.isEmpty()) {
            bottom.add(JournalImportDialog.boldLabel(JournalTexts.text("journal.overview.notImported")));
            for (PlannedFile f : notImported) {
                bottom.add(JournalImportDialog.wrapLabel(f.path().getFileName() + " – " + JournalTexts.of("fileError", f.error())));
            }
        }
        for (PlannedFile f : plan.files()) {
            if (f.status() == PlannedFile.Status.READY && f.problemCount() > 0) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.add(new JLabel(f.path().getFileName() + ": "
                        + JournalTexts.text("journal.overview.problems", String.valueOf(f.problemCount()))));
                JButton details = new JButton(JournalTexts.text("journal.button.details"));
                details.addActionListener(e -> showProblems(f));
                row.add(details);
                bottom.add(row);
            }
        }
        add(bottom, BorderLayout.SOUTH);
    }

    private JTable battleTable(List<PlannedBattle> battles) {
        String[] columns = {
                JournalTexts.text("journal.overview.col.date"),
                JournalTexts.text("journal.overview.col.opponent"),
                JournalTexts.text("journal.overview.col.result"),
                JournalTexts.text("journal.overview.col.rankingPoints"),
                JournalTexts.of("direction", LogDirection.ATTACK),
                JournalTexts.of("direction", LogDirection.DEFENSE),
                JournalTexts.text("journal.overview.col.check"),
                JournalTexts.text("journal.overview.col.warnings")};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (PlannedBattle b : battles) {
            String result = b.status() == BattleStatus.RUNNING
                    ? JournalTexts.text("journal.overview.running")
                    : b.result() == null ? "" : JournalTexts.of("battleResult", b.result());
            model.addRow(new Object[]{
                    JournalTexts.date(b.date()),
                    JournalTexts.opponent(b.opponent()),
                    result,
                    JournalTexts.rankingPoints(b.rankingPoints()),
                    action(b, LogDirection.ATTACK),
                    action(b, LogDirection.DEFENSE),
                    verdictSymbol(b.check().verdict()) + " " + JournalTexts.of("verdict", b.check().verdict()),
                    b.warnings().stream().map(w -> JournalTexts.of("warning", w)).collect(Collectors.joining("; "))});
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(7).setPreferredWidth(220);
        return table;
    }

    private static String action(PlannedBattle battle, LogDirection direction) {
        PlannedBattle.LogAction action = battle.actions().get(direction);
        return action == null ? "–" : JournalTexts.of("logAction", action);
    }

    private static String verdictSymbol(BattleLogCheck.Verdict verdict) {
        return switch (verdict) {
            case MATCHES -> "✔";
            case MISMATCH -> "✖";
            case RUNNING -> "⏳";
            case NOT_CHECKABLE -> "?";
        };
    }

    private void showProblems(PlannedFile file) {
        String text = file.parsed().problems().stream().map(ParseProblem::toString).collect(Collectors.joining("\n"));
        JTextArea area = new JTextArea(text, 15, 80);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                JournalTexts.text("journal.overview.problemsTitle") + " – " + file.path().getFileName(),
                JOptionPane.INFORMATION_MESSAGE);
    }
}
