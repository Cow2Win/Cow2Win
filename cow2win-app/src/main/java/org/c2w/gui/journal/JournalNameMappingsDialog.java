package org.c2w.gui.journal;

import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.db.NameMapping;
import org.c2w.service.AppContext;
import org.c2w.service.JournalMaintenanceService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The manual name mappings of the journal (not modal): kind, raw name and the
 * catalog entry it maps to (display name and image). Change (choose from the
 * catalog of that kind) and delete. Changes apply to future imports; stored
 * battles are updated by "parse again" - offered right here for all battles.
 */
public final class JournalNameMappingsDialog extends JDialog {

    private final AppContext context;
    private final JournalMaintenanceService service;
    private final JournalOperations operations;
    private final MappingTableModel tableModel = new MappingTableModel();
    private final JTable table;
    private final JButton changeButton = new JButton(JournalTexts.text("journal.mappings.change"));
    private final JButton deleteButton = new JButton(JournalTexts.text("journal.mappings.delete"));
    private final JButton reparseButton = new JButton(JournalTexts.text("journal.action.reparseAll"));
    private final JLabel hint = new JLabel(" ");
    private boolean hasJournal;
    private final AppContext.Listener listener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            reload();
        }
    };

    public JournalNameMappingsDialog(Window owner, AppContext context, JournalOperations operations) {
        super(owner, ModalityType.MODELESS);
        this.context = context;
        this.service = operations.service();
        this.operations = operations;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table = JournalSwing.table(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(JournalCatalog.ICON_SIZE + 4);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                NameMapping m = tableModel.mapping(row);
                setIcon(JournalCatalog.icon(context.catalog(), m.kind(), m.catalogId()));
                return this;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        changeButton.addActionListener(e -> selected().ifPresent(this::change));
        deleteButton.addActionListener(e -> selected().ifPresent(this::delete));
        reparseButton.addActionListener(e -> operations.reparse(this, null));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttons.add(changeButton);
        buttons.add(deleteButton);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(reparseButton);
        JPanel south = new JPanel(new BorderLayout());
        south.add(hint, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        context.addListener(listener);
        setSize(760, 420);
        setLocationRelativeTo(owner);
        updateButtons();
        reload();
    }

    @Override
    public void dispose() {
        context.removeListener(listener);
        super.dispose();
    }

    /** Reads the mappings again (background). */
    public void reload() {
        setTitle(JournalTexts.text("journal.mappings.title", context.guild() == null ? "" : context.guild().displayName()));
        JournalSwing.background(this, () -> {
            Optional<JournalRepository> repo = service.repository();
            return repo.isEmpty() ? Optional.<List<NameMapping>>empty() : Optional.of(repo.get().listNameMappings());
        }, mappings -> {
            hasJournal = mappings.isPresent();
            tableModel.set(mappings.orElse(List.of()));
            if (!hasJournal) {
                hint.setText(JournalTexts.text("journal.noJournal"));
            } else if (tableModel.getRowCount() == 0 && hint.getText().isBlank()) {
                hint.setText(JournalTexts.text("journal.mappings.empty"));
            }
            updateButtons();
        });
    }

    private Optional<NameMapping> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(tableModel.mapping(table.convertRowIndexToModel(row)));
    }

    private void updateButtons() {
        boolean selection = hasJournal && selected().isPresent();
        changeButton.setEnabled(selection);
        deleteButton.setEnabled(selection);
        reparseButton.setEnabled(hasJournal);
    }

    private void changed() {
        hint.setText(JournalTexts.text("journal.mappings.hint"));
        reload();
    }

    private void change(NameMapping mapping) {
        List<NamesStepPanel.CatalogChoice> all = JournalCatalog.choices(context.catalog(), mapping.kind());
        DefaultListModel<NamesStepPanel.CatalogChoice> listModel = new DefaultListModel<>();
        all.forEach(listModel::addElement);
        JList<NamesStepPanel.CatalogChoice> list = new JList<>(listModel);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(l, value, index, selected, focus);
                if (value instanceof NamesStepPanel.CatalogChoice choice) {
                    setIcon(choice.icon());
                }
                return this;
            }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        all.stream().filter(c -> c.id().equals(mapping.catalogId())).findFirst().ifPresent(c -> list.setSelectedValue(c, true));
        JTextField search = new JTextField(16);
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                NamesStepPanel.CatalogChoice selected = list.getSelectedValue();
                String wanted = search.getText().strip().toLowerCase(Locale.ROOT);
                listModel.clear();
                for (NamesStepPanel.CatalogChoice c : all) {
                    if (wanted.isEmpty() || c.label().toLowerCase(Locale.ROOT).contains(wanted)
                            || c.id().toLowerCase(Locale.ROOT).contains(wanted)) {
                        listModel.addElement(c);
                    }
                }
                if (selected != null) {
                    list.setSelectedValue(selected, true);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JPanel north = JournalImportDialog.verticalPanel();
        north.add(new JLabel(JournalTexts.text("journal.mappings.changeQuestion",
                JournalTexts.visibleSpaces(mapping.rawName()), JournalTexts.of("nameKind", mapping.kind()))));
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        searchRow.add(new JLabel(JournalTexts.text("journal.mappings.search")));
        searchRow.add(search);
        north.add(searchRow);
        panel.add(north, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(360, 320));
        panel.add(scroll, BorderLayout.CENTER);
        String title = JournalTexts.text("journal.mappings.changeTitle");
        if (JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION || list.getSelectedValue() == null) {
            return;
        }
        String catalogId = list.getSelectedValue().id();
        JournalSwing.background(this, () -> {
            service.putNameMapping(mapping.kind(), mapping.rawName(), catalogId);
            return catalogId;
        }, done -> changed());
    }

    private void delete(NameMapping mapping) {
        if (!JournalSwing.confirm(this, JournalTexts.text("journal.mappings.deleteQuestion",
                        JournalTexts.visibleSpaces(mapping.rawName()), JournalCatalog.label(mapping.kind(), mapping.catalogId())),
                JournalTexts.text("journal.mappings.delete"))) {
            return;
        }
        JournalSwing.background(this, () -> service.deleteNameMapping(mapping.kind(), mapping.rawName()),
                done -> changed());
    }

    /** Rows: kind, raw name, target (display name and id). */
    private static final class MappingTableModel extends AbstractTableModel {
        private static final String[] KEYS = {"kind", "raw", "target"};
        private List<NameMapping> mappings = List.of();

        void set(List<NameMapping> newMappings) {
            mappings = newMappings;
            fireTableDataChanged();
        }

        NameMapping mapping(int row) {
            return mappings.get(row);
        }

        @Override
        public int getRowCount() {
            return mappings.size();
        }

        @Override
        public int getColumnCount() {
            return KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            return JournalTexts.text("journal.mappings.col." + KEYS[column]);
        }

        @Override
        public Object getValueAt(int row, int column) {
            NameMapping m = mappings.get(row);
            return switch (column) {
                case 0 -> JournalTexts.of("nameKind", m.kind());
                case 1 -> JournalTexts.visibleSpaces(m.rawName());
                default -> JournalCatalog.label(m.kind(), m.catalogId()) + " (" + m.catalogId() + ")";
            };
        }
    }
}
