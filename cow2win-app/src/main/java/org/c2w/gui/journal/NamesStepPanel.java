package org.c2w.gui.journal;

import org.c2w.data.journal.NameMappingKind;
import org.c2w.service.journal.UnknownNameQuestion;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Unknown names step: per name without catalog id a chooser with the candidates
 * on top and then the whole catalog of that kind (display name and image),
 * filterable by a search field. Default: leave unknown - only the readability of
 * the journal depends on it, never the guild.
 */
final class NamesStepPanel extends JPanel {

    /** A catalog entry offered for a name; {@code id == null} = leave unknown. */
    record CatalogChoice(String id, String label, Icon icon) {
        @Override
        public String toString() {
            return label;
        }
    }

    NamesStepPanel(ImportWizardModel model, Function<NameMappingKind, List<CatalogChoice>> catalog, Runnable onChange) {
        super(new BorderLayout(0, 8));
        add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.names.hint")), BorderLayout.NORTH);

        JPanel list = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        int y = 0;
        for (UnknownNameQuestion q : model.plan().unknownNames()) {
            List<CatalogChoice> all = choices(q, catalog.apply(q.kind()));
            JComboBox<CatalogChoice> combo = new JComboBox<>(all.toArray(new CatalogChoice[0]));
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                              boolean focus) {
                    super.getListCellRendererComponent(l, value, index, selected, focus);
                    if (value instanceof CatalogChoice choice) {
                        setIcon(choice.icon());
                    }
                    return this;
                }
            });
            select(combo, model.nameAnswer(q.id()));
            combo.addActionListener(e -> {
                CatalogChoice choice = (CatalogChoice) combo.getSelectedItem();
                model.setNameAnswer(q.id(), choice == null ? null : choice.id());
                onChange.run();
            });
            JTextField search = new JTextField(10);
            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    filter(combo, all, search.getText(), model.nameAnswer(q.id()));
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    filter(combo, all, search.getText(), model.nameAnswer(q.id()));
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    filter(combo, all, search.getText(), model.nameAnswer(q.id()));
                }
            });

            c.gridy = y++;
            c.gridx = 0;
            list.add(new JLabel(JournalTexts.of("nameKind", q.kind())), c);
            c.gridx = 1;
            list.add(JournalImportDialog.boldLabel(JournalTexts.visibleSpaces(q.rawName())), c);
            c.gridx = 2;
            list.add(new JLabel(JournalTexts.text("journal.names.occurrences", String.valueOf(q.occurrences()))), c);
            c.gridx = 3;
            list.add(search, c);
            c.gridx = 4;
            c.weightx = 1;
            list.add(combo, c);
            c.weightx = 0;
        }
        c.gridy = y;
        c.weighty = 1;
        list.add(Box.createGlue(), c);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** "(leave unknown)", then the candidates, then the rest of the catalog. */
    private static List<CatalogChoice> choices(UnknownNameQuestion q, List<CatalogChoice> catalog) {
        List<CatalogChoice> result = new ArrayList<>();
        result.add(new CatalogChoice(null, JournalTexts.text("journal.names.unknown"), null));
        catalog.stream().filter(e -> q.candidates().contains(e.id())).forEach(result::add);
        catalog.stream().filter(e -> !q.candidates().contains(e.id())).forEach(result::add);
        return result;
    }

    private static void filter(JComboBox<CatalogChoice> combo, List<CatalogChoice> all, String text, String selectedId) {
        String wanted = text.strip().toLowerCase(Locale.ROOT);
        DefaultComboBoxModel<CatalogChoice> filtered = new DefaultComboBoxModel<>();
        for (CatalogChoice choice : all) {
            if (choice.id() == null || choice.id().equals(selectedId) || wanted.isEmpty()
                    || choice.label().toLowerCase(Locale.ROOT).contains(wanted)
                    || choice.id().toLowerCase(Locale.ROOT).contains(wanted)) {
                filtered.addElement(choice);
            }
        }
        ActionListenerGuard.withoutEvents(combo, () -> {
            combo.setModel(filtered);
            select(combo, selectedId);
        });
    }

    private static void select(JComboBox<CatalogChoice> combo, String id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            CatalogChoice choice = combo.getItemAt(i);
            if (java.util.Objects.equals(choice.id(), id)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    /** Runs a model change of a combo box without firing its action listeners. */
    private static final class ActionListenerGuard {
        static void withoutEvents(JComboBox<?> combo, Runnable change) {
            var listeners = combo.getActionListeners();
            for (var l : listeners) {
                combo.removeActionListener(l);
            }
            try {
                change.run();
            } finally {
                for (var l : listeners) {
                    combo.addActionListener(l);
                }
            }
        }
    }
}
