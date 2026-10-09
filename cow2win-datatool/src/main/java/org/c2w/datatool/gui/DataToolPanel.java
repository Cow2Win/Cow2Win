package org.c2w.datatool.gui;

import org.c2w.datatool.data.ColumnSpec;
import org.c2w.datatool.data.ColumnType;
import org.c2w.datatool.data.DataSet;
import org.c2w.datatool.data.Fields;
import org.c2w.datatool.data.Language;
import org.c2w.datatool.data.Problem;
import org.c2w.datatool.data.Row;
import org.c2w.datatool.data.SaveOptions;
import org.c2w.datatool.data.SaveResult;
import org.c2w.datatool.data.Schema;
import org.c2w.datatool.data.TableKind;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content of the tool window: tool bar, one tab per data file (all the same generic table,
 * configured by {@link Schema}) and the problem list. Kept separate from the frame so it
 * can be built headless in tests.
 */
public final class DataToolPanel extends JPanel {

    private static final String REMINDER =
            "Run mvn test (LanguageFilesConsistencyTest, BattleLogGameNamesTest, BestPossibleLineupAlgorithmTest)";

    private DataSet data;
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<TableKind, JTable> tables = new EnumMap<>(TableKind.class);
    private final DefaultListModel<Problem> problems = new DefaultListModel<>();
    private final JList<Problem> problemList = new JList<>(problems);
    private final JLabel problemSummary = new JLabel(" ");

    public DataToolPanel(DataSet data) {
        super(new BorderLayout());
        this.data = data;
        add(toolBar(), BorderLayout.NORTH);

        problemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        problemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && problemList.getSelectedValue() != null) {
                    jumpTo(problemList.getSelectedValue());
                }
            }
        });
        JPanel problemPanel = new JPanel(new BorderLayout());
        problemSummary.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        problemPanel.add(problemSummary, BorderLayout.NORTH);
        problemPanel.add(new JScrollPane(problemList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, problemPanel);
        split.setResizeWeight(0.8);
        split.setDividerLocation(560);
        add(split, BorderLayout.CENTER);
        buildTabs();
    }

    // --- read access (tests, frame) ---

    public DataSet data() {
        return data;
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    public JTable table(TableKind kind) {
        return tables.get(kind);
    }

    // --- building ---

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button("Save", this::save));
        bar.add(button("Reload", this::reload));
        bar.add(button("Validate", this::validateData));
        bar.addSeparator();
        bar.add(button("Add row", this::addRow));
        bar.add(button("Delete row", this::deleteRow));
        return bar;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void buildTabs() {
        int selected = Math.max(0, tabs.getSelectedIndex());
        tabs.removeAll();
        tables.clear();
        DataCellRenderer renderer = new DataCellRenderer(data);
        for (TableKind kind : TableKind.values()) {
            List<ColumnSpec> columns = Schema.columns(kind, data);
            DataTableModel model = new DataTableModel(data, kind, columns, this::refreshTitles);
            JTable table = new JTable(model);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getTableHeader().setReorderingAllowed(false);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            boolean withImages = columns.stream().anyMatch(c -> c.type() == ColumnType.IMAGE);
            table.setRowHeight(withImages ? DataCellRenderer.ICON_SIZE + 4 : 24);
            for (int i = 0; i < columns.size(); i++) {
                ColumnSpec spec = columns.get(i);
                TableColumn column = table.getColumnModel().getColumn(i);
                if (spec.type() != ColumnType.BOOLEAN) {
                    column.setCellRenderer(renderer);
                }
                column.setPreferredWidth(width(spec));
                if (spec.type() == ColumnType.ENUM || spec.type() == ColumnType.MARK
                        || spec.type() == ColumnType.ID_REF) {
                    column.setCellEditor(new ComboEditor(spec));
                }
                if (spec.type() == ColumnType.MARK) {
                    column.setHeaderValue("<html>" + spec.header() + "</html>");
                }
            }
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int row = table.rowAtPoint(e.getPoint());
                        int column = table.columnAtPoint(e.getPoint());
                        if (row >= 0 && column >= 0) {
                            editInDialog(table, row, column);
                        }
                    }
                }
            });
            tables.put(kind, table);
            tabs.addTab(kind.title(), new JScrollPane(table));
        }
        tabs.setSelectedIndex(Math.min(selected, tabs.getTabCount() - 1));
        refreshTitles();
    }

    private static int width(ColumnSpec spec) {
        return switch (spec.type()) {
            case IMAGE -> 170;
            case ID_LIST -> 260;
            case MARK, INTEGER, BOOLEAN -> 90;
            case ENUM -> 150;
            default -> 160;
        };
    }

    private void refreshTitles() {
        for (TableKind kind : TableKind.values()) {
            tabs.setTitleAt(kind.ordinal(), kind.title() + (data.table(kind).isDirty() ? " *" : ""));
        }
    }

    private TableKind currentKind() {
        return TableKind.values()[tabs.getSelectedIndex()];
    }

    private void stopEditing() {
        for (JTable table : tables.values()) {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
        }
    }

    // --- id lists and avatars ---

    private void editInDialog(JTable table, int viewRow, int viewColumn) {
        DataTableModel model = (DataTableModel) table.getModel();
        int rowIndex = table.convertRowIndexToModel(viewRow);
        ColumnSpec spec = model.column(table.convertColumnIndexToModel(viewColumn));
        Row row = model.row(rowIndex);
        if (!spec.isEditable(row)) {
            return;
        }
        if (spec.type() == ColumnType.ID_LIST) {
            List<String> options = spec.ref() == null ? spec.options() : data.table(spec.ref()).ids();
            String hint = spec.maxCount() == Integer.MAX_VALUE ? "" : spec.minCount() + "-" + spec.maxCount();
            List<String> chosen = IdListDialog.show(this, spec.header(), options, row.getList(spec.field()),
                    id -> spec.ref() == null ? id : data.displayName(spec.ref(), id), spec.orderable(), hint);
            if (chosen != null) {
                model.update(rowIndex, spec.field(), chosen);
            }
        } else if (spec.type() == ColumnType.IMAGE) {
            chooseImage(model, rowIndex);
        }
    }

    private void chooseImage(DataTableModel model, int rowIndex) {
        TableKind kind = model.kind();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        String fileName = source.getFileName().toString();
        boolean copy = true;
        try {
            Path target = data.root().resolve("images").resolve(kind.imageFolder()).resolve(fileName);
            boolean sameFile = data.imageExistsInFolder(kind, fileName)
                    && java.nio.file.Files.isSameFile(source, target);
            if (data.imageExistsInFolder(kind, fileName) && !sameFile) {
                Object[] choices = {"Overwrite", "Use existing file", "Cancel"};
                int answer = JOptionPane.showOptionDialog(this, "images/" + kind.imageFolder() + "/" + fileName
                                + " exists already.\nOverwrite it with the chosen file on save?", "Image exists",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[2]);
                if (answer == 2 || answer < 0) {
                    return;
                }
                copy = answer == 0;
            }
            data.setImage(kind, model.row(rowIndex), source, copy);
            model.fireTableRowsUpdated(rowIndex, rowIndex);
            refreshTitles();
        } catch (IOException e) {
            error("Could not use the image: " + e.getMessage());
        }
    }

    // --- tool bar actions ---

    private void addRow() {
        stopEditing();
        TableKind kind = currentKind();
        data.addRow(kind);
        JTable table = tables.get(kind);
        DataTableModel model = (DataTableModel) table.getModel();
        int index = model.getRowCount() - 1;
        model.fireTableRowsInserted(index, index);
        int column = Math.max(0, model.columnOf(kind.isCatalog() ? Language.EN.field() : kind.keyField()));
        table.changeSelection(index, column, false, false);
        table.editCellAt(index, column);
        table.requestFocusInWindow();
        refreshTitles();
    }

    private void deleteRow() {
        stopEditing();
        TableKind kind = currentKind();
        JTable table = tables.get(kind);
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            info("Select the row to delete first.");
            return;
        }
        DataTableModel model = (DataTableModel) table.getModel();
        int index = table.convertRowIndexToModel(viewRow);
        Row row = model.row(index);
        Object key = row.get(kind.keyField());
        String message = "Delete " + (key == null ? "this new row" : key) + " from " + kind.title() + "?";
        if (!row.isNew() && kind.keyField().equals(Fields.ID)) {
            message += "\n\nUser workspaces (guild files, lineups, journal) may use this id."
                    + "\nReferences in other files are not removed - the check reports them as errors."
                    + "\nImage files are never deleted.";
        }
        int answer = JOptionPane.showConfirmDialog(this, message, "Delete row", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        data.deleteRow(kind, row);
        model.fireTableRowsDeleted(index, index);
        refreshTitles();
    }

    private void validateData() {
        stopEditing();
        List<Problem> found = data.validate();
        showProblems(found);
        if (found.isEmpty()) {
            info("No problems found.");
        }
    }

    private void showProblems(List<Problem> found) {
        problems.clear();
        found.stream().filter(Problem::isError).forEach(problems::addElement);
        found.stream().filter(p -> !p.isError()).forEach(problems::addElement);
        long errors = found.stream().filter(Problem::isError).count();
        problemSummary.setText(errors + " error(s), " + (found.size() - errors)
                + " warning(s) - double-click a problem to jump to the cell");
    }

    /** Selects the tab, row and cell of a problem. */
    void jumpTo(Problem problem) {
        tabs.setSelectedIndex(problem.table().ordinal());
        JTable table = tables.get(problem.table());
        if (problem.row() < 0 || problem.row() >= table.getRowCount()) {
            return;
        }
        DataTableModel model = (DataTableModel) table.getModel();
        int column = problem.field() == null ? -1 : model.columnOf(problem.field());
        int viewRow = table.convertRowIndexToView(problem.row());
        int viewColumn = column < 0 ? 0 : table.convertColumnIndexToView(column);
        table.changeSelection(viewRow, viewColumn, false, false);
        table.requestFocusInWindow();
    }

    private void save() {
        stopEditing();
        List<Problem> found = data.validate();
        showProblems(found);
        List<Problem> errors = found.stream().filter(Problem::isError).toList();
        if (!errors.isEmpty()) {
            jumpTo(errors.get(0));
            problemList.setSelectedIndex(0);
            error("Save refused: " + errors.size() + " error(s) - see the problem list.");
            return;
        }
        if (!data.isDirty()) {
            info("Nothing to save.");
            return;
        }
        Set<TableKind> cowScore = EnumSet.noneOf(TableKind.class);
        for (TableKind catalog : List.of(TableKind.HEROES, TableKind.TITANS, TableKind.PETS, TableKind.WAR_FLAGS)) {
            List<String> ids = data.newEntriesWithoutCowScore(catalog);
            if (!ids.isEmpty()) {
                boolean defaultYes = catalog == TableKind.PETS || catalog == TableKind.WAR_FLAGS;
                if (askYesNo("Also create entries in " + catalog.cowScoreOfCatalog().title() + " for: "
                        + String.join(", ", ids) + "?", "New " + catalog.title(), defaultYes)) {
                    cowScore.add(catalog);
                }
            }
        }
        boolean setDataVersion = data.catalogFilesChanged()
                && askYesNo("Set dataVersion to today?", "catalog-version.json", true);
        try {
            SaveResult result = data.save(new SaveOptions(cowScore, setDataVersion, LocalDate.now()));
            if (!result.saved()) {
                showProblems(result.errors());
                error("Save refused: " + result.errors().size() + " error(s).");
                return;
            }
            reloadFromDisk();
            showProblems(found.stream().filter(p -> !p.isError()).toList());
            info(result.written().isEmpty() ? "Nothing was written - no file content changed."
                    : "Written:\n  " + String.join("\n  ", result.written()) + "\n\n" + REMINDER);
        } catch (IOException e) {
            error("Saving failed: " + e.getMessage() + "\nSome files may already have been written - check git status.");
        }
    }

    private void reload() {
        stopEditing();
        if (data.isDirty() && !askYesNo("Discard the unsaved changes and reload?", "Reload", false)) {
            return;
        }
        reloadFromDisk();
        problems.clear();
        problemSummary.setText(" ");
    }

    private void reloadFromDisk() {
        try {
            data = DataSet.load(data.root());
            buildTabs();
        } catch (IOException e) {
            error("Reloading failed: " + e.getMessage());
        }
    }

    /** Asks before closing with unsaved changes; true if the window may close. */
    public boolean confirmClose() {
        stopEditing();
        return !data.isDirty() || askYesNo("Discard the unsaved changes and close?", "Close", false);
    }

    // --- dialogs ---

    private boolean askYesNo(String message, String title, boolean defaultYes) {
        Object[] choices = {"Yes", "No"};
        int answer = JOptionPane.showOptionDialog(this, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, choices, choices[defaultYes ? 0 : 1]);
        return answer == 0;
    }

    private void info(String message) {
        JOptionPane.showMessageDialog(this, message, "Data tool", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Data tool", JOptionPane.ERROR_MESSAGE);
    }

    /** Combo box editor for enum values, marks and catalog ids (the latter read when editing starts). */
    private final class ComboEditor extends DefaultCellEditor {

        private final ColumnSpec spec;

        ComboEditor(ColumnSpec spec) {
            super(new JComboBox<String>());
            this.spec = spec;
            setClickCountToStart(1);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
                                                     int column) {
            JComboBox<String> combo = (JComboBox<String>) getComponent();
            combo.removeAllItems();
            List<String> items = new ArrayList<>();
            if (!spec.required()) {
                items.add("");
            }
            items.addAll(spec.ref() == null ? spec.options() : data.table(spec.ref()).ids());
            items.forEach(combo::addItem);
            return super.getTableCellEditorComponent(table, value == null ? "" : value, isSelected, row, column);
        }
    }
}
