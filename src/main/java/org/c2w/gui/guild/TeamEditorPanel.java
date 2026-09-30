package org.c2w.gui.guild;


import org.c2w.data.model.Pet;
import org.c2w.data.model.WarFlag;
import org.c2w.data.repository.PetRepository;
import org.c2w.data.repository.WarFlagRepository;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.LanguageService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * One team row: power field, then - for hero teams only, see
 * {@link TeamExtras} - a war flag and a pet combo box, then
 * {@value #SLOT_COUNT} member slot combo boxes, all in one FlowLayout row
 * (in exactly this order: power, war flag, pet, members).
 */
public final class TeamEditorPanel<T> extends JPanel {

    private static final int SLOT_COUNT = 5;

    /** Maximum number of digits in the power text field (see {@link #buildPowerField}) - far more than any realistic team power. */
    private static final int MAX_POWER_DIGITS = 9;

    /**
     * Milliseconds within which consecutive keystrokes are treated as one
     * continued type-ahead search (see {@link #buildLabelKeySelectionManager(Function, boolean)}) -
     * a keystroke arriving later than this starts a fresh search instead of
     * extending the previous one, matching the informal convention most
     * desktop combo boxes/list controls use for "type to jump".
     */
    private static final long TYPEAHEAD_TIMEOUT_MS = 1000;

    /** Language file keys for what the war flag/pet combo boxes show while nothing is selected, and as their tooltip prefix. */
    private static final String KEY_WAR_FLAG = "teamEditor.warFlag";
    private static final String KEY_PET = "teamEditor.pet";

    private final List<T> sortedCatalog;
    private final Function<T, String> label;
    private final Function<T, Icon> icon;
    private final Function<T, String> roleDescriber;
    private final String emptyLabel;
    private final TeamDraft<T> teamDraft;
    private final List<JComboBox<T>> combos = new ArrayList<>(SLOT_COUNT);
    private final List<JLabel> roleLabels = new ArrayList<>(SLOT_COUNT);
    private final Runnable onChanged;
    private JTextField powerField;

    /** Optional war flag/pet combo boxes - both null unless a {@link TeamExtras} was given (hero teams only). */
    private JComboBox<WarFlag> warFlagCombo;
    private JComboBox<Pet> petCombo;

    /** Guards the war flag/pet combo boxes' own listeners while their model/selection is changed programmatically. */
    private boolean updatingExtras;

    private boolean refreshing;
    private boolean formattingPowerField;

    public TeamEditorPanel(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                    Function<T, String> roleDescriber, TeamDraft<T> teamDraft, String emptyLabel,
                    Comparator<T> catalogOrder) {
        this(catalog, label, icon, roleDescriber, teamDraft, emptyLabel, catalogOrder, null);
    }

    /**
     * Same as {@link #TeamEditorPanel(List, Function, Function, Function, TeamDraft, String, Comparator)},
     * with an additional callback invoked every time a slot selection change
     * actually updates {@code teamDraft.members} (i.e. alongside
     * {@link #touchLastModified()} - NOT for the initial population from the
     * given draft, same as that method) - added 2026-09-10 so a caller like
     * {@code org.c2w.gui.fort.FortificationEntryDialog} can keep a label
     * derived from this team's current members (e.g. a buff-member count) in
     * sync without polling. {@code onChanged} may be null (no-op), same as
     * {@code roleDescriber}.
     */
    public TeamEditorPanel(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                    Function<T, String> roleDescriber, TeamDraft<T> teamDraft, String emptyLabel,
                    Comparator<T> catalogOrder, Runnable onChanged) {
        this(catalog, label, icon, roleDescriber, teamDraft, emptyLabel, catalogOrder, onChanged, null);
    }

    /**
     * Same as {@link #TeamEditorPanel(List, Function, Function, Function, TeamDraft, String, Comparator, Runnable)},
     * plus - if {@code extras} is not null - a war flag and a pet combo box
     * between the power field and the member slots, bound to
     * {@code teamDraft.warFlag}/{@code teamDraft.pet} (hero teams only, see
     * {@link TeamExtras}). A change there counts as a real user change just
     * like a slot change: it touches lastModified and fires {@code onChanged}.
     */
    public TeamEditorPanel(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                    Function<T, String> roleDescriber, TeamDraft<T> teamDraft, String emptyLabel,
                    Comparator<T> catalogOrder, Runnable onChanged, TeamExtras extras) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setOpaque(false);
        this.label = label;
        this.icon = icon;
        this.roleDescriber = roleDescriber;
        this.emptyLabel = emptyLabel;
        this.teamDraft = teamDraft;
        this.onChanged = onChanged;
        this.sortedCatalog = catalog.stream()
                .sorted(catalogOrder)
                .toList();

        this.powerField = buildPowerField(teamDraft);
        add(powerField);

        if (extras != null) {
            warFlagCombo = buildExtraCombo(WarFlagRepository.findAll(), WarFlag::id, WarFlag::imagePath,
                    teamDraft.warFlag, extras.blockedWarFlagIds(), KEY_WAR_FLAG, true,
                    selected -> teamDraft.warFlag = selected);
            add(warFlagCombo);
            petCombo = buildExtraCombo(PetRepository.findAll(), Pet::id, Pet::imagePath,
                    teamDraft.pet, extras.blockedPetIds(), KEY_PET, false,
                    selected -> teamDraft.pet = selected);
            add(petCombo);
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            JComboBox<T> combo = new JComboBox<>();
            combo.setKeySelectionManager(buildLabelKeySelectionManager(label, false));
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
                if (onChanged != null) {
                    onChanged.run();
                }
            });
        }
    }

    /**
     * Builds a fresh {@link JComboBox.KeySelectionManager} for one slot
     * combo box (see class Javadoc) - or one of the war flag/pet combo
     * boxes, see {@link #buildExtraCombo} - that jumps to the first entry whose
     * DISPLAY name (via {@code labelOf}, e.g. "Yasmine") - not
     * {@link Object#toString()} - starts with what was typed, so typing "y"
     * while a slot combo has focus (dropdown open or not) selects
     * "Yasmine" even though E (Hero/Titan/Pet/WarFlag, all records) has no meaningful
     * toString() of its own and this combo may be showing only an icon
     * rather than the name (see class Javadoc on icon). Consecutive
     * keystrokes within {@value #TYPEAHEAD_TIMEOUT_MS}ms accumulate into
     * one search (typing "ya" narrows further among names starting with
     * "Y"); a keystroke that doesn't extend any match starts over from
     * just that one character instead of getting stuck. A NEW instance is
     * built per combo box (rather than one manager shared across all 5
     * slots) so each slot's typed-so-far buffer is independent of the
     * others'. {@code null} (the "- none -" entry) is never matched.
     *
     * With {@code matchAnyWord}, an entry also matches if ANY word of its
     * display name starts with what was typed - used for war flags, whose
     * names all share the same leading words ("Kriegsflagge der/des ...",
     * "War Flag of ..."), so matching only the start of the name could never
     * tell them apart.
     */
    private static <E> JComboBox.KeySelectionManager buildLabelKeySelectionManager(Function<E, String> labelOf,
                                                                                   boolean matchAnyWord) {
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
                    E typedElement = (E) element;
                    String display = labelOf.apply(typedElement);
                    if (display != null && matches(display.toLowerCase(), prefix)) {
                        return i;
                    }
                }
                return -1;
            }

            private boolean matches(String display, String prefix) {
                if (display.startsWith(prefix)) {
                    return true;
                }
                if (matchAnyWord) {
                    for (String word : display.split("\\s+")) {
                        if (word.startsWith(prefix)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }


    /**
     * Builds one of the optional war flag/pet combo boxes (see {@link TeamExtras}):
     * "- none -" plus the catalog sorted by display name, icon-only like the
     * member slots (display name as tooltip). While nothing is selected, the
     * closed combo box shows its kind ("War flag"/"Pet") instead of
     * "- none -", so the two are told apart at a glance. Its model is rebuilt
     * every time the dropdown opens, hiding every id {@code blockedIds}
     * currently returns (used by another hero team of the same member)
     * except the current selection.
     */
    private <E> JComboBox<E> buildExtraCombo(List<E> catalog, Function<E, String> idOf, Function<E, String> imagePathOf,
                                             E initial, Supplier<Set<String>> blockedIds, String kindKey,
                                             boolean typeAheadMatchesAnyWord, Consumer<E> onSelected) {
        Function<E, String> labelOf = e -> LanguageService.displayName(idOf.apply(e));
        List<E> sortedEntries = catalog.stream()
                .sorted(Comparator.comparing(labelOf, String.CASE_INSENSITIVE_ORDER))
                .toList();
        String kindLabel = LanguageService.displayName(kindKey);

        // Match the draft's value against the catalog by id, not equals(): a
        // Pet/WarFlag record also carries its CowScore, so an instance loaded
        // before a CowScore edit is no longer equal to the catalog's current one.
        E selected = initial == null ? null : sortedEntries.stream()
                .filter(e -> idOf.apply(e).equals(idOf.apply(initial)))
                .findFirst()
                .orElse(initial);

        JComboBox<E> combo = new JComboBox<>();
        // Same "type to jump" behavior as the member slots, e.g. "o" selects "Oliver" - for war flags
        // matching any word of the name, see buildLabelKeySelectionManager.
        combo.setKeySelectionManager(buildLabelKeySelectionManager(labelOf, typeAheadMatchesAnyWord));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText(index == -1 ? kindLabel : emptyLabel);
                    setIcon(null);
                    setToolTipText(null);
                } else {
                    @SuppressWarnings("unchecked")
                    E typed = (E) value;
                    setText(null);
                    setIcon(IconLoader.iconFor(imagePathOf.apply(typed), TeamExtras.ICON_SIZE));
                    setToolTipText(labelOf.apply(typed));
                }
                return this;
            }
        });

        updatingExtras = true;
        try {
            combo.setModel(extraModel(sortedEntries, idOf, Set.of(), selected));
            combo.setSelectedItem(selected);
        } finally {
            updatingExtras = false;
        }
        updateExtraTooltip(combo, kindLabel, labelOf);

        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                updatingExtras = true;
                try {
                    @SuppressWarnings("unchecked")
                    E current = (E) combo.getSelectedItem();
                    combo.setModel(extraModel(sortedEntries, idOf, blockedIds.get(), current));
                    combo.setSelectedItem(current);
                } finally {
                    updatingExtras = false;
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        combo.addActionListener(e -> {
            if (updatingExtras) {
                return;
            }
            @SuppressWarnings("unchecked")
            E current = (E) combo.getSelectedItem();
            onSelected.accept(current);
            updateExtraTooltip(combo, kindLabel, labelOf);
            touchLastModified();
            if (onChanged != null) {
                onChanged.run();
            }
        });
        return combo;
    }

    /** "- none -" (null) plus every entry whose id is not blocked - {@code current} is always kept, even if blocked or not in the catalog. */
    private static <E> DefaultComboBoxModel<E> extraModel(List<E> sortedEntries, Function<E, String> idOf,
                                                         Set<String> blockedIds, E current) {
        DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        boolean currentAdded = current == null;
        for (E entry : sortedEntries) {
            boolean isCurrent = current != null && idOf.apply(entry).equals(idOf.apply(current));
            if (isCurrent || !blockedIds.contains(idOf.apply(entry))) {
                model.addElement(isCurrent ? current : entry);
                currentAdded |= isCurrent;
            }
        }
        if (!currentAdded) {
            model.addElement(current);
        }
        return model;
    }

    /** Tooltip of a closed war flag/pet combo box: its kind plus the selected entry's display name, e.g. "Pet: Albus". */
    private static <E> void updateExtraTooltip(JComboBox<E> combo, String kindLabel, Function<E, String> labelOf) {
        @SuppressWarnings("unchecked")
        E selected = (E) combo.getSelectedItem();
        combo.setToolTipText(selected == null ? kindLabel : kindLabel + ": " + labelOf.apply(selected));
    }

    private JTextField buildPowerField(TeamDraft<T> teamDraft) {
        JTextField powerField = new JTextField(GuiUtils.NUMBER_FORMAT.format(teamDraft.totalPower), MAX_POWER_DIGITS);
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
                setPowerFieldText(powerField, GuiUtils.NUMBER_FORMAT.format(parsePower(powerField.getText())));
            }
        });
        powerField.setPreferredSize(new Dimension(50, powerField.getPreferredSize().height));
        return powerField;
    }

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

    /** Strips everything but digits (e.g. {@link GuiUtils#NUMBER_FORMAT}'s grouping separators) before parsing - empty/blank (or digit-less) text = 0. */
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

    /**
     * Resets this team back to empty: every slot combo goes back to
     * "- none -" and the power field back to 0, exactly as if each slot had
     * been cleared out by hand one at a time. Added 2026-09-18 for
     * {@code org.c2w.gui.fort.FortificationEntryDialog}, which calls this
     * when its own member combo (a different combo - which guild member
     * this row belongs to, not a team slot) is reset to "no selection", so
     * the team built here is discarded along with that row's fortification
     * assignment. Fires {@code onChanged} exactly like an ordinary slot
     * change (see the per-combo {@code addActionListener} above), so
     * anything mirroring this team's state (e.g. a buff-member count label)
     * stays in sync; a no-op {@code onChanged} (null) is fine, same as
     * everywhere else it's used.
     */
    public void clear() {
        refreshing = true;
        try {
            for (JComboBox<T> combo : combos) {
                combo.setSelectedItem(null);
            }
        } finally {
            refreshing = false;
        }
        setPowerFieldText(powerField, GuiUtils.NUMBER_FORMAT.format(0));
        teamDraft.totalPower = 0;
        teamDraft.pet = null;
        teamDraft.warFlag = null;
        clearExtraCombo(warFlagCombo, KEY_WAR_FLAG);
        clearExtraCombo(petCombo, KEY_PET);
        syncDraftFromCombos();
        refreshComboOptions();
        updateRoleLabels();
        updateComboTooltips();
        touchLastModified();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** Resets an optional war flag/pet combo box (null if not shown) to "- none -" without firing its own listener - see {@link #clear()}. */
    private void clearExtraCombo(JComboBox<?> combo, String kindKey) {
        if (combo == null) {
            return;
        }
        updatingExtras = true;
        try {
            combo.setSelectedItem(null);
        } finally {
            updatingExtras = false;
        }
        combo.setToolTipText(LanguageService.displayName(kindKey));
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
