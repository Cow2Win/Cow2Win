package org.c2w.gui.flag;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.WarFlag;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.WarFlagRepository;
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
 * Dialog for maintaining a war flag's {@link FortMarks}: for every fortification of
 * type {@link FortificationType#HERO} a "marked" checkbox - a war flag marked for a
 * fortification adds a bonus to its team's CowScore there (see {@code
 * TeamScoreCalculator}). Successor of the former tier-based
 * generalScore/buffFitScores (CowScore concept of 2026-09-30). Opened from the
 * toolbar, independent of the currently open guild/lineup. Saving writes the
 * workspace copy of {@code warFlagCowScore.json} (see {@code FortMarkFiles}).
 */
public final class WarFlagCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "warFlagBuffFitScores.title";

    // Shared with the hero/titan/war flag dialogs - identical wording for all of them.
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";
    private static final String KEY_FORTIFICATIONS_HEADER = "fortMarks.header";
    private static final String KEY_MARKED_TOOLTIP = "fortMarks.markedTooltip";

    /** War-flag-specific confirmation question of {@link #onRestoreDefaults()}. */
    private static final String KEY_RESTORE_DEFAULTS_CONFIRM = "warFlagBuffFitScores.restoreDefaultsConfirm";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Icon size for the war flag name label in {@link #buildDetailPanel}. */
    private static final int WAR_FLAG_ICON_SIZE = 32;

    /** Width reserved for a row's fortification-name label, so every checkbox lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Every known war flag, sorted by display name - the catalog never changes while this dialog is open. */
    private final WarFlagRepository repository;
    private final List<WarFlag> warFlagCatalog;

    /** Every fortification a war flag can be marked for: all fortifications of type {@link FortificationType#HERO}. */
    private final List<Fortification> heroFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.HERO)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress edits: the ids of the fortifications each war flag is marked for,
     * keyed by war flag id - populated lazily for every war flag opened in this dialog
     * session; a war flag never opened keeps its original marks on save.
     */
    private final Map<String, Set<String>> workingMarks = new LinkedHashMap<>();

    private final DefaultListModel<WarFlag> warFlagListModel = new DefaultListModel<>();
    private final JList<WarFlag> warFlagList = new JList<>(warFlagListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public WarFlagCoreScoreDialog(Frame owner, WarFlagRepository repository) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);
        if (repository == null) {
            throw new IllegalArgumentException("WarFlagCoreScoreDialog needs a WarFlagRepository");
        }
        this.repository = repository;
        this.warFlagCatalog = repository.findAll().stream()
                .sorted(Comparator.comparing(WarFlagCoreScoreDialog::warFlagLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

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

    /** The war flag's header (avatar plus name), a small header and one checkbox row per {@link #heroFortifications} entry. */
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
        panel.add(Box.createVerticalStrut(12));

        JLabel fortificationsHeader = new JLabel(LanguageService.displayName(KEY_FORTIFICATIONS_HEADER));
        fortificationsHeader.setForeground(Color.WHITE);
        fortificationsHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        fortificationsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fortificationsHeader);
        panel.add(Box.createVerticalStrut(4));

        Set<String> marked = workingMarks.computeIfAbsent(warFlag.id(), id -> markedIds(warFlag.fortMarks()));
        for (Fortification fortification : heroFortifications) {
            JPanel row = buildFortificationRow(fortification, marked);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /** One (war flag, fortification) row: the fortification's display name and a "marked" checkbox wired into {@code marked}. */
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
     * replaces the working marks of every war flag with the shipped defaults.
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
        for (WarFlag warFlag : warFlagCatalog) {
            workingMarks.put(warFlag.id(), markedIds(defaults.getOrDefault(warFlag.id(), FortMarks.NONE)));
        }
        onWarFlagSelected(warFlagList.getSelectedValue());
        Logger.log("War flag CowScore dialog: restored the default values for every war flag (not saved yet)");
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

    private static String warFlagLabel(WarFlag warFlag) {
        return LanguageService.displayName(warFlag.id());
    }

    /** Writes every war flag's current {@link #workingMarks} back and saves the catalog via {@link WarFlagRepository#saveCowScores}. */
    private void onSaveScores() {
        List<WarFlag> updatedCatalog = warFlagCatalog.stream()
                .map(warFlag -> {
                    Set<String> marked = workingMarks.get(warFlag.id());
                    if (marked == null) {
                        return warFlag;
                    }
                    Map<String, FortMark> marks = new LinkedHashMap<>();
                    marked.forEach(fortificationId -> marks.put(fortificationId, FortMark.POSITIVE));
                    return new WarFlag(warFlag.id(), warFlag.imagePath(), new FortMarks(marks));
                })
                .toList();

        try {
            repository.saveCowScores(updatedCatalog);
            Logger.log("Saved: warFlagCowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
