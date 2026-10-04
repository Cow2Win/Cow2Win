package org.c2w.gui.journal;

import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalCounts;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.db.Season;
import org.c2w.service.AppContext;
import org.c2w.service.JournalMaintenanceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The seasons of the journal (not modal): number, start, end (last day), number
 * of battles, note. New (suggested after the last season in the 12-week
 * raster), edit (end suggestion = start + 12 weeks), delete (only the season, or
 * with all its battles). Overlapping seasons are refused naming the other one;
 * before saving, the user sees how many battles change their season - after
 * every change all battles are reassigned by date in the same transaction.
 */
public final class JournalSeasonsDialog extends JDialog {

    private final AppContext context;
    private final JournalMaintenanceService service;
    private final Runnable onChanged;
    private final SeasonTableModel tableModel = new SeasonTableModel();
    private final JTable table;
    private final JButton newButton = new JButton(JournalTexts.text("journal.seasons.new"));
    private final JButton editButton = new JButton(JournalTexts.text("journal.seasons.edit"));
    private final JButton deleteButton = new JButton(JournalTexts.text("journal.seasons.delete"));
    private final JLabel message = new JLabel(" ");
    private boolean hasJournal;
    private final AppContext.Listener listener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            reload();
        }
    };

    public JournalSeasonsDialog(Window owner, AppContext context, JournalMaintenanceService service, Runnable onChanged) {
        super(owner, ModalityType.MODELESS);
        this.context = context;
        this.service = service;
        this.onChanged = onChanged;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table = JournalSwing.table(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        newButton.addActionListener(e -> createSeason());
        editButton.addActionListener(e -> selected().ifPresent(this::editSeason));
        deleteButton.addActionListener(e -> selected().ifPresent(this::deleteSeason));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttons.add(newButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(message);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        context.addListener(listener);
        setSize(760, 380);
        setLocationRelativeTo(owner);
        updateButtons();
        reload();
    }

    @Override
    public void dispose() {
        context.removeListener(listener);
        super.dispose();
    }

    /** Reads the seasons and their battle counts again (background). */
    public void reload() {
        setTitle(JournalTexts.text("journal.seasons.title", context.guild() == null ? "" : context.guild().name()));
        JournalSwing.background(this, () -> {
            Optional<JournalRepository> repo = service.repository();
            if (repo.isEmpty()) {
                return Optional.<Object[]>empty();
            }
            Map<Integer, Integer> battles = new HashMap<>();
            for (BattleSummary b : repo.get().listBattles(null)) {
                if (b.seasonId() != null) {
                    battles.merge(b.seasonId(), 1, Integer::sum);
                }
            }
            return Optional.of(new Object[]{repo.get().listSeasons(), battles});
        }, loaded -> {
            hasJournal = loaded.isPresent();
            message.setText(hasJournal ? " " : JournalTexts.text("journal.noJournal"));
            @SuppressWarnings("unchecked")
            List<Season> seasons = loaded.map(o -> (List<Season>) o[0]).orElse(List.of());
            @SuppressWarnings("unchecked")
            Map<Integer, Integer> battles = loaded.map(o -> (Map<Integer, Integer>) o[1]).orElse(Map.of());
            tableModel.set(seasons, battles);
            updateButtons();
        });
    }

    private Optional<Season> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(tableModel.season(row));
    }

    private void updateButtons() {
        newButton.setEnabled(hasJournal);
        editButton.setEnabled(hasJournal && selected().isPresent());
        deleteButton.setEnabled(hasJournal && selected().isPresent());
    }

    private void createSeason() {
        JournalSwing.background(this, () -> service.suggestNewSeason(LocalDate.now()), suggested ->
                edit(suggested, JournalTexts.text("journal.seasons.newTitle")));
    }

    private void editSeason(Season season) {
        edit(season, JournalTexts.text("journal.seasons.editTitle"));
    }

    /**
     * Shows the edit form; on a conflict (checked in the background) it is shown again with
     * the entered values, otherwise the change is previewed and saved.
     */
    private void edit(Season initial, String title) {
        JSpinner number = new JSpinner(new SpinnerNumberModel(initial.number(), 0, 9999, 1));
        JSpinner start = JournalImportDialog.dateSpinner(initial.start());
        JSpinner end = JournalImportDialog.dateSpinner(initial.lastDay());
        JTextField note = new JTextField(initial.note() == null ? "" : initial.note(), 24);
        start.addChangeListener(e -> end.setValue(JournalImportDialog.toDate(
                date(start).plus(Season.DEFAULT_LENGTH).minusDays(1))));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 3, 3, 6);
        Object[][] rows = {
                {"journal.seasons.number", number}, {"journal.seasons.start", start},
                {"journal.seasons.end", end}, {"journal.seasons.note", note}};
        for (int i = 0; i < rows.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            form.add(new JLabel(JournalTexts.text((String) rows[i][0])), c);
            c.gridx = 1;
            form.add((Component) rows[i][1], c);
        }
        c.gridy = rows.length;
        c.gridx = 1;
        form.add(new JLabel(JournalTexts.text("journal.seasons.endHint")), c);

        LocalDate first;
        LocalDate last;
        do {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            first = date(start);
            last = date(end);
            if (last.isBefore(first)) {
                JOptionPane.showMessageDialog(this, JournalTexts.text("journal.seasons.invalidDates"), title,
                        JOptionPane.WARNING_MESSAGE);
            }
        } while (last.isBefore(first));
        String noteText = note.getText().isBlank() ? null : note.getText().strip();
        Season candidate = new Season(initial.id(), (Integer) number.getValue(), first, last.plusDays(1), noteText);
        JournalSwing.background(this, () -> service.findConflict(candidate), conflict -> {
            if (conflict.isPresent()) {
                JOptionPane.showMessageDialog(this, JournalTexts.seasonConflict(conflict.get()), title,
                        JOptionPane.WARNING_MESSAGE);
                edit(candidate, title);
            } else {
                save(candidate, title);
            }
        });
    }

    private void save(Season candidate, String title) {
        JournalSwing.background(this, () -> service.previewSave(candidate), changes -> {
            if (changes > 0 && !JournalSwing.confirm(this, JournalTexts.reassignPreview(changes), title)) {
                return;
            }
            JournalSwing.background(this, () -> service.saveSeason(candidate), change -> {
                reload();
                onChanged.run();
            });
        });
    }

    private void deleteSeason(Season season) {
        JRadioButton only = new JRadioButton(JournalTexts.text("journal.seasons.deleteOnly"), true);
        JRadioButton withBattles = new JRadioButton(JournalTexts.text("journal.seasons.deleteWithBattles"));
        ButtonGroup group = new ButtonGroup();
        group.add(only);
        group.add(withBattles);
        JPanel panel = JournalImportDialog.verticalPanel();
        panel.add(new JLabel(JournalTexts.text("journal.seasons.deleteChoice", JournalTexts.seasonText(season))));
        panel.add(only);
        panel.add(withBattles);
        String title = JournalTexts.text("journal.seasons.deleteTitle");
        if (JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        boolean includeBattles = withBattles.isSelected();
        JournalSwing.background(this, () -> service.countSeason(season.id()), (JournalCounts counts) -> {
            if (!JournalSwing.confirm(this, JournalTexts.deleteSeasonQuestion(season, counts, includeBattles), title)) {
                return;
            }
            JournalSwing.background(this, () -> service.deleteSeason(season.id(), includeBattles), change -> {
                reload();
                onChanged.run();
            });
        });
    }

    private static LocalDate date(JSpinner spinner) {
        return JournalImportDialog.toLocalDate((Date) spinner.getValue());
    }

    /** Rows: number, start, end (last day), battles, note. */
    private static final class SeasonTableModel extends AbstractTableModel {
        private static final String[] KEYS = {"number", "start", "end", "battles", "note"};
        private List<Season> seasons = List.of();
        private Map<Integer, Integer> battles = Map.of();

        void set(List<Season> newSeasons, Map<Integer, Integer> newBattles) {
            seasons = newSeasons;
            battles = newBattles;
            fireTableDataChanged();
        }

        Season season(int row) {
            return seasons.get(row);
        }

        @Override
        public int getRowCount() {
            return seasons.size();
        }

        @Override
        public int getColumnCount() {
            return KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            return JournalTexts.text("journal.seasons.col." + KEYS[column]);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0, 3 -> Integer.class;
                case 1, 2 -> LocalDate.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            Season s = seasons.get(row);
            return switch (column) {
                case 0 -> s.number();
                case 1 -> s.start();
                case 2 -> s.lastDay();
                case 3 -> battles.getOrDefault(s.id(), 0);
                default -> s.note() == null ? "" : s.note();
            };
        }
    }
}
