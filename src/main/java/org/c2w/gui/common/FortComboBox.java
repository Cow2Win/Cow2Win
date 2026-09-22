package org.c2w.gui.common;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class FortComboBox extends JComboBox<Fortification> {

    private static final String KEY_NO_FORTIFICATION = "common.none";
    private static final long TYPEAHEAD_TIMEOUT_MS = 1000;
    private java.util.List<Fortification> fortificationCatalog;
    private FortificationType type = FortificationType.HERO;
    DefaultComboBoxModel<Fortification> model = new DefaultComboBoxModel<>();

    public FortComboBox(List<Fortification> fortificationCatalog, FortificationType type){
        super();
        this.fortificationCatalog = fortificationCatalog;
        this.type = type;
        init();
    }

    private void init(){
        List<Fortification> sorted = fortificationCatalog.stream()
                .filter(f -> f.type() == type)
                .sorted(Comparator.comparing(f -> LanguageService.displayName(f.id())))
                .toList();

        setModel(model);
        model.addElement(null);
        sorted.forEach(model::addElement);

        setKeySelectionManager(buildKeySelectionManager());

        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? LanguageService.displayName(KEY_NO_FORTIFICATION)
                        : LanguageService.displayName(((Fortification) value).id()));
                return this;
            }
        });
    }

    /**
     * Builds a {@link JComboBox.KeySelectionManager} that jumps to the
     * first entry whose DISPLAY name (via {@link LanguageService#displayName},
     * e.g. "Wachturm") - not {@link Object#toString()} - starts with what
     * was typed, mirroring the manager used by the hero/member combo boxes
     * (see {@code GuildTeamEntryDialog#buildMemberCombo} and
     * {@code TeamEditorPanel#buildLabelKeySelectionManager}). Consecutive
     * keystrokes within {@value #TYPEAHEAD_TIMEOUT_MS}ms accumulate into
     * one search (typing "wa" narrows further among names starting with
     * "W"); a keystroke that doesn't extend any match starts over from
     * just that one character instead of getting stuck. {@code null} (the
     * "- none -" entry) is never matched.
     */
    private JComboBox.KeySelectionManager buildKeySelectionManager() {
        return new JComboBox.KeySelectionManager() {
            private String typed = "";
            private long lastKeystrokeAt = 0;

            @Override
            public int selectionForKey(char keyChar, ComboBoxModel<?> model) {
                long now = System.currentTimeMillis();
                String continued = now - lastKeystrokeAt <= TYPEAHEAD_TIMEOUT_MS ? typed : "";
                String candidate = continued + Character.toLowerCase(keyChar);
                lastKeystrokeAt = now;

                int match = firstMatch(model, candidate);
                if (match < 0 && !continued.isEmpty()) {
                    candidate = String.valueOf(Character.toLowerCase(keyChar));
                    match = firstMatch(model, candidate);
                }
                typed = candidate;
                return match;
            }

            private int firstMatch(ComboBoxModel<?> model, String prefix) {
                for (int i = 0; i < model.getSize(); i++) {
                    Object element = model.getElementAt(i);
                    if (element == null) {
                        continue;
                    }
                    String display = LanguageService.displayName(((Fortification) element).id());
                    if (display != null && display.toLowerCase().startsWith(prefix)) {
                        return i;
                    }
                }
                return -1;
            }
        };
    }
}
