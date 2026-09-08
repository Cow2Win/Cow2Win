package org.c2w.gui.guild;


import org.tdi.cow2.Config;

import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Editor for ONE team (hero or titan team, T = Hero or Titan): a single
 * FlowLayout row with an optional team label, a text field for the team's
 * total power (see {@link org.tdi.cow2.model.HeroTeam#totalPower()} /
 * {@link org.tdi.cow2.model.TitanTeam#totalPower()}, {@link #buildPowerField})
 * and 5 comboboxes, one slot per possible team member (HeroTeam/TitanTeam
 * both allow up to 5), sorted according to the catalogOrder comparator
 * supplied by the caller - heroes by name, titans by element then name (see
 * {@link MemberEditorPanel#buildTeamRows}). {@link MemberEditorPanel} simply
 * stacks several TeamEditorPanel rows on top of each other (see there) -
 * there is no "remove team" button here: the rows are fixed (3 hero + 2
 * titan rows per member); an "empty" team with totalPower 0 is simply
 * dropped on save instead of having to be removed separately (see
 * {@link GuildDraftConverter#toGuild}).
 *
 * Each combo box automatically hides whatever is already selected in one of
 * the other 4 combos - the same hero/titan can therefore not accidentally
 * end up twice in the same team. An empty slot ("- none -") is allowed;
 * empty slots are simply skipped when the selection is read out.
 *
 * Writes changes back into the given {@link TeamDraft} IMMEDIATELY (no
 * separate "apply" step needed) - the TeamDraft is therefore always the
 * current state of this team.
 *
 * title is placed as a plain JLabel at the start of the row (e.g.
 * "Heroes 1"/"Titans 1", see {@link MemberEditorPanel}) - if it is null/blank
 * this label is simply omitted.
 *
 * icon supplies an already appropriately scaled icon per catalog entry (see
 * {@link org.tdi.cow2.gui.IconLoader}). If icon actually returns an icon for
 * an entry, the combo box renderer shows ONLY that icon (no name text
 * anymore) - both in the dropdown list and for the currently selected entry
 * of the combo box itself - and the name is instead available as a tooltip
 * on the respective dropdown entry / on the combo box itself. If icon is
 * null or returns no icon for an entry (e.g. a missing hero/titan icon), the
 * renderer falls back to showing the name as text, as before.
 *
 * roleDescriber is optional (null = no role display, currently the case for
 * titan teams - titans have an element instead of roles) and supplies a
 * display text per catalog entry (e.g. "TANK, SUPPORT"), shown as its own
 * JLabel NEXT TO the respective slot combo box, updated automatically on
 * every selection change in that slot.
 *
 * Each of the 5 slot combos (added 2026-09-05, per explicit user request -
 * see {@link #buildLabelKeySelectionManager()}) also jumps to the first
 * entry whose DISPLAY name (via label) starts with whatever letter(s) the
 * user types while that combo has focus - e.g. typing "y" jumps to
 * "Yasmine" - the same convenience Swing combo boxes normally provide out
 * of the box, EXCEPT that Swing's own default only matches against
 * {@link Object#toString()}, which for T (Hero/Titan, both records with no
 * display name field of their own - see e.g. {@link org.tdi.cow2.model.Hero})
 * never matches the name shown to the user, so this installs a custom
 * {@link JComboBox.KeySelectionManager} per combo instead.
 */
public final class TeamEditorPanel<T> extends JPanel {

    private static final int SLOT_COUNT = 5;

    /** Maximum number of digits in the power text field (see {@link #buildPowerField}) - far more than any realistic team power. */
    private static final int MAX_POWER_DIGITS = 9;

    /**
     * Milliseconds within which consecutive keystrokes are treated as one
     * continued type-ahead search (see {@link #buildLabelKeySelectionManager()}) -
     * a keystroke arriving later than this starts a fresh search instead of
     * extending the previous one, matching the informal convention most
     * desktop combo boxes/list controls use for "type to jump".
     */
    private static final long TYPEAHEAD_TIMEOUT_MS = 1000;

    private final List<T> sortedCatalog;
    private final Function<T, String> label;
    private final Function<T, Icon> icon;
    private final Function<T, String> roleDescriber;
    private final String emptyLabel;
    private final TeamDraft<T> teamDraft;
    private final List<JComboBox<T>> combos = new ArrayList<>(SLOT_COUNT);
    private final List<JLabel> roleLabels = new ArrayList<>(SLOT_COUNT);
    private boolean refreshing;
    private boolean formattingPowerField;

    public TeamEditorPanel(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                    Function<T, String> roleDescriber, TeamDraft<T> teamDraft, String emptyLabel,
                    Comparator<T> catalogOrder) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        this.label = label;
        this.icon = icon;
        this.roleDescriber = roleDescriber;
        this.emptyLabel = emptyLabel;
        this.teamDraft = teamDraft;
        this.sortedCatalog = catalog.stream()
                .sorted(catalogOrder)
                .toList();

        JTextField powerField = buildPowerField(teamDraft);
        add(powerField);

        for (int i = 0; i < SLOT_COUNT; i++) {
            JComboBox<T> combo = new JComboBox<>();
            combo.setKeySelectionManager(buildLabelKeySelectionManager());
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                                boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                    if (value == null) {
                        setText(emptyLabel);
                        setIcon(null);
                        setToolTipText(null);
                    } else {
                        @SuppressWarnings("unchecked")
                        T typed = (T) value;
                        Icon itemIcon = TeamEditorPanel.this.icon == null ? null : TeamEditorPanel.this.icon.apply(typed);
                        if (itemIcon != null) {
                            setText(null);
                            setIcon(itemIcon);
                            setToolTipText(label.apply(typed));
                        } else {
                            setText(label.apply(typed));
                            setIcon(null);
                            setToolTipText(null);
                        }
                    }
                    return this;
                }
            });
            combos.add(combo);
            add(combo);

            if (roleDescriber != null) {
                JLabel roleLabel = new JLabel();
                roleLabels.add(roleLabel);
                add(roleLabel);
            }
        }

        // Take over the pre-selection from the given draft (list order = slot order).
        for (int i = 0; i < SLOT_COUNT; i++) {
            T initial = i < teamDraft.members.size() ? teamDraft.members.get(i) : null;
            combos.get(i).setModel(fullModelWithout(new HashSet<>()));
            combos.get(i).setSelectedItem(initial);
        }
        refreshComboOptions();
        syncDraftFromCombos();
        updateRoleLabels();
        updateComboTooltips();

        // Match the power field's height to the slot combo boxes' height -
        // a plain JTextField otherwise renders shorter than a JComboBox
        // under most look and feels (the combo's arrow button pads its
        // preferred height). Width is left as set in buildPowerField.
        //
        // Must run AFTER the combos have their real model (just above) -
        // measuring right after `new JComboBox<>()` (i.e. still with the
        // default empty model) only ever exercises the renderer's
        // null/"- none -" (text-only, no icon) branch, which is shorter
        // than a populated combo once icon-bearing entries (see the icon
        // constructor parameter, e.g. 32px hero/titan icons in
        // MemberEditorPanel) push the real preferred height up - leaving
        // the power field frozen at that too-small, pre-model height.
        Dimension comboSize = combos.get(0).getPreferredSize();
        Dimension powerFieldSize = powerField.getPreferredSize();
        powerField.setPreferredSize(new Dimension(powerFieldSize.width, comboSize.height));
        for (JComboBox<T> combo : combos) {
            combo.addActionListener(e -> {
                if (refreshing) {
                    return;
                }
                syncDraftFromCombos();
                refreshComboOptions();
                updateRoleLabels();
                updateComboTooltips();
                touchLastModified();
            });
        }
    }

    /**
     * Builds a fresh {@link JComboBox.KeySelectionManager} for one slot
     * combo box (see class Javadoc) that jumps to the first entry whose
     * DISPLAY name (via {@link #label}, e.g. "Yasmine") - not
     * {@link Object#toString()} - starts with what was typed, so typing "y"
     * while a slot combo has focus (dropdown open or not) selects
     * "Yasmine" even though T (Hero/Titan, both records) has no meaningful
     * toString() of its own and this combo may be showing only an icon
     * rather than the name (see class Javadoc on icon). Consecutive
     * keystrokes within {@value #TYPEAHEAD_TIMEOUT_MS}ms accumulate into
     * one search (typing "ya" narrows further among names starting with
     * "Y"); a keystroke that doesn't extend any match starts over from
     * just that one character instead of getting stuck. A NEW instance is
     * built per combo box (rather than one manager shared across all 5
     * slots) so each slot's typed-so-far buffer is independent of the
     * others'. {@code null} (the "- none -" entry) is never matched.
     */
    private JComboBox.KeySelectionManager buildLabelKeySelectionManager() {
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
                    // The accumulated buffer matched nothing (e.g. this
                    // keystroke starts an unrelated new search rather than
                    // continuing the previous one) - retry with just the
                    // newly typed character alone instead of getting stuck.
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
                    @SuppressWarnings("unchecked")
                    T typedElement = (T) element;
                    String display = label.apply(typedElement);
                    if (display != null && display.toLowerCase().startsWith(prefix)) {
                        return i;
                    }
                }
                return -1;
            }
        };
    }

    /**
     * Builds the power text field: a plain JTextField (rather than a
     * JSpinner) that only allows digits and at most MAX_POWER_DIGITS of them
     * via a DocumentFilter (see {@link DigitsOnlyFilter}) - both while typing
     * and when pasting excess/invalid characters. Writes the parsed value
     * (empty field = 0) back into teamDraft.totalPower on every change, just
     * like the selection combo boxes.
     *
     * Right-aligned, and displayed formatted via {@link Config#NUMBER_FORMAT}
     * (e.g. "1.234.567") whenever the field does NOT have focus. While the
     * field has focus it instead shows the plain digits, since
     * {@link Config#NUMBER_FORMAT}'s grouping separators are not part of
     * {@link DigitsOnlyFilter}'s allowed character set and would otherwise
     * get in the way of typing/caret placement. The reformatting on
     * focus gained/lost is done via {@link #setPowerFieldText}, which both
     * suppresses the DocumentFilter (so the separators actually make it into
     * the field) and marks the change as programmatic (see
     * {@link #formattingPowerField}) so it does not spuriously touch
     * teamDraft.lastModified.
     */
    private JTextField buildPowerField(TeamDraft<T> teamDraft) {
        JTextField powerField = new JTextField(Config.NUMBER_FORMAT.format(teamDraft.totalPower), MAX_POWER_DIGITS);
        powerField.setHorizontalAlignment(JTextField.RIGHT);
        ((PlainDocument) powerField.getDocument()).setDocumentFilter(new DigitsOnlyFilter(MAX_POWER_DIGITS));
        powerField.getDocument().addDocumentListener(onChange(() -> {
            if (formattingPowerField) {
                return;
            }
            teamDraft.totalPower = parsePower(powerField.getText());
            touchLastModified();
        }));
        powerField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                setPowerFieldText(powerField, String.valueOf(parsePower(powerField.getText())));
            }

            @Override
            public void focusLost(FocusEvent e) {
                setPowerFieldText(powerField, Config.NUMBER_FORMAT.format(parsePower(powerField.getText())));
            }
        });
        powerField.setPreferredSize(new Dimension(50, powerField.getPreferredSize().height));
        return powerField;
    }

    /**
     * Programmatically replaces the power field's text (used to switch
     * between the plain-digits and {@link Config#NUMBER_FORMAT}-formatted
     * display on focus gained/lost, see {@link #buildPowerField}) without
     * running into {@link DigitsOnlyFilter} - which would otherwise strip
     * {@link Config#NUMBER_FORMAT}'s grouping separators right back out -
     * and without the resulting document event being mistaken for a real
     * user edit (see {@link #formattingPowerField}).
     */
    private void setPowerFieldText(JTextField powerField, String text) {
        formattingPowerField = true;
        PlainDocument document = (PlainDocument) powerField.getDocument();
        DocumentFilter filter = document.getDocumentFilter();
        document.setDocumentFilter(null);
        try {
            powerField.setText(text);
        } finally {
            document.setDocumentFilter(filter);
            formattingPowerField = false;
        }
    }

    /** Strips everything but digits (e.g. {@link Config#NUMBER_FORMAT}'s grouping separators) before parsing - empty/blank (or digit-less) text = 0. */
    private static int parsePower(String text) {
        String digitsOnly = text == null ? "" : text.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? 0 : Integer.parseInt(digitsOnly);
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    /** Allows only digits in a JTextField and caps the total length at maxDigits (see {@link #buildPowerField}). */
    private static final class DigitsOnlyFilter extends DocumentFilter {
        private final int maxDigits;

        DigitsOnlyFilter(int maxDigits) {
            this.maxDigits = maxDigits;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs) throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String digitsOnly = text == null ? "" : text.replaceAll("[^0-9]", "");
            int resultLength = fb.getDocument().getLength() - length + digitsOnly.length();
            if (resultLength > maxDigits) {
                digitsOnly = digitsOnly.substring(0, Math.max(0, digitsOnly.length() - (resultLength - maxDigits)));
            }
            super.replace(fb, offset, length, digitsOnly, attrs);
        }
    }

    /** Recomputes which entries are selectable in which slot (see class Javadoc). */
    private void refreshComboOptions() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            for (int i = 0; i < SLOT_COUNT; i++) {
                JComboBox<T> combo = combos.get(i);
                @SuppressWarnings("unchecked")
                T current = (T) combo.getSelectedItem();

                Set<T> selectedElsewhere = new HashSet<>();
                for (int j = 0; j < SLOT_COUNT; j++) {
                    if (j == i) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    T other = (T) combos.get(j).getSelectedItem();
                    if (other != null) {
                        selectedElsewhere.add(other);
                    }
                }

                combo.setModel(fullModelWithout(selectedElsewhere));
                combo.setSelectedItem(current);
            }
        } finally {
            refreshing = false;
        }
    }

    private DefaultComboBoxModel<T> fullModelWithout(Set<T> excluded) {
        DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        for (T t : sortedCatalog) {
            if (!excluded.contains(t)) {
                model.addElement(t);
            }
        }
        return model;
    }

    private void syncDraftFromCombos() {
        teamDraft.members.clear();
        for (JComboBox<T> combo : combos) {
            @SuppressWarnings("unchecked")
            T selected = (T) combo.getSelectedItem();
            if (selected != null) {
                teamDraft.members.add(selected);
            }
        }
    }

    /**
     * Sets teamDraft.lastModified to today's date (see {@link TeamDraft#lastModified}) -
     * called by every listener that reflects a REAL user change to this team
     * (slot selection, power). NOT called during the initial population of
     * this panel from a loaded/new TeamDraft (see the constructor order:
     * listeners are only registered AFTER the initial population).
     */
    private void touchLastModified() {
        teamDraft.lastModified = LocalDate.now();
    }

    /** Updates all role labels (see class Javadoc on roleDescriber) - no-op if roleDescriber is null. */
    private void updateRoleLabels() {
        if (roleDescriber == null) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            @SuppressWarnings("unchecked")
            T selected = (T) combos.get(i).getSelectedItem();
            roleLabels.get(i).setText(selected == null ? "" : roleDescriber.apply(selected));
        }
    }

    /**
     * Sets the name of the currently selected entry as a tooltip on each
     * combo box itself (not just in the expanded dropdown), so it stays
     * available even while the combo box is closed, in case the renderer
     * shows only the icon instead of the name because one is available (see
     * class Javadoc on icon). No-op if no icon function was supplied (e.g.
     * titan teams) - there the renderer shows the name as text anyway.
     */
    private void updateComboTooltips() {
        if (icon == null) {
            return;
        }
        for (JComboBox<T> combo : combos) {
            @SuppressWarnings("unchecked")
            T selected = (T) combo.getSelectedItem();
            combo.setToolTipText(selected == null ? null : label.apply(selected));
        }
    }
}
