package org.c2w.gui.pet;

import org.c2w.data.model.CowScore;
import org.c2w.data.model.CowScoreTier;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Pet;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.PetRepository;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for maintaining a pet's {@link CowScore} - {@link Pet#generalScore()}
 * and {@link Pet#buffFitScores()} - the PET-side counterpart of {@code
 * HeroCoreScoreDialog}. Opened from the main window's "File" menu,
 * independent of the currently open guild/lineup, since the pet catalog is
 * shared across every guild.
 *
 * <p>Pets only ever appear in {@link FortificationType#HERO} fortifications
 * (they accompany a hero team), so - exactly like the hero dialog - the
 * detail panel shows one {@link CowScoreTier} combo box for the pet's
 * {@link Pet#generalScore()} (used for buff-less fortifications), followed by
 * one row per HERO fortification that has a buff. Titan fortifications are
 * never listed. Unlike heroes, pets have no roles, so there is no
 * role-match column.
 *
 * <p>Picking {@link CowScoreTier#GOOD} in a fortification row is equivalent
 * to having no override: it removes the entry from the pet's working scores
 * (see {@link #buildFortificationRow}). Saving writes every pet to the
 * workspace copy of {@code petCowScore.json} (never {@code pets.json} - see
 * {@link PetRepository}'s class Javadoc). The "restore defaults" button
 * resets every pet to the defaults shipped inside the jar - see {@link
 * #onRestoreDefaults()}.
 */
public final class PetCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "petBuffFitScores.title";

    // Shared with the hero/titan dialogs - identical wording for all three.
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_GENERAL_SCORE = "heroBuffFitScores.generalScore";
    private static final String KEY_BUFF_FIT_SCORES_HEADER = "heroBuffFitScores.buffFitScoresHeader";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";

    /** Pet-specific confirmation question of {@link #onRestoreDefaults()}. */
    private static final String KEY_RESTORE_DEFAULTS_CONFIRM = "petBuffFitScores.restoreDefaultsConfirm";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Avatar size for the pet name label in {@link #buildDetailPanel}. */
    private static final int PET_ICON_SIZE = 32;

    /** Width reserved for a row's name label, so every combo box in the list lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Every known pet, sorted by display name - the catalog never changes while this dialog is open. */
    private final List<Pet> petCatalog = PetRepository.findAll().stream()
            .sorted(Comparator.comparing(PetCoreScoreDialog::petLabel, String.CASE_INSENSITIVE_ORDER))
            .toList();

    /** Every fortification a pet's {@link Pet#buffFitScores()} can apply to: HERO fortifications with a buff. */
    private final List<Fortification> buffedFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.HERO && f.buff() != null)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress buffFitScores edits, keyed by pet id, populated lazily for
     * every pet the user has actually looked at. A pet never selected keeps
     * its original map untouched on {@link #onSaveScores()}.
     */
    private final Map<String, Map<String, CowScoreTier>> workingScores = new LinkedHashMap<>();

    /** In-progress generalScore edits, keyed by pet id - populated lazily like {@link #workingScores}. */
    private final Map<String, CowScoreTier> workingGeneralScores = new LinkedHashMap<>();

    private final DefaultListModel<Pet> petListModel = new DefaultListModel<>();
    private final JList<Pet> petList = new JList<>(petListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public PetCoreScoreDialog(Frame owner) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);

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

    /**
     * Builds the given pet's header (avatar plus display name), its {@link
     * Pet#generalScore()} row, a small header and one row per {@link
     * #buffedFortifications} entry - see {@link #buildFortificationRow}.
     */
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

        JPanel generalScoreRow = buildGeneralScoreRow(pet);
        generalScoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(generalScoreRow);
        panel.add(Box.createVerticalStrut(12));

        JLabel buffFitScoresHeader = new JLabel(LanguageService.displayName(KEY_BUFF_FIT_SCORES_HEADER));
        buffFitScoresHeader.setForeground(Color.WHITE);
        buffFitScoresHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        buffFitScoresHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(buffFitScoresHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, CowScoreTier> petScores = workingScores.computeIfAbsent(pet.id(),
                id -> new LinkedHashMap<>(pet.buffFitScores()));

        for (Fortification fortification : buffedFortifications) {
            JPanel row = buildFortificationRow(fortification, petScores);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /** The pet's {@link Pet#generalScore()} row: a label plus a {@link CowScoreTier} combo box, stored as selected. */
    private JPanel buildGeneralScoreRow(Pet pet) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel label = new JLabel(LanguageService.displayName(KEY_GENERAL_SCORE));
        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, label.getPreferredSize().height));
        row.add(label);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(workingGeneralScores.computeIfAbsent(pet.id(), id -> pet.generalScore()));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            workingGeneralScores.put(pet.id(), selected == null ? CowScoreTier.GOOD : selected);
        });
        row.add(combo);
        return row;
    }

    /**
     * One (pet, fortification) row: the fortification's display name and a
     * {@link CowScoreTier} combo box pre-selected to the pet's current
     * override (or {@link CowScoreTier#GOOD} if none). Selecting {@link
     * CowScoreTier#GOOD} removes the override instead of storing it.
     */
    private JPanel buildFortificationRow(Fortification fortification, Map<String, CowScoreTier> petScores) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(petScores.getOrDefault(fortification.id(), CowScoreTier.GOOD));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            if (selected == null || selected == CowScoreTier.GOOD) {
                petScores.remove(fortification.id());
            } else {
                petScores.put(fortification.id(), selected);
            }
        });
        row.add(combo);
        return row;
    }

    /** A {@link CowScoreTier} combo box listing all tiers, rendered as e.g. "Good (0.8)". */
    private static JComboBox<CowScoreTier> buildScoreTierCombo() {
        JComboBox<CowScoreTier> combo = new JComboBox<>(CowScoreTier.values());
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CowScoreTier tier) {
                    setText(LanguageService.displayName("scoreTier." + tier.name()) + " (" + tier.value() + ")");
                }
                return this;
            }
        });
        return combo;
    }

    /**
     * Handler of the "restore defaults" button: after a confirmation, replaces
     * the working values of every pet with the shipped defaults from {@link
     * PetRepository#loadDefaultCowScores()}. Nothing is written until the
     * user saves.
     */
    private void onRestoreDefaults() {
        int answer = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName(KEY_RESTORE_DEFAULTS_CONFIRM),
                LanguageService.displayName("common.confirmTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        Map<String, CowScore> defaults = PetRepository.loadDefaultCowScores();
        for (Pet pet : petCatalog) {
            CowScore cowScore = defaults.getOrDefault(pet.id(), CowScore.DEFAULT);
            workingGeneralScores.put(pet.id(), cowScore.generalScore());
            // GOOD means "no override" in this dialog - see buildFortificationRow.
            Map<String, CowScoreTier> buffFitScores = new LinkedHashMap<>();
            cowScore.buffFitScores().forEach((fortificationId, tier) -> {
                if (tier != CowScoreTier.GOOD) {
                    buffFitScores.put(fortificationId, tier);
                }
            });
            workingScores.put(pet.id(), buffFitScores);
        }
        onPetSelected(petList.getSelectedValue());
        Logger.log("Pet CowScore dialog: restored the default values for every pet (not saved yet)");
    }

    private static String petLabel(Pet pet) {
        return LanguageService.displayName(pet.id());
    }

    /**
     * Writes every pet's working values back into a fresh {@link Pet} (the
     * original values for a pet never opened) and saves the whole catalog via
     * {@link PetRepository#saveCowScores} - which only writes {@code
     * petCowScore.json}, never {@code pets.json}.
     */
    private void onSaveScores() {
        List<Pet> updatedCatalog = petCatalog.stream()
                .map(pet -> {
                    Map<String, CowScoreTier> scores = workingScores.get(pet.id());
                    Map<String, CowScoreTier> buffFitScores = scores == null ? pet.buffFitScores() : scores;
                    CowScoreTier generalScore = workingGeneralScores.getOrDefault(pet.id(), pet.generalScore());
                    return new Pet(pet.id(), pet.imagePath(), new CowScore(generalScore, buffFitScores));
                })
                .toList();

        try {
            PetRepository.saveCowScores(updatedCatalog);
            Logger.log("Saved: petCowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
