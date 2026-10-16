package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.Season;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.common.InfoArea;
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
 * The battle list of the open guild's journal (not modal): season, date,
 * opponent, result, ranking points, own/opponent points, stored logs - newest
 * first, sortable, filterable (season, opponent, result; the filter lives in
 * {@link JournalBattleTableModel}). Victories, defeats and the running battle
 * are tinted over the whole row. The season of a battle is
 * computed from its date ({@link org.c2w.data.journal.SeasonCalendar}); the
 * season filter offers only the seasons that contain a stored battle. The
 * toolbar in the north holds the icon buttons "Import …", "Details …" and
 * "Delete" (all selected battles, like the Delete key) and
 * the filters (season, result, opponent); players and name mappings are only in
 * the main menu. Below the toolbar an information area explains what the list
 * is for; the information panel in the south shows "n of m battles" and the sums
 * of ranking points, own and opponent points of the shown battles. Double click
 * / Enter / "Details …" opens the battle detail; the delete button, the context
 * menu and the Delete key delete all selected battles (e.g. a whole season:
 * filter it, select all, Delete).
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

        JournalOperations operations();
    }

    private static final String ICON_IMPORT = "/images/app/add.png";
    private static final String ICON_DETAILS = "/images/app/arrows-in-line-horizontal.png";
    private static final String ICON_DELETE = "/images/app/delete.png";
    private static final int TOOLBAR_ICON_SIZE = 20;
    private static final int INFO_ROWS = 2;

    private final AppContext context;
    private final Host host;
    private final JournalBattleTableModel tableModel = new JournalBattleTableModel();
    private final JTable table;
    private final JComboBox<Object> seasonFilter = new JComboBox<>();
    private final JTextField opponentFilter = new JTextField(12);
    private final JComboBox<BattleListFilter.ResultFilter> resultFilter =
            new JComboBox<>(BattleListFilter.ResultFilter.values());
    private final JLabel countLabel = new JLabel(" ");
    private final JLabel rankingPointsTotal = new JLabel(" ");
    private final JLabel ownPointsTotal = new JLabel(" ");
    private final JLabel opponentPointsTotal = new JLabel(" ");
    private final FlatButton detailsButton =
            new FlatButton(IconLoader.iconFor(ICON_DETAILS, TOOLBAR_ICON_SIZE, AppColors.ACCENT));
    private final FlatButton deleteButton =
            new FlatButton(IconLoader.iconFor(ICON_DELETE, TOOLBAR_ICON_SIZE, AppColors.ERROR));
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
        // One renderer for every column: the look and feel installs its own (untinted) renderers
        // for String/Integer/..., so class-based defaults would leave some cells uncolored.
        Renderer renderer = new Renderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        table.setIntercellSpacing(new Dimension(0, 0)); // no gaps between the tinted cells
        table.getColumnModel().getColumn(JournalBattleTableModel.Column.OPPONENT.ordinal()).setPreferredWidth(220);
        int seasonWidth = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont())
                .stringWidth(table.getColumnName(JournalBattleTableModel.Column.SEASON.ordinal())) + 24;
        table.getColumnModel().getColumn(JournalBattleTableModel.Column.SEASON.ordinal()).setPreferredWidth(Math.max(40, seasonWidth));
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

        JComponent top = buildNorth();

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
        root.add(buildInfoPanel(), BorderLayout.SOUTH);
        setContentPane(root);
        TransferHandler drop = new FileDropHandler(host::importFiles);
        for (JComponent c : List.of(root, table, empty, scroll)) {
            c.setTransferHandler(drop);
        }

        context.addListener(listener);
        // High enough for the information area and the totals panel next to about ten table rows.
        setSize(1000, 625);
        setLocationRelativeTo(owner);
        updateButtons();
        updateInfo();
        reload();
    }

    /**
     * One row like the other dialogs' toolbars: import and details (icon buttons),
     * then the filters season, result, opponent.
     */
    private JComponent buildToolbar() {
        detailsButton.setToolTipText(JournalTexts.text("journal.button.details"));
        // The look and feel does not gray out a recolored icon by itself.
        detailsButton.setDisabledIcon(IconLoader.iconFor(ICON_DETAILS, TOOLBAR_ICON_SIZE, AppColors.MUTED));
        detailsButton.addActionListener(e -> openDetails());
        deleteButton.setToolTipText(JournalTexts.text("journal.action.delete"));
        deleteButton.setDisabledIcon(IconLoader.iconFor(ICON_DELETE, TOOLBAR_ICON_SIZE, AppColors.MUTED));
        deleteButton.addActionListener(e -> deleteSelected());
        FlatButton importButton = new FlatButton(IconLoader.iconFor(ICON_IMPORT, TOOLBAR_ICON_SIZE, AppColors.ACCENT));
        importButton.setToolTipText(JournalTexts.text("journal.battles.import"));
        importButton.addActionListener(e -> host.importFiles(List.of()));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.add(importButton);
        panel.add(detailsButton);
        panel.add(deleteButton);
        panel.add(Box.createHorizontalStrut(16));
        panel.add(new JLabel(JournalTexts.text("journal.battles.season")));
        panel.add(seasonFilter);
        panel.add(new JLabel(JournalTexts.text("journal.battles.result")));
        panel.add(resultFilter);
        panel.add(new JLabel(JournalTexts.text("journal.battles.opponentFilter")));
        panel.add(opponentFilter);

        seasonFilter.setRenderer(PlayersStepPanel.textRenderer(this::seasonText));
        resultFilter.setRenderer(PlayersStepPanel.textRenderer(v -> JournalTexts.of("resultFilter", (Enum<?>) v)));
        seasonFilter.addActionListener(e -> applyFilter());
        resultFilter.addActionListener(e -> applyFilter());
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
        return panel;
    }

    /** The toolbar with the information area below it. */
    private JComponent buildNorth() {
        InfoArea info = new InfoArea(INFO_ROWS);
        info.setText(JournalTexts.text("journal.battles.info"));
        JPanel north = new JPanel(new BorderLayout());
        north.add(buildToolbar(), BorderLayout.NORTH);
        north.add(InfoArea.framed(info), BorderLayout.CENTER);
        return north;
    }

    /** One row: "n of m battles" and the sums of the shown battles. */
    private JComponent buildInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JLabel label : List.of(countLabel, rankingPointsTotal, ownPointsTotal, opponentPointsTotal)) {
            if (label != countLabel) {
                panel.add(new JLabel("·"));
            }
            panel.add(label);
        }
        return panel;
    }

    /** Count and sums of the shown battles - after every filter change and reload. */
    private void updateInfo() {
        countLabel.setText(tableModel.countText());
        JournalBattleTableModel.Totals totals = tableModel.shownTotals();
        rankingPointsTotal.setText(JournalTexts.text("journal.battles.total.rankingPoints",
                JournalTexts.rankingPointsTotal(totals.rankingPoints())));
        ownPointsTotal.setText(JournalTexts.text("journal.battles.total.ownPoints",
                JournalTexts.number(totals.ownPoints())));
        opponentPointsTotal.setText(JournalTexts.text("journal.battles.total.opponentPoints",
                JournalTexts.number(totals.opponentPoints())));
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
                        && tableModel.seasonFilter() == s.number()) {
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
        updateInfo();
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
                season instanceof Season s ? s.number() : null,
                opponentFilter.getText(),
                (BattleListFilter.ResultFilter) resultFilter.getSelectedItem()));
        updateInfo();
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
        int selected = selectedBattles().size();
        detailsButton.setEnabled(selected == 1);
        deleteButton.setEnabled(selected > 0);
    }

    private void openDetails() {
        List<BattleSummary> battles = selectedBattles();
        if (battles.size() == 1) {
            host.openDetail(battles.get(0).battleId());
        }
    }

    private void deleteSelected() {
        host.operations().deleteBattles(this, selectedBattles());
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
        JPopupMenu menu = new JPopupMenu();
        JMenuItem details = new JMenuItem(JournalTexts.text("journal.button.details"));
        details.setEnabled(battles.size() == 1);
        details.addActionListener(ev -> openDetails());
        menu.add(details);
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

    /**
     * The renderer of every column: formats dates and numbers, colors the whole row
     * by result (always opaque - the look and feel paints table cells transparent).
     * Season and logs are centered, the points right-aligned, date, opponent and
     * result left-aligned; the headers keep their own alignment.
     */
    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            JournalBattleTableModel.Column col = JournalBattleTableModel.Column.values()[table.convertColumnIndexToModel(column)];
            Object shown = value;
            if (value instanceof LocalDate date) {
                shown = JournalTexts.date(date);
            } else if (value instanceof Integer number) {
                shown = col == JournalBattleTableModel.Column.RANKING_POINTS ? JournalTexts.rankingPoints(number)
                        : JournalTexts.number(number);
            }
            super.getTableCellRendererComponent(table, shown, selected, focus, row, column);
            setOpaque(true);
            setHorizontalAlignment(alignment(col));
            if (!selected) {
                BattleSummary battle = tableModel.battle(table.convertRowIndexToModel(row));
                Color tint = battle.status() == BattleStatus.RUNNING ? JournalSwing.RUNNING
                        : battle.result() == BattleResult.WIN ? JournalSwing.GOOD
                        : battle.result() == BattleResult.LOSS ? JournalSwing.BAD : null;
                setBackground(JournalSwing.blend(table.getBackground(), tint));
            }
            return this;
        }

        private static int alignment(JournalBattleTableModel.Column col) {
            return switch (col) {
                case SEASON, LOGS -> CENTER;
                case RANKING_POINTS, OWN_POINTS, OPPONENT_POINTS -> RIGHT;
                case DATE, OPPONENT, RESULT -> LEFT;
            };
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
