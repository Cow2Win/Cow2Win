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
 * The shared layout and logic of the four {@link CowScorePanel}s: the catalog list on the
 * left, on the right the selected entry's header (avatar plus name) and one row per
 * fortification of one {@link FortificationType}, each row with the fortification's name
 * followed by whatever the subclass adds in {@link #addRowControls} (a mark combo box or a
 * "marked" checkbox).
 *
 * <p>Edits go to {@link #workingMarks}, populated lazily per entry the user has actually
 * looked at (or for every entry on {@link #restoreDefaults()}); an entry never opened keeps
 * its original marks completely untouched on {@link #save()}.
 *
 * @param <T> the catalog entry type (hero, titan, pet or war flag)
 */
public abstract class AbstractCowScorePanel<T> extends JPanel implements CowScorePanel {

    /** Language file key for the small header above the per-fortification rows. */
    private static final String KEY_FORT_MARKS_HEADER = "fortMarks.header";

    /** Language file key for the tooltip of a "marked" checkbox (see {@link #addMarkedCheckBox}). */
    private static final String KEY_MARKED_TOOLTIP = "fortMarks.markedTooltip";

    /** Avatar size for the selected entry's name label. */
    private static final int AVATAR_SIZE = 32;

    /** Width reserved for a row's fortification-name label, so every control in the list lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Width reserved for a row's buff label (see {@link #addBuffLabel}). */
    private static final int BUFF_LABEL_WIDTH = 150;

    /** Every fortification an entry can be marked for, sorted by display name. */
    private final List<Fortification> fortifications;

    /** Every catalog entry, sorted by display name - replaced by the saved catalog after each {@link #save()}. */
    private List<T> catalog = List.of();

    /** In-progress edits: fortification id -&gt; mark per entry id, see the class Javadoc. */
    private final Map<String, Map<String, FortMark>> workingMarks = new LinkedHashMap<>();

    private final DefaultListModel<T> listModel = new DefaultListModel<>();
    private final JList<T> list = new JList<>(listModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());
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
    protected final void init(List<T> entries, int dividerLocation) {
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

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(detailContainer));
        split.setDividerLocation(dividerLocation);
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

    /** Adds the controls after the fortification's name to {@code row}, e.g. via {@link #addFortMarkCombo}. */
    protected abstract void addRowControls(JPanel row, T entry, Fortification fortification, Map<String, FortMark> marks);

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
     * with the shipped defaults and refreshes the detail panel. Nothing is written until
     * {@link #save()}.
     */
    @Override
    public void restoreDefaults() {
        Map<String, FortMarks> defaults = loadDefaults();
        for (T entry : catalog) {
            workingMarks.put(id(entry), workingCopy(defaults.getOrDefault(id(entry), FortMarks.NONE)));
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

    /** A combo box neutral/positive/negative, pre-selected to {@code marks}' current entry and wired into it. */
    protected final void addFortMarkCombo(JPanel row, Fortification fortification, Map<String, FortMark> marks) {
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
        combo.setSelectedItem(marks.get(fortification.id()));
        combo.addActionListener(e -> setMark(marks, fortification.id(), (FortMark) combo.getSelectedItem()));
        row.add(combo);
    }

    /** A "marked" checkbox ({@link FortMark#POSITIVE} or nothing) wired into {@code marks}. */
    protected final void addMarkedCheckBox(JPanel row, Fortification fortification, Map<String, FortMark> marks) {
        JCheckBox checkBox = new JCheckBox();
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

    private void refreshDetail() {
        detailContainer.removeAll();
        T entry = list.getSelectedValue();
        if (entry != null) {
            detailContainer.add(buildDetailPanel(entry), BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /** The entry's header (avatar plus name), a small header and one row per {@link #fortifications} entry. */
    private JPanel buildDetailPanel(T entry) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel nameLabel = new JLabel(label(entry), IconLoader.iconFor(imagePath(entry), AVATAR_SIZE), JLabel.LEFT);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setIconTextGap(8);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(12));

        JLabel fortificationsHeader = new JLabel(LanguageService.displayName(KEY_FORT_MARKS_HEADER));
        fortificationsHeader.setForeground(Color.WHITE);
        fortificationsHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        fortificationsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fortificationsHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, FortMark> marks = workingMarks.computeIfAbsent(id(entry), id -> workingCopy(fortMarks(entry)));
        for (Fortification fortification : fortifications) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            JLabel fortificationLabel = new JLabel(LanguageService.displayName(fortification.id()));
            fortificationLabel.setForeground(Color.WHITE);
            fortificationLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, fortificationLabel.getPreferredSize().height));
            row.add(fortificationLabel);
            addRowControls(row, entry, fortification, marks);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }
}
