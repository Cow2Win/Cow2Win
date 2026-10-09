package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalCounts;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.LogInfo;
import org.c2w.data.journal.db.Season;
import org.c2w.service.JournalMaintenanceService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * The battle actions offered by the battle list and the battle detail: delete,
 * parse again, save the original CSV, set the season by hand. Every database
 * access runs in the background; after a change {@code onChanged} runs (on the
 * Swing thread) so all journal windows reload.
 */
final class JournalOperations {

    private final JournalMaintenanceService service;
    private final Runnable onChanged;

    JournalOperations(JournalMaintenanceService service, Runnable onChanged) {
        this.service = service;
        this.onChanged = onChanged;
    }

    JournalMaintenanceService service() {
        return service;
    }

    /** Asks - naming exactly what goes - and deletes the battles; {@code afterDelete} runs on success. */
    void deleteBattles(Component parent, List<BattleSummary> battles, Runnable afterDelete) {
        if (battles.isEmpty()) {
            return;
        }
        List<Integer> ids = battles.stream().map(BattleSummary::battleId).toList();
        JournalSwing.background(parent, () -> service.countBattles(ids), (JournalCounts counts) -> {
            if (!JournalSwing.confirm(parent, JournalTexts.deleteBattlesQuestion(battles, counts),
                    JournalTexts.text("journal.delete.title"))) {
                return;
            }
            JournalSwing.background(parent, () -> service.deleteBattles(ids), deleted -> {
                if (afterDelete != null) {
                    afterDelete.run();
                }
                onChanged.run();
            });
        });
    }

    /**
     * Parses the stored logs of these battles ({@code null}: all) again, with a progress
     * window, and shows the result (logs, parse problems before → after).
     */
    void reparse(Component parent, List<Integer> battleIdsOrNull) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        JDialog progressDialog = new JDialog(owner, JournalTexts.text("journal.reparse.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar bar = new JProgressBar();
        bar.setStringPainted(true);
        bar.setIndeterminate(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(bar, BorderLayout.CENTER);
        progressDialog.setContentPane(panel);
        progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progressDialog.setSize(420, 110);
        progressDialog.setLocationRelativeTo(parent);

        SwingWorker<JournalMaintenanceService.ReparseResult, int[]> worker = new SwingWorker<>() {
            @Override
            protected JournalMaintenanceService.ReparseResult doInBackground() throws JournalException {
                return service.reparse(battleIdsOrNull, (done, total) -> publish(new int[]{done, total}));
            }

            @Override
            protected void process(List<int[]> chunks) {
                int[] last = chunks.get(chunks.size() - 1);
                bar.setIndeterminate(false);
                bar.setMaximum(Math.max(1, last[1]));
                bar.setValue(last[0]);
                bar.setString(JournalTexts.text("journal.reparse.running", String.valueOf(last[0]),
                        String.valueOf(last[1])));
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    JOptionPane.showMessageDialog(parent, JournalTexts.reparseResult(get()),
                            JournalTexts.text("journal.reparse.title"), JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    JournalSwing.showError(parent, e.getCause());
                }
                onChanged.run();
            }
        };
        worker.execute();
        progressDialog.setVisible(true);
    }

    /** Saves the original CSV of one log of the battle to a file the user chooses. */
    void saveCsv(Component parent, BattleSummary battle, LogDirection direction) {
        JournalSwing.background(parent, () -> {
            var repo = service.repository().orElseThrow(() -> new JournalException("The open guild has no journal"));
            Optional<byte[]> raw = repo.loadRawCsv(battle.battleId(), direction);
            String name = repo.listLogs(battle.battleId()).stream().filter(l -> l.direction() == direction)
                    .map(LogInfo::fileName).findFirst().orElse("battle-log.csv");
            return raw.map(bytes -> new Object[]{name, bytes}).orElse(null);
        }, found -> {
            if (found == null) {
                return;
            }
            JFileChooser chooser = new JFileChooser(JournalActions.startDirectory().toFile());
            chooser.setDialogTitle(JournalTexts.text("journal.saveCsv.title"));
            chooser.setSelectedFile(new File(JournalActions.startDirectory().toFile(), (String) found[0]));
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path target = chooser.getSelectedFile().toPath();
            if (Files.exists(target) && !JournalSwing.confirm(parent,
                    JournalTexts.text("journal.saveCsv.overwrite", target.getFileName().toString()),
                    JournalTexts.text("journal.saveCsv.title"))) {
                return;
            }
            JournalSwing.background(parent, () -> Files.write(target, (byte[]) found[1]), written -> {
            });
        });
    }

    /** Lets the user pick a season containing the battle's day, or none. */
    void assignSeason(Component parent, BattleSummary battle) {
        JournalSwing.background(parent, () -> service.seasonsFor(battle.date()), (List<Season> seasons) -> {
            List<Object> choices = new ArrayList<>();
            choices.add(JournalTexts.text("journal.assignSeason.none"));
            choices.addAll(seasons);
            JComboBox<Object> combo = new JComboBox<>(choices.toArray());
            combo.setRenderer(PlayersStepPanel.textRenderer(item -> item instanceof Season s
                    ? JournalTexts.seasonText(s) : String.valueOf(item)));
            for (Season s : seasons) {
                if (battle.seasonId() != null && s.id() == battle.seasonId()) {
                    combo.setSelectedItem(s);
                }
            }
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.add(new JLabel(JournalTexts.text("journal.assignSeason.question", JournalTexts.date(battle.date()),
                    battle.opponent().name())), BorderLayout.NORTH);
            panel.add(combo, BorderLayout.CENTER);
            if (JOptionPane.showConfirmDialog(parent, panel, JournalTexts.text("journal.assignSeason.title"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            Integer seasonId = combo.getSelectedItem() instanceof Season s ? s.id() : null;
            JournalSwing.background(parent, () -> {
                service.assignSeason(battle.battleId(), seasonId);
                return seasonId;
            }, done -> onChanged.run());
        });
    }

    /** A "save original CSV" menu with one entry per stored log of the battle. */
    JMenu saveCsvMenu(Component parent, BattleSummary battle) {
        JMenu menu = new JMenu(JournalTexts.text("journal.action.saveCsv"));
        for (LogDirection direction : List.of(LogDirection.DEFENSE, LogDirection.ATTACK)) {
            JMenuItem item = new JMenuItem(JournalTexts.of("action.saveCsv", direction));
            item.setEnabled(battle.directions().contains(direction));
            item.addActionListener(e -> saveCsv(parent, battle, direction));
            menu.add(item);
        }
        return menu;
    }
}
