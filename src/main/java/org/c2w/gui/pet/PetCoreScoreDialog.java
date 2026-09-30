package org.c2w.gui.pet;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Pet;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.PetRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dialog for maintaining a pet's {@link FortMarks}: for every fortification of
 * type {@link FortificationType#HERO} a "marked" checkbox - a pet marked for a
 * fortification adds a bonus to its team's CowScore there (see {@code
 * TeamScoreCalculator}). Successor of the former tier-based
 * generalScore/buffFitScores (CowScore concept of 2026-09-30). Opened from the
 * toolbar, independent of the currently open guild/lineup. Saving writes the
 * workspace copy of {@code petCowScore.json} (see {@code FortMarkFiles}).
 */
public final class PetCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "petBuffFitScores.title";

    // Shared with the hero/titan/war flag dialogs - identical wording for all of them.
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";
    private static final String KEY_FORTIFICATIONS_HEADER = "fortMarks.header";
    private static final String KEY_MARKED_TOOLTIP = "fortMarks.markedTooltip";

    /** Pet-specific confirmation question of {@link #onRestoreDefaults()}. */
    private static final String KEY_RESTORE_DEFAULTS_CONFIRM = "petBuffFitScores.restoreDefaultsConfirm";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Avatar size for the pet name label in {@link #buildDetailPanel}. */
    private static final int PET_ICON_SIZE = 32;

    /** Width reserved for a row's fortification-name label, so every checkbox lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Every known pet, sorted by display name - the catalog never changes while this dialog is open. */
    private final PetRepository repository;
    private final List<Pet> petCatalog;

    /** Every fortification a pet can be marked for: all fortifications of type {@link FortificationType#HERO}. */
    private final List<Fortification> heroFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.HERO)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress edits: the ids of the fortifications each pet is marked for,
     * keyed by pet id - populated lazily for every pet opened in this dialog
     * session; a pet never opened keeps its original marks on save.
     */
    private final Map<String, Set<String>> workingMarks = new LinkedHashMap<>();

    private final DefaultListModel<Pet> petListModel = new DefaultListModel<>();
    private final JList<Pet> petList = new JList<>(petListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public PetCoreScoreDialog(Frame owner, PetRepository repository) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);
        if (repository == null) {
            throw new IllegalArgumentException("PetCoreScoreDialog needs a PetRepository");
        }
        this.repository = repository;
        this.petCatalog = repository.findAll().stream()
                .sorted(Comparator.comparing(PetCoreScoreDialog::petLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        if (!petCatalog.isEmpty()) {
            petList.setSelectedIndex(0);
        }
        setSize(620, 520);
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
        petList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Pet pet) {
                    setText(petLabel(pet));
                }
                return this;
            }
        });
        petCatalog.forEach(petListModel::addElement);
        petList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onPetSelected(petList.getSelectedValue());
            }
        });
        left.add(new JScrollPane(petList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(detailContainer));
        split.setDividerLocation(180);
        return split;
    }

    private void onPetSelected(Pet pet) {
        detailContainer.removeAll();
        if (pet != null) {
            detailContainer.add(buildDetailPanel(pet), BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /** The pet's header (avatar plus name), a small header and one checkbox row per {@link #heroFortifications} entry. */
    private JPanel buildDetailPanel(Pet pet) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel petNameLabel = new JLabel(petLabel(pet), IconLoader.iconFor(pet.imagePath(), PET_ICON_SIZE), JLabel.LEFT);
        petNameLabel.setForeground(Color.WHITE);
        petNameLabel.setFont(petNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        petNameLabel.setIconTextGap(8);
        petNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(petNameLabel);
        panel.add(Box.createVerticalStrut(12));

        JLabel fortificationsHeader = new JLabel(LanguageService.displayName(KEY_FORTIFICATIONS_HEADER));
        fortificationsHeader.setForeground(Color.WHITE);
        fortificationsHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        fortificationsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fortificationsHeader);
        panel.add(Box.createVerticalStrut(4));

        Set<String> marked = workingMarks.computeIfAbsent(pet.id(), id -> markedIds(pet.fortMarks()));
        for (Fortification fortification : heroFortifications) {
            JPanel row = buildFortificationRow(fortification, marked);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /** One (pet, fortification) row: the fortification's display name and a "marked" checkbox wired into {@code marked}. */
    private JPanel buildFortificationRow(Fortification fortification, Set<String> marked) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setToolTipText(LanguageService.displayName(KEY_MARKED_TOOLTIP));
        checkBox.setSelected(marked.contains(fortification.id()));
        checkBox.addActionListener(e -> {
            if (checkBox.isSelected()) {
                marked.add(fortification.id());
            } else {
                marked.remove(fortification.id());
            }
        });
        row.add(checkBox);
        return row;
    }

    /**
     * Handler of the "restore defaults" toolbar button: after a confirmation,
     * replaces the working marks of every pet with the shipped defaults.
     * Nothing is written until the user saves.
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
        for (Pet pet : petCatalog) {
            workingMarks.put(pet.id(), markedIds(defaults.getOrDefault(pet.id(), FortMarks.NONE)));
        }
        onPetSelected(petList.getSelectedValue());
        Logger.log("Pet CowScore dialog: restored the default values for every pet (not saved yet)");
    }

    /** The ids of the fortifications marked {@link FortMark#POSITIVE} in {@code fortMarks}. */
    private static Set<String> markedIds(FortMarks fortMarks) {
        Set<String> ids = new LinkedHashSet<>();
        fortMarks.marks().forEach((fortificationId, mark) -> {
            if (mark == FortMark.POSITIVE) {
                ids.add(fortificationId);
            }
        });
        return ids;
    }

    private static String petLabel(Pet pet) {
        return LanguageService.displayName(pet.id());
    }

    /** Writes every pet's current {@link #workingMarks} back and saves the catalog via {@link PetRepository#saveCowScores}. */
    private void onSaveScores() {
        List<Pet> updatedCatalog = petCatalog.stream()
                .map(pet -> {
                    Set<String> marked = workingMarks.get(pet.id());
                    if (marked == null) {
                        return pet;
                    }
                    Map<String, FortMark> marks = new LinkedHashMap<>();
                    marked.forEach(fortificationId -> marks.put(fortificationId, FortMark.POSITIVE));
                    return new Pet(pet.id(), pet.imagePath(), new FortMarks(marks));
                })
                .toList();

        try {
            repository.saveCowScores(updatedCatalog);
            Logger.log("Saved: petCowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
