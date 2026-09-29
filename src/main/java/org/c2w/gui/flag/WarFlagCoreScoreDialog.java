package org.c2w.gui.flag;

import org.c2w.data.model.CowScore;
import org.c2w.data.model.CowScoreTier;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.WarFlag;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.WarFlagRepository;
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
 * Dialog for maintaining a war flag's {@link CowScore} - {@link WarFlag#generalScore()}
 * and {@link WarFlag#buffFitScores()} - the WAR-FLAG-side counterpart of {@code
 * HeroCoreScoreDialog}. Opened from the main window's "File" menu,
 * independent of the currently open guild/lineup, since the war flag catalog is
 * shared across every guild.
 *
 * <p>War flags only ever appear in {@link FortificationType#HERO} fortifications
 * (they accompany a hero team), so - exactly like the hero dialog - the
 * detail panel shows one {@link CowScoreTier} combo box for the war flag's
 * {@link WarFlag#generalScore()} (used for buff-less fortifications), followed by
 * one row per HERO fortification that has a buff. Titan fortifications are
 * never listed. Unlike heroes, war flags have no roles, so there is no
 * role-match column.
 *
 * <p>Picking {@link CowScoreTier#GOOD} in a fortification row is equivalent
 * to having no override: it removes the entry from the war flag's working scores
 * (see {@link #buildFortificationRow}). Saving writes every war flag to the
 * workspace copy of {@code warFlagCowScore.json} (never {@code warFlags.json} - see
 * {@link WarFlagRepository}'s class Javadoc). The "restore defaults" button
 * resets every war flag to the defaults shipped inside the jar - see {@link
 * #onRestoreDefaults()}.
 */
public final class WarFlagCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "warFlagBuffFitScores.title";

    // Shared with the hero/titan/pet dialogs - identical wording for all four.
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_GENERAL_SCORE = "heroBuffFitScores.generalScore";
    private static final String KEY_BUFF_FIT_SCORES_HEADER = "heroBuffFitScores.buffFitScoresHeader";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";

    /** War-flag-specific confirmation question of {@link #onRestoreDefaults()}. */
    private static final String KEY_RESTORE_DEFAULTS_CONFIRM = "warFlagBuffFitScores.restoreDefaultsConfirm";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Icon size for the war flag name label in {@link #buildDetailPanel}. */
    private static final int WAR_FLAG_ICON_SIZE = 32;

    /** Width reserved for a row's name label, so every combo box in the list lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Every known war flag, sorted by display name - the catalog never changes while this dialog is open. */
    private final List<WarFlag> warFlagCatalog = WarFlagRepository.findAll().stream()
            .sorted(Comparator.comparing(WarFlagCoreScoreDialog::warFlagLabel, String.CASE_INSENSITIVE_ORDER))
            .toList();

    /** Every fortification a war flag's {@link WarFlag#buffFitScores()} can apply to: HERO fortifications with a buff. */
    private final List<Fortification> buffedFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.HERO && f.buff() != null)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress buffFitScores edits, keyed by war flag id, populated lazily for
     * every war flag the user has actually looked at. A war flag never selected keeps
     * its original map untouched on {@link #onSaveScores()}.
     */
    private final Map<String, Map<String, CowScoreTier>> workingScores = new LinkedHashMap<>();

    /** In-progress generalScore edits, keyed by war flag id - populated lazily like {@link #workingScores}. */
    private final Map<String, CowScoreTier> workingGeneralScores = new LinkedHashMap<>();

    private final DefaultListModel<WarFlag> warFlagListModel = new DefaultListModel<>();
    private final JList<WarFlag> warFlagList = new JList<>(warFlagListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public WarFlagCoreScoreDialog(Frame owner) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        if (!warFlagCatalog.isEmpty()) {
            warFlagList.setSelectedIndex(0);
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
        warFlagList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof WarFlag warFlag) {
                    setText(warFlagLabel(warFlag));
                }
                return this;
            }
        });
        warFlagCatalog.forEach(warFlagListModel::addElement);
        warFlagList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onWarFlagSelected(warFlagList.getSelectedValue());
            }
        });
        left.add(new JScrollPane(warFlagList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(detailContainer));
        split.setDividerLocation(180);
        return split;
    }

    private void onWarFlagSelected(WarFlag warFlag) {
        detailContainer.removeAll();
        if (warFlag != null) {
            detailContainer.add(buildDetailPanel(warFlag), BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /**
     * Builds the given war flag's header (icon plus display name), its {@link
     * WarFlag#generalScore()} row, a small header and one row per {@link
     * #buffedFortifications} entry - see {@link #buildFortificationRow}.
     */
    private JPanel buildDetailPanel(WarFlag warFlag) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel warFlagNameLabel = new JLabel(warFlagLabel(warFlag), IconLoader.iconFor(warFlag.imagePath(), WAR_FLAG_ICON_SIZE), JLabel.LEFT);
        warFlagNameLabel.setForeground(Color.WHITE);
        warFlagNameLabel.setFont(warFlagNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        warFlagNameLabel.setIconTextGap(8);
        warFlagNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(warFlagNameLabel);

        JPanel generalScoreRow = buildGeneralScoreRow(warFlag);
        generalScoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(generalScoreRow);
        panel.add(Box.createVerticalStrut(12));

        JLabel buffFitScoresHeader = new JLabel(LanguageService.displayName(KEY_BUFF_FIT_SCORES_HEADER));
        buffFitScoresHeader.setForeground(Color.WHITE);
        buffFitScoresHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        buffFitScoresHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(buffFitScoresHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, CowScoreTier> warFlagScores = workingScores.computeIfAbsent(warFlag.id(),
                id -> new LinkedHashMap<>(warFlag.buffFitScores()));

        for (Fortification fortification : buffedFortifications) {
            JPanel row = buildFortificationRow(fortification, warFlagScores);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /** The war flag's {@link WarFlag#generalScore()} row: a label plus a {@link CowScoreTier} combo box, stored as selected. */
    private JPanel buildGeneralScoreRow(WarFlag warFlag) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel label = new JLabel(LanguageService.displayName(KEY_GENERAL_SCORE));
        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, label.getPreferredSize().height));
        row.add(label);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(workingGeneralScores.computeIfAbsent(warFlag.id(), id -> warFlag.generalScore()));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            workingGeneralScores.put(warFlag.id(), selected == null ? CowScoreTier.GOOD : selected);
        });
        row.add(combo);
        return row;
    }

    /**
     * One (warFlag, fortification) row: the fortification's display name and a
     * {@link CowScoreTier} combo box pre-selected to the war flag's current
     * override (or {@link CowScoreTier#GOOD} if none). Selecting {@link
     * CowScoreTier#GOOD} removes the override instead of storing it.
     */
    private JPanel buildFortificationRow(Fortification fortification, Map<String, CowScoreTier> warFlagScores) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(warFlagScores.getOrDefault(fortification.id(), CowScoreTier.GOOD));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            if (selected == null || selected == CowScoreTier.GOOD) {
                warFlagScores.remove(fortification.id());
            } else {
                warFlagScores.put(fortification.id(), selected);
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
     * the working values of every war flag with the shipped defaults from {@link
     * WarFlagRepository#loadDefaultCowScores()}. Nothing is written until the
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

        Map<String, CowScore> defaults = WarFlagRepository.loadDefaultCowScores();
        for (WarFlag warFlag : warFlagCatalog) {
            CowScore cowScore = defaults.getOrDefault(warFlag.id(), CowScore.DEFAULT);
            workingGeneralScores.put(warFlag.id(), cowScore.generalScore());
            // GOOD means "no override" in this dialog - see buildFortificationRow.
            Map<String, CowScoreTier> buffFitScores = new LinkedHashMap<>();
            cowScore.buffFitScores().forEach((fortificationId, tier) -> {
                if (tier != CowScoreTier.GOOD) {
                    buffFitScores.put(fortificationId, tier);
                }
            });
            workingScores.put(warFlag.id(), buffFitScores);
        }
        onWarFlagSelected(warFlagList.getSelectedValue());
        Logger.log("War flag CowScore dialog: restored the default values for every war flag (not saved yet)");
    }

    private static String warFlagLabel(WarFlag warFlag) {
        return LanguageService.displayName(warFlag.id());
    }

    /**
     * Writes every war flag's working values back into a fresh {@link WarFlag} (the
     * original values for a war flag never opened) and saves the whole catalog via
     * {@link WarFlagRepository#saveCowScores} - which only writes {@code
     * warFlagCowScore.json}, never {@code warFlags.json}.
     */
    private void onSaveScores() {
        List<WarFlag> updatedCatalog = warFlagCatalog.stream()
                .map(warFlag -> {
                    Map<String, CowScoreTier> scores = workingScores.get(warFlag.id());
                    Map<String, CowScoreTier> buffFitScores = scores == null ? warFlag.buffFitScores() : scores;
                    CowScoreTier generalScore = workingGeneralScores.getOrDefault(warFlag.id(), warFlag.generalScore());
                    return new WarFlag(warFlag.id(), warFlag.imagePath(), new CowScore(generalScore, buffFitScores));
                })
                .toList();

        try {
            WarFlagRepository.saveCowScores(updatedCatalog);
            Logger.log("Saved: warFlagCowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
