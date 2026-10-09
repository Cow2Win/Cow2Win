package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.Season;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The battle list of the open guild's journal (not modal): date, opponent,
 * result, ranking points, own/opponent points, stored logs, season - newest
 * first, sortable, filterable (season, opponent, period, status, result; the
 * filter lives in {@link JournalBattleTableModel}). Double click / Enter /
 * "Details …" opens the battle detail; the context menu and the Delete key offer
 * the battle actions (several battles at once for "parse again" and "delete").
 * Follows the open guild (reloads and retitles on a guild switch), reads in the
 * background and never creates a journal file. Battle logs can be dropped onto
 * it to import them.
 */
public final class JournalBattleListDialog extends JDialog {

    /** What the list needs from the journal menu. */
    public interface Host {
        /** Starts an import - with the dropped files, or with the file chooser if the list is empty. */
        void importFiles(List<Path> files);

        void openDetail(int battleId);

        void openPlayers();

        void openSeasons();

        void openNameMappings();

        JournalOperations operations();
    }

    private final AppContext context;
    private final Host host;
    private final JournalBattleTableModel tableModel = new JournalBattleTableModel();
    private final JTable table;
    private final JComboBox<Object> seasonFilter = new JComboBox<>();
    private final JTextField opponentFilter = new JTextField(12);
    private final JCheckBox fromEnabled = new JCheckBox(JournalTexts.text("journal.battles.from"));
    private final JSpinner fromDate = JournalImportDialog.dateSpinner(LocalDate.now().minusMonths(3));
    private final JCheckBox toEnabled = new JCheckBox(JournalTexts.text("journal.battles.to"));
    private final JSpinner toDate = JournalImportDialog.dateSpinner(LocalDate.now());
    private final JComboBox<BattleListFilter.StatusFilter> statusFilter =
            new JComboBox<>(BattleListFilter.StatusFilter.values());
    private final JComboBox<BattleListFilter.ResultFilter> resultFilter =
            new JComboBox<>(BattleListFilter.ResultFilter.values());
    private final JLabel countLabel = new JLabel(" ");
    private final JButton detailsButton = new JButton(JournalTexts.text("journal.button.details"));
    private final JPanel center = new JPanel(new CardLayout());
    private final JLabel message = new JLabel("", SwingConstants.CENTER);
    private final AppContext.Listener listener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            reload();
        }
    };
    private boolean updatingFilter;
    private int loadGeneration;

    public JournalBattleListDialog(Window owner, AppContext context, Host host) {
        super(owner, ModalityType.MODELESS);
        this.context = context;
        this.host = host;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table = new JTable(tableModel);
        TableRowSorter<JournalBattleTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(JournalBattleTableModel.Column.DATE.ordinal(), SortOrder.DESCENDING)));
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultRenderer(Object.class, new Renderer());
        table.setDefaultRenderer(Integer.class, new Renderer());
        table.setDefaultRenderer(LocalDate.class, new Renderer());
        table.getColumnModel().getColumn(JournalBattleTableModel.Column.OPPONENT.ordinal()).setPreferredWidth(220);
        table.setToolTipText(JournalTexts.text("journal.battles.dropHint"));
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openDetails();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        bindKey(KeyEvent.VK_ENTER, "openDetails", this::openDetails);
        bindKey(KeyEvent.VK_DELETE, "deleteSelected", this::deleteSelected);

        JComponent top = filterPanel();

        JPanel empty = new JPanel(new GridBagLayout());
        JPanel emptyContent = JournalImportDialog.verticalPanel();
        message.setAlignmentX(CENTER_ALIGNMENT);
        emptyContent.add(message);
        JButton emptyImport = new JButton(JournalTexts.text("journal.battles.import"));
        emptyImport.setAlignmentX(CENTER_ALIGNMENT);
        emptyImport.addActionListener(e -> host.importFiles(List.of()));
        emptyContent.add(Box.createVerticalStrut(8));
        emptyContent.add(emptyImport);
        empty.add(emptyContent);
        JScrollPane scroll = new JScrollPane(table);
        center.add(scroll, "table");
        center.add(empty, "empty");

        JPanel root = new JPanel(new BorderLayout());
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(buttonPanel(), BorderLayout.SOUTH);
        setContentPane(root);
        TransferHandler drop = new FileDropHandler(host::importFiles);
        for (JComponent c : List.of(root, table, empty, scroll)) {
            c.setTransferHandler(drop);
        }

        context.addListener(listener);
        setSize(1000, 520);
        setLocationRelativeTo(owner);
        updateButtons();
        reload();
    }

    /** Two rows: season, opponent, period - then status, result, reset and "n of m battles". */
    private JComponent filterPanel() {
        JPanel first = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        first.add(new JLabel(JournalTexts.text("journal.battles.season")));
        first.add(seasonFilter);
        first.add(new JLabel(JournalTexts.text("journal.battles.opponentFilter")));
        first.add(opponentFilter);
        first.add(fromEnabled);
        first.add(fromDate);
        first.add(toEnabled);
        first.add(toDate);
        JPanel second = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        second.add(new JLabel(JournalTexts.text("journal.battles.status")));
        second.add(statusFilter);
        second.add(new JLabel(JournalTexts.text("journal.battles.result")));
        second.add(resultFilter);
        JButton reset = new JButton(JournalTexts.text("journal.battles.resetFilter"));
        second.add(reset);
        second.add(Box.createHorizontalStrut(12));
        second.add(countLabel);
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(first);
        panel.add(second);

        seasonFilter.setRenderer(PlayersStepPanel.textRenderer(this::seasonText));
        statusFilter.setRenderer(PlayersStepPanel.textRenderer(v -> JournalTexts.of("statusFilter", (Enum<?>) v)));
        resultFilter.setRenderer(PlayersStepPanel.textRenderer(v -> JournalTexts.of("resultFilter", (Enum<?>) v)));
        fromDate.setEnabled(false);
        toDate.setEnabled(false);
        seasonFilter.addActionListener(e -> applyFilter());
        statusFilter.addActionListener(e -> applyFilter());
        resultFilter.addActionListener(e -> applyFilter());
        fromEnabled.addActionListener(e -> applyFilter());
        toEnabled.addActionListener(e -> applyFilter());
        fromDate.addChangeListener(e -> applyFilter());
        toDate.addChangeListener(e -> applyFilter());
        opponentFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        reset.addActionListener(e -> {
            updatingFilter = true;
            try {
                seasonFilter.setSelectedIndex(0);
                opponentFilter.setText("");
                fromEnabled.setSelected(false);
                toEnabled.setSelected(false);
                statusFilter.setSelectedItem(BattleListFilter.StatusFilter.ALL);
                resultFilter.setSelectedItem(BattleListFilter.ResultFilter.ALL);
            } finally {
                updatingFilter = false;
            }
            applyFilter();
        });
        return panel;
    }

    private JComponent buttonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        detailsButton.addActionListener(e -> openDetails());
        panel.add(detailsButton);
        JButton importButton = new JButton(JournalTexts.text("journal.battles.import"));
        importButton.addActionListener(e -> host.importFiles(List.of()));
        panel.add(importButton);
        panel.add(Box.createHorizontalStrut(16));
        JButton players = new JButton(JournalTexts.text("journal.action.players"));
        players.addActionListener(e -> host.openPlayers());
        JButton seasons = new JButton(JournalTexts.text("journal.action.seasons"));
        seasons.addActionListener(e -> host.openSeasons());
        JButton mappings = new JButton(JournalTexts.text("journal.action.nameMappings"));
        mappings.addActionListener(e -> host.openNameMappings());
        panel.add(players);
        panel.add(seasons);
        panel.add(mappings);
        return panel;
    }

    private void bindKey(int key, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key, 0), name);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
        table.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    @Override
    public void dispose() {
        context.removeListener(listener);
        super.dispose();
    }

    /** Reads the journal of the open guild again in the background; the selected battles stay selected. */
    public void reload() {
        setTitle(JournalTexts.text("journal.battles.title", context.guild() == null ? "" : context.guild().displayName()));
        Set<Integer> selected = Set.copyOf(selectedBattles().stream().map(BattleSummary::battleId).toList());
        int generation = ++loadGeneration;
        new SwingWorker<JournalBattleTableModel.Data, Void>() {
            private String error;

            @Override
            protected JournalBattleTableModel.Data doInBackground() {
                try {
                    return JournalBattleTableModel.load(context.journal());
                } catch (Exception e) {
                    Logger.logException("Could not read the journal", e);
                    error = JournalImportDialog.errorText(e);
                    return JournalBattleTableModel.Data.EMPTY;
                }
            }

            @Override
            protected void done() {
                if (generation != loadGeneration) {
                    return;
                }
                try {
                    show(get(), error, selected);
                } catch (Exception e) {
                    Logger.logException("Could not show the battle list", e);
                }
            }
        }.execute();
    }

    private void show(JournalBattleTableModel.Data data, String error, Set<Integer> selected) {
        message.setText(error != null ? error : JournalTexts.text("journal.battles.empty"));
        tableModel.setData(data);
        updatingFilter = true;
        try {
            DefaultComboBoxModel<Object> seasons = new DefaultComboBoxModel<>();
            seasons.addElement(JournalTexts.text("journal.battles.allSeasons"));
            data.seasons().forEach(seasons::addElement);
            seasonFilter.setModel(seasons);
            for (int i = 0; i < seasons.getSize(); i++) {
                if (seasons.getElementAt(i) instanceof Season s && tableModel.seasonFilter() != null
                        && s.id() == tableModel.seasonFilter()) {
                    seasonFilter.setSelectedIndex(i);
                }
            }
        } finally {
            updatingFilter = false;
        }
        for (int battleId : selected) {
            int row = tableModel.rowOf(battleId);
            if (row >= 0) {
                int view = table.convertRowIndexToView(row);
                table.getSelectionModel().addSelectionInterval(view, view);
            }
        }
        countLabel.setText(tableModel.countText());
        boolean empty = data.battles().isEmpty();
        ((CardLayout) center.getLayout()).show(center, empty ? "empty" : "table");
        updateButtons();
    }

    private void applyFilter() {
        if (updatingFilter) {
            return;
        }
        Object season = seasonFilter.getSelectedItem();
        tableModel.setFilter(new BattleListFilter(
                season instanceof Season s ? s.id() : null,
                opponentFilter.getText(),
                fromEnabled.isSelected() ? JournalImportDialog.toLocalDate((java.util.Date) fromDate.getValue()) : null,
                toEnabled.isSelected() ? JournalImportDialog.toLocalDate((java.util.Date) toDate.getValue()) : null,
                (BattleListFilter.StatusFilter) statusFilter.getSelectedItem(),
                (BattleListFilter.ResultFilter) resultFilter.getSelectedItem()));
        fromDate.setEnabled(fromEnabled.isSelected());
        toDate.setEnabled(toEnabled.isSelected());
        countLabel.setText(tableModel.countText());
        updateButtons();
    }

    private List<BattleSummary> selectedBattles() {
        List<BattleSummary> battles = new ArrayList<>();
        if (table == null) {
            return battles;
        }
        for (int viewRow : table.getSelectedRows()) {
            battles.add(tableModel.battle(table.convertRowIndexToModel(viewRow)));
        }
        return battles;
    }

    private void updateButtons() {
        detailsButton.setEnabled(selectedBattles().size() == 1);
    }

    private void openDetails() {
        List<BattleSummary> battles = selectedBattles();
        if (battles.size() == 1) {
            host.openDetail(battles.get(0).battleId());
        }
    }

    private void deleteSelected() {
        host.operations().deleteBattles(this, selectedBattles(), null);
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && !table.isRowSelected(row)) {
            table.setRowSelectionInterval(row, row);
        }
        List<BattleSummary> battles = selectedBattles();
        if (battles.isEmpty()) {
            return;
        }
        boolean single = battles.size() == 1;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem details = new JMenuItem(JournalTexts.text("journal.button.details"));
        details.setEnabled(single);
        details.addActionListener(ev -> openDetails());
        menu.add(details);
        JMenuItem reparse = new JMenuItem(JournalTexts.text("journal.action.reparse"));
        reparse.addActionListener(ev -> host.operations().reparse(this,
                battles.stream().map(BattleSummary::battleId).toList()));
        menu.add(reparse);
        if (single) {
            menu.add(host.operations().saveCsvMenu(this, battles.get(0)));
        }
        JMenuItem season = new JMenuItem(JournalTexts.text("journal.action.assignSeason"));
        season.setEnabled(single);
        season.addActionListener(ev -> host.operations().assignSeason(this, battles.get(0)));
        menu.add(season);
        menu.addSeparator();
        JMenuItem delete = new JMenuItem(JournalTexts.text("journal.action.delete"));
        delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        delete.addActionListener(ev -> deleteSelected());
        menu.add(delete);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private String seasonText(Object item) {
        return item instanceof Season s ? JournalTexts.seasonText(s) : String.valueOf(item);
    }

    /** Formats dates and numbers, colors the rows by result. */
    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Object shown = value;
            if (value instanceof LocalDate date) {
                shown = JournalTexts.date(date);
            } else if (value instanceof Integer number) {
                shown = column == JournalBattleTableModel.Column.RANKING_POINTS.ordinal()
                        ? JournalTexts.rankingPoints(number) : JournalTexts.number(number);
            }
            super.getTableCellRendererComponent(table, shown, selected, focus, row, column);
            setHorizontalAlignment(value instanceof Integer ? RIGHT : LEFT);
            if (!selected) {
                BattleSummary battle = tableModel.battle(table.convertRowIndexToModel(row));
                Color tint = battle.status() == BattleStatus.RUNNING ? JournalSwing.RUNNING
                        : battle.result() == BattleResult.WIN ? JournalSwing.GOOD
                        : battle.result() == BattleResult.LOSS ? JournalSwing.BAD : null;
                setBackground(JournalSwing.blend(table.getBackground(), tint));
            }
            return this;
        }
    }

    /** Accepts dropped files and starts an import with them. */
    private static final class FileDropHandler extends TransferHandler {
        private final Consumer<List<Path>> onImport;

        FileDropHandler(Consumer<List<Path>> onImport) {
            this.onImport = onImport;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                @SuppressWarnings("unchecked")
                List<File> dropped = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> files = dropped.stream().filter(File::isFile).map(File::toPath).collect(Collectors.toList());
                if (files.isEmpty()) {
                    return false;
                }
                SwingUtilities.invokeLater(() -> onImport.accept(files));
                return true;
            } catch (Exception e) {
                Logger.logException("Could not accept the dropped files", e);
                return false;
            }
        }
    }
}
