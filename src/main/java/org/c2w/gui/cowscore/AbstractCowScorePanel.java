package org.c2w.gui.cowscore;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shared layout and logic of the hero, titan, pet and war flag {@link CowScorePanel}s: the
 * catalog list on the left (as wide as in every other tab, see {@link #setListWidth}), on the
 * right the selected entry's header (avatar plus name), a header row with the column titles
 * ({@link #columnHeaderKeys()}) and one row per fortification of one {@link FortificationType},
 * each row with the fortification's name followed by whatever the subclass adds in
 * {@link #addRowControls} (buff label and mark combo box, or a "marked" checkbox).
 *
 * <p>Edits go to {@link #workingMarks}, populated lazily per entry the user has actually
 * looked at (or for every entry on {@link #restoreDefaults()}); an entry never opened keeps
 * its original marks completely untouched on {@link #save()}.
 *
 * <p>A hero/titan whose role/element matches a fortification's buff gets no mark there
 * ({@link #buffMatches}): the buff already counts on its own, so the row shows a locked
 * "Positive (buff)" instead of the combo box, and a mark found in the working copy is removed
 * when the entry is opened (making the tab unsaved).
 *
 * @param <T> the catalog entry type (hero, titan, pet or war flag)
 */
public abstract class AbstractCowScorePanel<T> extends JPanel implements CowScorePanel {

    /** Language file key for the tooltip of a "marked" checkbox (see {@link #addMarkedCheckBox}). */
    private static final String KEY_MARKED_TOOLTIP = "fortMarks.markedTooltip";

    /** Language file key for the first column title. */
    private static final String KEY_COLUMN_FORTIFICATION = "fortMarks.column.fortification";

    /** Avatar size for the selected entry's name label - shared with {@code HeroComboPanel}. */
    public static final int AVATAR_SIZE = 40;

    /** Font size of the selected entry's name label - shared with {@code HeroComboPanel}. */
    public static final float NAME_FONT_SIZE = 18f;

    /** Width reserved for a row's fortification-name label, so every control in the list lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Width reserved for a row's buff label (see {@link #addBuffLabel}). */
    private static final int BUFF_LABEL_WIDTH = 150;

    /** Every fortification an entry can be marked for, sorted by display name. */
    private final List<Fortification> fortifications;

    /** Every catalog entry, sorted by display name - replaced by the saved catalog after each {@link #save()}. */
    private List<T> catalog = List.of();

    /** In-progress edits: entry id -&gt; mark per fortification id, see the class Javadoc. */
    private final Map<String, Map<String, FortMark>> workingMarks = new LinkedHashMap<>();

    private final DefaultListModel<T> listModel = new DefaultListModel<>();
    private final JList<T> list = new JList<>(listModel);
    private final JScrollPane listScrollPane = new JScrollPane(list);
    private final JPanel detailContainer = new JPanel(new BorderLayout());
    private final JScrollPane detailScrollPane = new JScrollPane(detailContainer);
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final List<ChangeListener> changeListeners = new ArrayList<>();
    private boolean unsaved;

    protected AbstractCowScorePanel(FortificationType fortificationType) {
        super(new BorderLayout());
        this.fortifications = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == fortificationType)
                .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Fills the list with {@code entries} (sorted by display name) and selects the first one.
     * Called by the subclass at the end of its constructor, once its own fields are set.
     */
    protected final void init(List<T> entries) {
        catalog = entries.stream()
                .sorted(Comparator.comparing(this::label, String.CASE_INSENSITIVE_ORDER))
                .toList();

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                @SuppressWarnings("unchecked")
                T entry = (T) value;
                if (entry != null) {
                    setText(label(entry));
                }
                return this;
            }
        });
        catalog.forEach(listModel::addElement);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshDetail();
            }
        });

        split.setLeftComponent(listScrollPane);
        split.setRightComponent(detailScrollPane);
        split.setResizeWeight(0);
        setListWidth(CowScoreLayout.MIN_LIST_WIDTH);
        add(split, BorderLayout.CENTER);

        if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    // --- what a subclass provides ---

    protected abstract String id(T entry);

    protected abstract String imagePath(T entry);

    protected abstract FortMarks fortMarks(T entry);

    /** A copy of {@code entry} with {@code fortMarks}. */
    protected abstract T withFortMarks(T entry, FortMarks fortMarks);

    /** The shipped default marks, keyed by entry id. */
    protected abstract Map<String, FortMarks> loadDefaults();

    /** Writes the whole catalog through the repository. */
    protected abstract void saveCatalog(List<T> updatedCatalog) throws IOException;

    /** File name for the log message after saving, e.g. {@code cowScore.json}. */
    protected abstract String fileName();

    /** Language file keys of the column titles after "Fortification" - one per control {@link #addRowControls} adds. */
    protected abstract List<String> columnHeaderKeys();

    /** Adds the controls after the fortification's name to {@code row}, e.g. via {@link #addFortMarkCombo}. */
    protected abstract void addRowControls(JPanel row, T entry, Fortification fortification, Map<String, FortMark> marks);

    /** True if {@code entry} matches the buff of {@code fortification} - then it carries no mark there. */
    protected boolean buffMatches(T entry, Fortification fortification) {
        return false;
    }

    /**
     * The working copy of {@code fortMarks} - by default all marks; checkbox panels keep only
     * {@link FortMark#POSITIVE} (see {@link #positiveMarksOnly}).
     */
    protected Map<String, FortMark> workingCopy(FortMarks fortMarks) {
        return new LinkedHashMap<>(fortMarks.marks());
    }

    // --- CowScorePanel ---

    @Override
    public boolean hasUnsavedChanges() {
        return unsaved;
    }

    /**
     * Writes every entry's current {@link #workingMarks} back into a fresh entry (untouched
     * for an entry never opened) and saves the whole catalog via {@link #saveCatalog}.
     */
    @Override
    public void save() throws IOException {
        List<T> updatedCatalog = catalog.stream()
                .map(entry -> {
                    Map<String, FortMark> marks = workingMarks.get(id(entry));
                    return marks == null ? entry : withFortMarks(entry, new FortMarks(marks));
                })
                .toList();
        saveCatalog(updatedCatalog);
        catalog = updatedCatalog;
        Logger.log("Saved: " + fileName());
        setUnsaved(false);
    }

    /**
     * Replaces the working values of <em>every</em> entry (not only the ones opened so far)
     * with the shipped defaults - without marks on buff-matching fortifications - and refreshes
     * the detail panel. Nothing is written until {@link #save()}.
     */
    @Override
    public void restoreDefaults() {
        Map<String, FortMarks> defaults = loadDefaults();
        for (T entry : catalog) {
            Map<String, FortMark> marks = workingCopy(defaults.getOrDefault(id(entry), FortMarks.NONE));
            removeBuffMatchingMarks(entry, marks);
            workingMarks.put(id(entry), marks);
        }
        refreshDetail();
        Logger.log("CowScore " + fileName() + ": restored the default values for every entry (not saved yet)");
        setUnsaved(true);
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    @Override
    public JComponent component() {
        return this;
    }

    @Override
    public List<String> listLabels() {
        return catalog.stream().map(this::label).toList();
    }

    @Override
    public void setListWidth(int width) {
        listScrollPane.setPreferredSize(new Dimension(width, CowScoreLayout.LIST_PREFERRED_HEIGHT));
        listScrollPane.setMinimumSize(new Dimension(width, 0));
        split.setDividerLocation(width + split.getInsets().left);
    }

    @Override
    public JScrollPane detailScrollPane() {
        return detailScrollPane;
    }

    /** The marks {@link #save()} would write for the entry with {@code entryId} right now. */
    public FortMarks currentMarks(String entryId) {
        Map<String, FortMark> marks = workingMarks.get(entryId);
        if (marks != null) {
            return new FortMarks(marks);
        }
        return catalog.stream().filter(e -> id(e).equals(entryId)).findFirst()
                .map(this::fortMarks).orElse(FortMarks.NONE);
    }

    /** Id of the entry selected in the list, or null. */
    public String selectedEntryId() {
        T selected = list.getSelectedValue();
        return selected == null ? null : id(selected);
    }

    /** Selects the entry with {@code entryId} (shows its detail) - e.g. for tests. */
    public void selectEntry(String entryId) {
        for (int i = 0; i < listModel.size(); i++) {
            if (id(listModel.get(i)).equals(entryId)) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    // --- helpers for subclasses ---

    /** Sets {@code fortificationId}'s mark in an entry's working {@code marks} - null = neutral - and marks the panel unsaved if it changed. */
    protected final void setMark(Map<String, FortMark> marks, String fortificationId, FortMark mark) {
        FortMark previous = mark == null ? marks.remove(fortificationId) : marks.put(fortificationId, mark);
        if (!Objects.equals(previous, mark)) {
            setUnsaved(true);
        }
    }

    /** The role/element label of a hero/titan row, green with the BUFF marker when the entry matches the fortification's buff. */
    protected static void addBuffLabel(JPanel row, String buffText, boolean matches) {
        String text = buffText == null ? ""
                : buffText + (matches ? "  " + LanguageService.displayName("fortMark.buffMarker") : "");
        JLabel label = new JLabel(text);
        label.setForeground(matches ? IconLoader.GREEN : Color.WHITE);
        label.setPreferredSize(new Dimension(BUFF_LABEL_WIDTH, label.getPreferredSize().height));
        row.add(label);
    }

    /**
     * A combo box neutral/positive/negative, pre-selected to {@code marks}' current entry and
     * wired into it - or, if {@code buffMatch}, a locked combo box showing "Positive (buff)" in
     * green with the tooltip {@code lockedTooltipKey} (the buff already counts, no mark is kept).
     * Named {@code "mark:<fortification id>"}.
     */
    protected final void addFortMarkCombo(JPanel row, Fortification fortification, Map<String, FortMark> marks,
                                          boolean buffMatch, String lockedTooltipKey) {
        JComboBox<FortMark> combo = new JComboBox<>(FortMark.values());
        combo.insertItemAt(null, 0);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(LanguageService.displayName("fortMark." + (value instanceof FortMark mark ? mark.name() : "NONE")));
                return this;
            }
        });
        combo.setName("mark:" + fortification.id());
        if (buffMatch) {
            Dimension size = combo.getPreferredSize();
            JComboBox<String> locked = new JComboBox<>(new String[]{LanguageService.displayName("fortMark.BUFF")});
            locked.setRenderer(new DefaultListCellRenderer() {
                @Override
                public void setForeground(Color color) {
                    // Always green like the buff label - also while the combo box is disabled.
                    super.setForeground(IconLoader.GREEN);
                }
            });
            locked.setEnabled(false);
            locked.setToolTipText(LanguageService.displayName(lockedTooltipKey));
            locked.setName(combo.getName());
            locked.setPreferredSize(new Dimension(Math.max(size.width, locked.getPreferredSize().width), size.height));
            row.add(locked);
            return;
        }
        combo.setSelectedItem(marks.get(fortification.id()));
        combo.addActionListener(e -> setMark(marks, fortification.id(), (FortMark) combo.getSelectedItem()));
        row.add(combo);
    }

    /** A "marked" checkbox ({@link FortMark#POSITIVE} or nothing) wired into {@code marks}, named {@code "mark:<fortification id>"}. */
    protected final void addMarkedCheckBox(JPanel row, Fortification fortification, Map<String, FortMark> marks) {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setName("mark:" + fortification.id());
        checkBox.setToolTipText(LanguageService.displayName(KEY_MARKED_TOOLTIP));
        checkBox.setSelected(marks.get(fortification.id()) == FortMark.POSITIVE);
        checkBox.addActionListener(e ->
                setMark(marks, fortification.id(), checkBox.isSelected() ? FortMark.POSITIVE : null));
        row.add(checkBox);
    }

    /** The {@link FortMark#POSITIVE} marks of {@code fortMarks} - the working copy of the checkbox panels. */
    protected static Map<String, FortMark> positiveMarksOnly(FortMarks fortMarks) {
        Map<String, FortMark> marks = new LinkedHashMap<>();
        fortMarks.marks().forEach((fortificationId, mark) -> {
            if (mark == FortMark.POSITIVE) {
                marks.put(fortificationId, mark);
            }
        });
        return marks;
    }

    // --- private ---

    private String label(T entry) {
        return LanguageService.displayName(id(entry));
    }

    private void setUnsaved(boolean unsaved) {
        this.unsaved = unsaved;
        ChangeEvent event = new ChangeEvent(this);
        List.copyOf(changeListeners).forEach(l -> l.stateChanged(event));
    }

    /** Removes the marks on fortifications whose buff {@code entry} matches; true if any was removed. */
    private boolean removeBuffMatchingMarks(T entry, Map<String, FortMark> marks) {
        boolean removed = false;
        for (Fortification fortification : fortifications) {
            if (buffMatches(entry, fortification) && marks.remove(fortification.id()) != null) {
                removed = true;
            }
        }
        return removed;
    }

    private void refreshDetail() {
        detailContainer.removeAll();
        T entry = list.getSelectedValue();
        if (entry != null) {
            detailContainer.add(buildDetailPanel(entry), BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /** The entry's header (avatar plus name), the column header row and one row per {@link #fortifications} entry. */
    private JPanel buildDetailPanel(T entry) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel nameLabel = CowScoreLayout.nameLabel(label(entry), IconLoader.iconFor(imagePath(entry), AVATAR_SIZE));
        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(12));

        Map<String, FortMark> marks = workingMarks.computeIfAbsent(id(entry), id -> workingCopy(fortMarks(entry)));
        if (removeBuffMatchingMarks(entry, marks)) {
            Logger.log("CowScore " + fileName() + ": removed marks of '" + id(entry)
                    + "' on buff-matching fortifications (not saved yet) - the buff already counts");
            setUnsaved(true);
        }
        List<JPanel> rows = new ArrayList<>();
        for (Fortification fortification : fortifications) {
            JPanel row = newRow();
            JLabel fortificationLabel = new JLabel(LanguageService.displayName(fortification.id()));
            fortificationLabel.setForeground(Color.WHITE);
            fortificationLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, fortificationLabel.getPreferredSize().height));
            row.add(fortificationLabel);
            addRowControls(row, entry, fortification, marks);
            rows.add(row);
        }

        panel.add(buildHeaderRow(rows.isEmpty() ? null : rows.get(0)));
        panel.add(Box.createVerticalStrut(4));
        rows.forEach(panel::add);
        return panel;
    }

    /**
     * The column titles, laid out like a fortification row: "Fortification" as wide as the
     * names, each further title as wide as the control below it in {@code firstRow}.
     */
    private JPanel buildHeaderRow(JPanel firstRow) {
        JPanel header = newRow();
        header.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        header.setName("columnHeader");
        header.add(columnTitle(KEY_COLUMN_FORTIFICATION, NAME_LABEL_WIDTH));
        List<String> keys = columnHeaderKeys();
        for (int i = 0; i < keys.size(); i++) {
            int index = i + 1;
            int width = firstRow != null && firstRow.getComponentCount() > index
                    ? firstRow.getComponent(index).getPreferredSize().width : 0;
            header.add(columnTitle(keys.get(i), width));
        }
        return header;
    }

    private static JLabel columnTitle(String key, int width) {
        JLabel title = new JLabel(LanguageService.displayName(key));
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setPreferredSize(new Dimension(Math.max(width, title.getPreferredSize().width), title.getPreferredSize().height));
        return title;
    }

    private static JPanel newRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }
}
