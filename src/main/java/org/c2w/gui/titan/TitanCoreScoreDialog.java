package org.c2w.gui.titan;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.TitanRepository;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for maintaining a titan's {@link FortMarks} - one {@link FortMark}
 * (neutral / positive / negative) per fortification of type {@link
 * FortificationType#TITAN} - the TITAN-side counterpart of {@link
 * org.c2w.gui.hero.HeroCoreScoreDialog}, with the same layout and behavior
 * (element matches instead of role matches). Opened from the "File" menu,
 * independent of the currently open guild/lineup, since the titan catalog
 * is shared across every guild.
 *
 * <p>Left side lists every known titan; picking one shows one row per titan
 * fortification (with and without a buff): its name, the element its {@link
 * ElementBuff} asks for with the automatic, read-only BUFF marker when the
 * titan's element matches, and a combo box for the manual mark. "Neutral"
 * means no entry at all. Saving writes every titan to the workspace copy of
 * {@code titanCowScore.json} (see {@code FortMarkFiles} for the format).
 *
 * <p>The toolbar's "restore defaults" button resets the marks of every titan
 * to the defaults shipped inside the jar - see {@link #onRestoreDefaults()}.
 */
public final class TitanCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "titanBuffFitScores.title";

    // The remaining texts are identical to the hero dialog's, so its language file keys are reused.
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";
    private static final String KEY_RESTORE_DEFAULTS_CONFIRM = "titanBuffFitScores.restoreDefaultsConfirm";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";

    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";

    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Avatar size for the {@link #buildDetailPanel}'s {@code titanNameLabel} icon. */
    private static final int TITAN_ICON_SIZE = 32;

    /** Language file key for the small header above the per-fortification rows (see {@link #buildDetailPanel}). */
    private static final String KEY_FORT_MARKS_HEADER = "fortMarks.header";

    /** Width reserved for a row's fortification-name label, so every combo box in the list lines up (see {@link #buildFortificationRow}). */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Width reserved for a row's element-match label (see {@link #buildFortificationRow}). */
    private static final int ELEMENT_LABEL_WIDTH = 150;

    /** Every known titan, sorted by display name - the catalog never changes while this dialog is open. */
    private final TitanRepository repository;
    private final List<Titan> titanCatalog;

    /** Every fortification a titan can be marked for: all fortifications of type {@link FortificationType#TITAN}. */
    private final List<Fortification> titanFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.TITAN)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress edits, keyed by titan id, populated lazily (one entry per
     * titan the user has actually looked at - see {@link #buildDetailPanel})
     * from that titan's current {@link Titan#fortMarks()}. A titan never
     * selected in this dialog session therefore keeps its original marks
     * completely untouched on {@link #onSaveScores()}.
     */
    private final Map<String, Map<String, FortMark>> workingMarks = new LinkedHashMap<>();

    private final DefaultListModel<Titan> titanListModel = new DefaultListModel<>();
    private final JList<Titan> titanList = new JList<>(titanListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public TitanCoreScoreDialog(Frame owner, TitanRepository repository) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);
        if (repository == null) {
            throw new IllegalArgumentException("TitanCoreScoreDialog needs a TitanRepository");
        }
        this.repository = repository;
        this.titanCatalog = repository.findAll().stream()
                .sorted(Comparator.comparing(TitanCoreScoreDialog::titanLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        if (!titanListModel.isEmpty()) {
            titanList.setSelectedIndex(0);
        }
        setSize(740, 520);
        setLocationRelativeTo(owner);
    }

    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_SCORES, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_SCORES));
        saveButton.addActionListener(e -> onSaveScores());
        buttons.add(saveButton);
        FlatButton restoreDefaultsButton = new FlatButton(IconLoader.iconFor(ICON_RESTORE_DEFAULTS, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        restoreDefaultsButton.setToolTipText(LanguageService.displayName(KEY_RESTORE_DEFAULTS));
        restoreDefaultsButton.addActionListener(e -> onRestoreDefaults());
        buttons.add(restoreDefaultsButton);
        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    private JSplitPane buildMainSplit() {
        JPanel left = new JPanel(new BorderLayout());
        titanList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Titan titan) {
                    setText(titanLabel(titan));
                }
                return this;
            }
        });
        titanCatalog.forEach(titanListModel::addElement);
        titanList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onTitanSelected(titanList.getSelectedValue());
            }
        });
        left.add(new JScrollPane(titanList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(detailContainer));
        split.setDividerLocation(220);
        return split;
    }

    private void onTitanSelected(Titan titan) {
        detailContainer.removeAll();
        if (titan != null) {
            detailContainer.add(buildDetailPanel(titan), BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /**
     * Builds the given titan's header (avatar plus {@link #titanLabel(Titan)}),
     * a small header, and one row per {@link #titanFortifications} entry, each
     * wired into {@link #workingMarks} - see {@link #buildFortificationRow}.
     */
    private JPanel buildDetailPanel(Titan titan) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel titanNameLabel = new JLabel(titanLabel(titan), IconLoader.iconFor(titan.imagePath(), TITAN_ICON_SIZE), JLabel.LEFT);
        titanNameLabel.setForeground(Color.WHITE);
        titanNameLabel.setFont(titanNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        titanNameLabel.setIconTextGap(8);
        titanNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titanNameLabel);
        panel.add(Box.createVerticalStrut(12));

        JLabel fortificationsHeader = new JLabel(LanguageService.displayName(KEY_FORT_MARKS_HEADER));
        fortificationsHeader.setForeground(Color.WHITE);
        fortificationsHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        fortificationsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fortificationsHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, FortMark> titanMarks = workingMarks.computeIfAbsent(titan.id(),
                id -> new LinkedHashMap<>(titan.fortMarks().marks()));

        for (Fortification fortification : titanFortifications) {
            JPanel row = buildFortificationRow(titan, fortification, titanMarks);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /**
     * One (titan, fortification) row: the fortification's display name, the
     * element its {@link ElementBuff} asks for (if any) plus the automatic,
     * read-only BUFF marker when the titan's element matches, and the mark
     * combo box, pre-selected to titanMarks' current entry (null = neutral).
     * Selecting neutral removes the entry.
     */
    private JPanel buildFortificationRow(Titan titan, Fortification fortification, Map<String, FortMark> titanMarks) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        ElementBuff elementBuff = fortification.buff() instanceof ElementBuff eb ? eb : null;
        boolean elementMatches = titan.matchesBuff(fortification.buff());
        String elementText = elementBuff == null ? ""
                : elementLabel(elementBuff.element()) + (elementMatches ? "  " + LanguageService.displayName("fortMark.buffMarker") : "");
        JLabel elementTextLabel = new JLabel(elementText);
        elementTextLabel.setForeground(elementMatches ? IconLoader.GREEN : Color.WHITE);
        elementTextLabel.setPreferredSize(new Dimension(ELEMENT_LABEL_WIDTH, elementTextLabel.getPreferredSize().height));
        row.add(elementTextLabel);

        JComboBox<FortMark> combo = buildFortMarkCombo();
        combo.setSelectedItem(titanMarks.get(fortification.id()));
        combo.addActionListener(e -> {
            FortMark selected = (FortMark) combo.getSelectedItem();
            if (selected == null) {
                titanMarks.remove(fortification.id());
            } else {
                titanMarks.put(fortification.id(), selected);
            }
        });
        row.add(combo);

        return row;
    }

    /** A combo box offering neutral (null), {@link FortMark#POSITIVE} and {@link FortMark#NEGATIVE}, rendered via language keys {@code fortMark.<NAME>}. */
    private static JComboBox<FortMark> buildFortMarkCombo() {
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
        return combo;
    }

    /**
     * The localized display name for a {@link TitanElement} (language file
     * key {@code titanElement.<NAME>}) - used instead of {@link
     * TitanElement#name()} so this element indicator is translated like every
     * other UI string.
     */
    private static String elementLabel(TitanElement element) {
        return LanguageService.displayName("titanElement." + element.name());
    }

    /**
     * Handler of the "restore defaults" toolbar button: after a confirmation,
     * replaces the working values of <em>every</em> titan (not only the ones
     * opened so far) with the shipped defaults from {@link
     * TitanRepository#loadDefaultCowScores()} and refreshes the detail panel
     * of the selected titan. Like every other edit in this dialog, nothing is
     * written until the user saves (see {@link #onSaveScores()}), so closing
     * the dialog without saving discards the reset again.
     */
    private void onRestoreDefaults() {
        int answer = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName(KEY_RESTORE_DEFAULTS_CONFIRM),
                LanguageService.displayName("common.confirmTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        Map<String, FortMarks> defaults = repository.loadDefaultCowScores();
        for (Titan titan : titanCatalog) {
            workingMarks.put(titan.id(), new LinkedHashMap<>(defaults.getOrDefault(titan.id(), FortMarks.NONE).marks()));
        }
        onTitanSelected(titanList.getSelectedValue());
        Logger.log("Titan CowScore dialog: restored the default values for every titan (not saved yet)");
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    /**
     * Writes every titan's current {@link #workingMarks} entry back into a
     * fresh {@link Titan} (untouched for a titan never opened in this dialog
     * session) and saves the whole catalog via {@link
     * TitanRepository#saveCowScores} - which only writes {@code
     * titanCowScore.json}, never {@code titans.json}.
     */
    private void onSaveScores() {
        List<Titan> updatedCatalog = titanCatalog.stream()
                .map(titan -> {
                    Map<String, FortMark> marks = workingMarks.get(titan.id());
                    FortMarks fortMarks = marks == null ? titan.fortMarks() : new FortMarks(marks);
                    return new Titan(titan.id(), titan.element(), titan.imagePath(), fortMarks);
                })
                .toList();

        try {
            repository.saveCowScores(updatedCatalog);
            Logger.log("Saved: titanCowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
