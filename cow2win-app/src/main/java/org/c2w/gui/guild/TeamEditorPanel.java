package org.c2w.gui.guild;


import org.c2w.data.model.Pet;
import org.c2w.data.model.TeamTemplate;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.data.model.TitanTeam;
import org.c2w.data.model.WarFlag;
import org.c2w.data.repository.TeamTemplateRepository;
import org.c2w.domain.TeamTemplates;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.Logger;

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
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * One team row: power field, then - for hero teams only, see
 * {@link TeamExtras} - a war flag and a pet combo box, then
 * {@value #SLOT_COUNT} member slot combo boxes, then - for titan teams only,
 * see {@link TitanTeamExtras} - two totem combo boxes, all in one FlowLayout
 * row (in exactly this order: power, war flag, pet, members, totem 1,
 * totem 2). The totems come last so that, in focus order, the titans they
 * depend on are already entered.
 *
 * <p>Optionally (see {@link #enableTemplates}) F1-F5 fills the member slots
 * from a stored team template and Shift+F1-F5 saves the row as one.
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

    /** Language file keys for the totem combo boxes: "Totem {0}" while nothing is selected / as tooltip prefix, the "none" entry, the rule hint. */
    private static final String KEY_TOTEM = "teamEditor.totem";
    private static final String KEY_NO_TOTEM = "teamEditor.noTotem";
    private static final String KEY_TOTEM_HINT = "teamEditor.totemHint";

    /** Language file keys for the team templates (see {@link #enableTemplates}). */
    private static final String KEY_TEMPLATE_HINT = "teamEditor.templateHint";
    private static final String KEY_TEMPLATE_OVERWRITE_TITLE = "teamEditor.templateOverwriteTitle";
    private static final String KEY_TEMPLATE_OVERWRITE = "teamEditor.templateOverwrite";
    private static final String KEY_TEMPLATE_SAVE_FAILED = "teamEditor.templateSaveFailed";

    private final List<T> sortedCatalog;
    private final Function<T, String> label;
    /** Tooltip of an entry shown as an icon - the {@link #label} unless set via {@link #setItemTooltip}. */
    private Function<T, String> itemTooltip;
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

    /** Optional totem combo boxes - empty unless a {@link TitanTeamExtras} was given (titan teams only). */
    private final List<JComboBox<TitanElement>> totemCombos = new ArrayList<>(TitanTeam.MAX_TOTEMS);

    /** What the totem combo boxes offer at most - see {@link TitanTeamExtras#totems()}. */
    private List<TitanElement> offeredTotems = List.of();

    /** Guards the war flag/pet/totem combo boxes' own listeners while their model/selection is changed programmatically. */
    private boolean updatingExtras;

    private boolean refreshing;
    private boolean formattingPowerField;

    /** Team templates of this row's kind and how to get an entry's id - both null until {@link #enableTemplates} (= no templates). */
    private TeamTemplateRepository templates;
    private Function<T, String> idOf;

    public TeamEditorPanel(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                    Function<T, String> roleDescriber, TeamDraft<T> teamDraft, String emptyLabel,
                    Comparator<T> catalogOrder) {
        this(catalog, label, icon, roleDescriber, teamDraft, emptyLabel, catalogOrder, null);
    }

    /**
     * Same as {@link #TeamEditorPanel(List, Function, Function, Function, TeamDraft, String, Comparator)},
     * with an additional callback invoked every time a real user change
     * updates the draft - a slot selection or the power value (i.e. alongside
     * {@link #touchLastModified()} - NOT for the initial population from the
     * given draft, nor for merely reformatting the power field) - so a caller
     * like {@code org.c2w.gui.fort.FortificationEntryDialog} can keep a label
     * derived from this team (e.g. a buff-member count or score) in sync
     * without polling. {@code onChanged} may be null (no-op), same as
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
        this(catalog, label, icon, roleDescriber, teamDraft, emptyLabel, catalogOrder, onChanged, extras, null);
    }

    /**
     * Same as {@link #TeamEditorPanel(List, Function, Function, Function, TeamDraft, String, Comparator, Runnable, TeamExtras)},
     * plus - if {@code titanExtras} is not null - two totem combo boxes
     * after the member slots, bound to
     * {@code teamDraft.totems} (titan teams only - T must be {@link Titan},
     * see {@link TitanTeamExtras}). A totem change counts as a real user
     * change just like a slot change. A slot change (by hand or via a
     * template) that leaves a selected totem without enough titans of its
     * element removes that totem again, see {@link #dropIneligibleTotems()}.
     */
    public TeamEditorPanel(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                    Function<T, String> roleDescriber, TeamDraft<T> teamDraft, String emptyLabel,
                    Comparator<T> catalogOrder, Runnable onChanged, TeamExtras extras, TitanTeamExtras titanExtras) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setOpaque(false);
        this.label = label;
        this.itemTooltip = label;
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
            warFlagCombo = buildExtraCombo(extras.warFlags(), WarFlag::id, WarFlag::imagePath,
                    teamDraft.warFlag, extras.blockedWarFlagIds(), KEY_WAR_FLAG, true,
                    selected -> teamDraft.warFlag = selected);
            add(warFlagCombo);
            petCombo = buildExtraCombo(extras.pets(), Pet::id, Pet::imagePath,
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
                            setToolTipText(itemTooltip.apply(typed));
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

        // Totems AFTER the slots: in focus order the titans are entered first, so the totem
        // dropdowns can already be filtered by them (see refreshTotemModel).
        if (titanExtras != null) {
            offeredTotems = titanExtras.totems();
            List<TitanElement> initialTotems = new ArrayList<>(teamDraft.totems);
            for (int i = 0; i < TitanTeam.MAX_TOTEMS; i++) {
                JComboBox<TitanElement> totemCombo = buildTotemCombo(i + 1,
                        i < initialTotems.size() ? initialTotems.get(i) : null);
                totemCombos.add(totemCombo);
                add(totemCombo);
            }
            refreshTotemModels(); // full models right away, so type-ahead (e.g. "e" = Erde) works without opening the dropdown
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
        // constructor parameter, e.g. 32px hero/titan icons in the
        // team assignment) push the real preferred height up - leaving
        // the power field frozen at that too-small, pre-model height.
        Dimension comboSize = combos.get(0).getPreferredSize();
        Dimension powerFieldSize = powerField.getPreferredSize();
        powerField.setPreferredSize(new Dimension(powerFieldSize.width, comboSize.height));
        // Same for the totem combo boxes - text-only entries would otherwise make them shorter than the icon slots.
        for (JComboBox<TitanElement> totemCombo : totemCombos) {
            totemCombo.setPreferredSize(new Dimension(totemCombo.getPreferredSize().width, comboSize.height));
        }
        for (JComboBox<T> combo : combos) {
            combo.addActionListener(e -> {
                if (refreshing) {
                    return;
                }
                syncDraftFromCombos();
                dropIneligibleTotems();
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
        // Pet/WarFlag record also carries its fortification marks, so an instance loaded
        // before a marks edit is no longer equal to the catalog's current one.
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

    /**
     * Builds totem combo box {@code number} (1 or 2, see {@link TitanTeamExtras}):
     * "no totem" plus the totems the row currently allows, by name (there
     * are no element icons). While nothing is selected, the closed combo box
     * shows "Totem 1"/"Totem 2". Its model is kept current on every titan or
     * totem change (see {@link #refreshTotemModels()}), so typing the first
     * letter of a name selects it, and is rebuilt once more when the dropdown
     * opens. Height: matched to the member slots in the constructor.
     */
    private JComboBox<TitanElement> buildTotemCombo(int number, TitanElement initial) {
        String kindLabel = LanguageService.displayName(KEY_TOTEM, number);
        String noTotemLabel = LanguageService.displayName(KEY_NO_TOTEM);

        JComboBox<TitanElement> combo = new JComboBox<>();
        combo.setKeySelectionManager(buildLabelKeySelectionManager(TotemTexts::name, false));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                setIcon(null);
                setToolTipText(null);
                if (value == null) {
                    setText(index == -1 ? kindLabel : noTotemLabel);
                } else {
                    setText(TotemTexts.name((TitanElement) value));
                }
                return this;
            }
        });
        // Keeps the closed combo box from changing its width with the selection.
        combo.setPrototypeDisplayValue(TitanElement.DISTORTION);

        setTotemSelection(combo, number, initial);

        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                refreshTotemModel(combo);
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
            syncTotemsFromCombos();
            updateTotemTooltip(combo, number);
            refreshOtherTotemModels(combo);
            touchLastModified();
            if (onChanged != null) {
                onChanged.run();
            }
        });
        return combo;
    }

    /**
     * Rebuilds {@code combo}'s model: "no totem" (null) plus every offered
     * totem the row's current titans allow (see
     * {@link TitanTeam#eligibleTotems}) that is not selected in the other
     * totem combo box - the own selection is always kept.
     */
    void refreshTotemModel(JComboBox<TitanElement> combo) {
        TitanElement current = (TitanElement) combo.getSelectedItem();
        Set<TitanElement> selectedElsewhere = EnumSet.noneOf(TitanElement.class);
        for (JComboBox<TitanElement> other : totemCombos) {
            if (other != combo && other.getSelectedItem() != null) {
                selectedElsewhere.add((TitanElement) other.getSelectedItem());
            }
        }
        Set<TitanElement> eligible = TitanTeam.eligibleTotems(titanMembers());

        DefaultComboBoxModel<TitanElement> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        for (TitanElement totem : offeredTotems) {
            if (totem == current || (eligible.contains(totem) && !selectedElsewhere.contains(totem))) {
                model.addElement(totem);
            }
        }
        if (current != null && model.getIndexOf(current) < 0) {
            model.addElement(current);
        }
        updatingExtras = true;
        try {
            combo.setModel(model);
            combo.setSelectedItem(current);
        } finally {
            updatingExtras = false;
        }
    }

    /**
     * Rebuilds every totem combo box's model (see {@link #refreshTotemModel}) -
     * called whenever the titans or the totems change, so a closed combo box
     * always holds exactly the selectable totems: type-ahead (e.g. "e" =
     * Erde) only searches the current model.
     */
    private void refreshTotemModels() {
        totemCombos.forEach(this::refreshTotemModel);
    }

    /** Like {@link #refreshTotemModels()}, except {@code changed} itself - for its own action listener. */
    private void refreshOtherTotemModels(JComboBox<TitanElement> changed) {
        for (JComboBox<TitanElement> combo : totemCombos) {
            if (combo != changed) {
                refreshTotemModel(combo);
            }
        }
    }

    /** Selects {@code totem} in totem combo box {@code number} without firing its own listener (the draft is not touched). */
    private void setTotemSelection(JComboBox<TitanElement> combo, int number, TitanElement totem) {
        updatingExtras = true;
        try {
            DefaultComboBoxModel<TitanElement> model = new DefaultComboBoxModel<>();
            model.addElement(null);
            if (totem != null) {
                model.addElement(totem);
            }
            combo.setModel(model);
            combo.setSelectedItem(totem);
        } finally {
            updatingExtras = false;
        }
        updateTotemTooltip(combo, number);
    }

    /** Tooltip of a totem combo box: e.g. "Totem 1: Feuer" (or just "Totem 1"), plus the rule hint. */
    private static void updateTotemTooltip(JComboBox<TitanElement> combo, int number) {
        String kindLabel = LanguageService.displayName(KEY_TOTEM, number);
        TitanElement selected = (TitanElement) combo.getSelectedItem();
        String text = selected == null ? kindLabel : kindLabel + ": " + TotemTexts.name(selected);
        combo.setToolTipText("<html>" + escapeHtml(text) + "<br>" + escapeHtml(LanguageService.displayName(KEY_TOTEM_HINT))
                + "</html>");
    }

    /** Writes the totem combo boxes' selections into {@code teamDraft.totems}. */
    private void syncTotemsFromCombos() {
        if (totemCombos.isEmpty()) {
            return;
        }
        teamDraft.totems.clear();
        for (JComboBox<TitanElement> combo : totemCombos) {
            if (combo.getSelectedItem() != null) {
                teamDraft.totems.add((TitanElement) combo.getSelectedItem());
            }
        }
    }

    /**
     * After the members changed: every selected totem the row's titans no
     * longer allow (fewer than {@link TitanTeam#MIN_TITANS_PER_TOTEM} of its
     * element) is reset to "no totem" without asking, logged, and removed
     * from the draft. A totem that becomes allowed again later is NOT
     * selected again automatically. No-op without totem combo boxes.
     */
    private void dropIneligibleTotems() {
        if (totemCombos.isEmpty()) {
            return;
        }
        Set<TitanElement> eligible = TitanTeam.eligibleTotems(titanMembers());
        for (int i = 0; i < totemCombos.size(); i++) {
            JComboBox<TitanElement> combo = totemCombos.get(i);
            TitanElement selected = (TitanElement) combo.getSelectedItem();
            if (selected != null && !eligible.contains(selected)) {
                Logger.log("Totem " + selected + " removed from the titan team - it requires at least "
                        + TitanTeam.MIN_TITANS_PER_TOTEM + " titans of its element");
                setTotemSelection(combo, i + 1, null);
            }
        }
        syncTotemsFromCombos();
        refreshTotemModels();
    }

    /** The draft's members as titans - only meaningful with totem combo boxes, i.e. for a titan team (see {@link TitanTeamExtras}). */
    private List<Titan> titanMembers() {
        return teamDraft.members.stream().filter(Titan.class::isInstance).map(Titan.class::cast).toList();
    }

    private JTextField buildPowerField(TeamDraft<T> teamDraft) {
        JTextField powerField = new JTextField(GuiUtils.NUMBER_FORMAT.format(teamDraft.totalPower), MAX_POWER_DIGITS);
        powerField.setHorizontalAlignment(JTextField.RIGHT);
        ((PlainDocument) powerField.getDocument()).setDocumentFilter(new DigitsOnlyFilter(MAX_POWER_DIGITS));
        powerField.getDocument().addDocumentListener(onChange(() -> {
            if (formattingPowerField) {
                return;
            }
            int power = parsePower(powerField.getText());
            if (power == teamDraft.totalPower) {
                return;
            }
            teamDraft.totalPower = power;
            touchLastModified();
            if (onChanged != null) {
                onChanged.run();
            }
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
     * Sets the tooltip of an entry shown as an icon (in the slot dropdowns and
     * on a slot's selected entry) - e.g. a titan's name, element and roles.
     * Defaults to the label; null restores that default.
     */
    public void setItemTooltip(Function<T, String> itemTooltip) {
        this.itemTooltip = itemTooltip == null ? label : itemTooltip;
        updateComboTooltips();
    }

    /**
     * Enables the team templates for this row: F1-F5 fills the 5 slots with
     * template 1-5 of {@code templates} (see {@link #loadTemplate}),
     * Shift+F1-F5 saves the row's current members as that template (see
     * {@link #saveTemplate}), and the row's tooltips mention both. The panel
     * itself is generic and does not know whether it edits heroes or titans
     * - the caller passes the matching repository (e.g. {@code
     * catalog.heroTemplates()}) and how to get an entry's id ({@code
     * Hero::id}). Not calling this (or passing a null repository) means
     * no templates.
     *
     * <p>The keys are bound with {@link JComponent#WHEN_FOCUSED} directly on
     * every focusable part of the row (power field, war flag/pet combo
     * boxes, member slots): WHEN_FOCUSED bindings of the focused component
     * are processed before any WHEN_ANCESTOR_OF_FOCUSED_COMPONENT binding -
     * which is where a look and feel puts its own combo box keys (e.g. F4 =
     * toggle popup under the Windows look and feel) - so these bindings
     * always win. An open dropdown is closed first. Typing to jump in a
     * combo box is unaffected: F-keys produce no key-typed character. The
     * same bindings are also put on the panel as an ancestor binding for
     * anything else inside the row.
     */
    public void enableTemplates(TeamTemplateRepository templates, Function<T, String> idOf) {
        if (templates == null) {
            return;
        }
        if (idOf == null) {
            throw new IllegalArgumentException("idOf must not be null");
        }
        this.templates = templates;
        this.idOf = idOf;

        List<JComponent> focusables = new ArrayList<>();
        focusables.add(powerField);
        if (warFlagCombo != null) {
            focusables.add(warFlagCombo);
        }
        if (petCombo != null) {
            focusables.add(petCombo);
        }
        focusables.addAll(totemCombos);
        focusables.addAll(combos);
        for (JComponent component : focusables) {
            bindTemplateKeys(component, JComponent.WHEN_FOCUSED);
        }
        bindTemplateKeys(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        String hint = LanguageService.displayName(KEY_TEMPLATE_HINT);
        setToolTipText(hint);
        powerField.setToolTipText(hint);
        updateComboTooltips();
    }

    private void bindTemplateKeys(JComponent component, int condition) {
        InputMap inputMap = component.getInputMap(condition);
        ActionMap actionMap = component.getActionMap();
        for (int slot = TeamTemplate.MIN_SLOT; slot <= TeamTemplate.MAX_SLOT; slot++) {
            int keyCode = KeyEvent.VK_F1 + slot - 1;
            int templateSlot = slot;
            String loadKey = "c2w.loadTeamTemplate" + slot;
            String saveKey = "c2w.saveTeamTemplate" + slot;
            inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), loadKey);
            inputMap.put(KeyStroke.getKeyStroke(keyCode, InputEvent.SHIFT_DOWN_MASK), saveKey);
            actionMap.put(loadKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    loadTemplate(templateSlot);
                }
            });
            actionMap.put(saveKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    saveTemplate(templateSlot);
                }
            });
        }
    }

    /**
     * Replaces all 5 member slots with template {@code slot}, in template
     * order (fewer entries = remaining slots emptied, ids no longer in the
     * catalog are skipped, see {@link TeamTemplates#toSlots}), without
     * asking. Power, war flag and pet stay as they are, so do the totems as
     * long as the new titans still allow them (see
     * {@link #dropIneligibleTotems()}). Counts as a real
     * user change: touches lastModified and fires {@code onChanged}. Does
     * nothing at all if templates are not enabled or the slot is empty.
     */
    void loadTemplate(int slot) {
        if (templates == null) {
            return;
        }
        Optional<TeamTemplate> template = templates.template(slot);
        if (template.isEmpty()) {
            return;
        }
        hidePopups();
        List<T> slots = TeamTemplates.toSlots(template.get(), sortedCatalog, idOf, SLOT_COUNT);
        refreshing = true;
        try {
            // Full models first - a slot's current model hides whatever another slot had selected so far.
            for (int i = 0; i < SLOT_COUNT; i++) {
                combos.get(i).setModel(fullModelWithout(new HashSet<>()));
                combos.get(i).setSelectedItem(slots.get(i));
            }
        } finally {
            refreshing = false;
        }
        syncDraftFromCombos();
        dropIneligibleTotems();
        refreshComboOptions();
        updateRoleLabels();
        updateComboTooltips();
        touchLastModified();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /**
     * Saves the row's current members (non-empty slots, in slot order) as
     * template {@code slot}, right away and independently of whether the
     * surrounding dialog is saved later - templates are not guild data. An
     * occupied slot is only overwritten after confirmation (showing the old
     * and the new lineup); an empty row does nothing, as does a template
     * that already holds exactly these members.
     */
    private void saveTemplate(int slot) {
        if (templates == null) {
            return;
        }
        hidePopups();
        List<String> ids = TeamTemplates.memberIds(selectedMembers(), idOf);
        if (ids.isEmpty()) {
            return;
        }
        Optional<TeamTemplate> existing = templates.template(slot);
        if (existing.isPresent()) {
            if (existing.get().memberIds().equals(ids)) {
                return;
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    LanguageService.displayName(KEY_TEMPLATE_OVERWRITE, slot,
                            namesOf(existing.get().memberIds()), namesOf(ids)),
                    LanguageService.displayName(KEY_TEMPLATE_OVERWRITE_TITLE),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            templates.save(slot, ids);
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not save team template " + slot + " to " + templates.templateFile(), e);
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName(KEY_TEMPLATE_SAVE_FAILED, slot, e.getMessage()),
                    LanguageService.displayName(KEY_TEMPLATE_OVERWRITE_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The selected entry of every slot (null = empty), in slot order. */
    private List<T> selectedMembers() {
        List<T> selected = new ArrayList<>(SLOT_COUNT);
        for (JComboBox<T> combo : combos) {
            @SuppressWarnings("unchecked")
            T item = (T) combo.getSelectedItem();
            selected.add(item);
        }
        return selected;
    }

    /** Display names for template ids, comma-separated - an id not in the catalog is shown as is. */
    private String namesOf(List<String> ids) {
        Map<String, T> byId = new HashMap<>();
        sortedCatalog.forEach(entry -> byId.putIfAbsent(idOf.apply(entry), entry));
        return ids.stream()
                .map(id -> byId.containsKey(id) ? label.apply(byId.get(id)) : id)
                .collect(Collectors.joining(", "));
    }

    /** Closes any open dropdown of this row before its content is changed by a template key. */
    private void hidePopups() {
        combos.forEach(JComboBox::hidePopup);
        if (warFlagCombo != null) {
            warFlagCombo.hidePopup();
        }
        if (petCombo != null) {
            petCombo.hidePopup();
        }
        totemCombos.forEach(JComboBox::hidePopup);
    }

    /**
     * Resets this team back to empty: every slot combo goes back to
     * "- none -" and the power field back to 0, exactly as if each slot had
     * been cleared out by hand one at a time. Used by
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
        teamDraft.totems.clear();
        clearExtraCombo(warFlagCombo, KEY_WAR_FLAG);
        clearExtraCombo(petCombo, KEY_PET);
        for (int i = 0; i < totemCombos.size(); i++) {
            setTotemSelection(totemCombos.get(i), i + 1, null);
        }
        syncDraftFromCombos();
        refreshTotemModels();
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
     * class Javadoc on icon). Without an icon function (e.g. titan teams)
     * the renderer shows the name as text anyway, so no name is added then.
     * If templates are enabled (see {@link #enableTemplates}), the template
     * key hint is added below the name (or is the whole tooltip).
     */
    private void updateComboTooltips() {
        String hint = templates == null ? null : LanguageService.displayName(KEY_TEMPLATE_HINT);
        if (icon == null && hint == null) {
            return;
        }
        for (JComboBox<T> combo : combos) {
            @SuppressWarnings("unchecked")
            T selected = (T) combo.getSelectedItem();
            String name = icon == null || selected == null ? null : itemTooltip.apply(selected);
            if (hint == null || name == null) {
                combo.setToolTipText(name == null ? hint : name);
            } else {
                combo.setToolTipText("<html>" + escapeHtml(name) + "<br>" + escapeHtml(hint) + "</html>");
            }
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
