package org.c2w.gui.titan;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.TitanRepository;
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
 * Dialog for maintaining a titan's {@link CowScore} - {@link
 * Titan#generalScore()} and {@link Titan#buffFitScores()} - the TITAN-side
 * counterpart of {@link org.c2w.gui.hero.HeroCoreScoreDialog} (added
 * 2026-09-28, right after titans got their own {@code titanCowScore.json}).
 * Same layout and same behavior, with element matches instead of role
 * matches: opened from the "File" menu, independent of the currently open
 * guild/lineup, since the titan catalog is shared across every guild.
 *
 * <p>Left side lists every known titan; picking one shows its {@link
 * Titan#generalScore()} combo box (used for buff-less fortifications),
 * followed by one row per fortification of type {@link
 * FortificationType#TITAN} that has a buff, each with its own {@link
 * CowScoreTier} combo box for that (titan, fortification) pair's {@link
 * Titan#buffFitScores()} entry.
 *
 * <p>{@link CowScoreTier#GOOD} is the default and never written to disk:
 * picking it in a fortification row removes the override from the working
 * map, and {@link TitanRepository#saveCowScores} drops any remaining GOOD
 * value on save regardless - so {@code titanCowScore.json} only ever grows an
 * entry for a deliberately set, non-default tier.
 */
public final class TitanCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "titanBuffFitScores.title";

    // The remaining texts are identical to the hero dialog's, so its language file keys are reused.
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_GENERAL_SCORE = "heroBuffFitScores.generalScore";
    private static final String KEY_BUFF_FIT_SCORES_HEADER = "heroBuffFitScores.buffFitScoresHeader";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Avatar size for the titan name label in {@link #buildDetailPanel}. */
    private static final int TITAN_ICON_SIZE = 32;

    /** Width reserved for a row's name label, so every combo box in the list lines up. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Width reserved for a row's element-match label (see {@link #buildFortificationRow}). */
    private static final int ELEMENT_LABEL_WIDTH = 120;

    /** Every known titan, sorted by display name - the catalog never changes while this dialog is open. */
    private final List<Titan> titanCatalog = TitanRepository.findAll().stream()
            .sorted(Comparator.comparing(TitanCoreScoreDialog::titanLabel, String.CASE_INSENSITIVE_ORDER))
            .toList();

    /** Every fortification a titan's {@link Titan#buffFitScores()} can meaningfully apply to - see class Javadoc. */
    private final List<Fortification> buffedFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.TITAN && f.buff() != null)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress buff-fit edits, keyed by titan id, populated lazily for
     * every titan the user has actually opened. A titan never selected in
     * this dialog session keeps its original map untouched on save.
     */
    private final Map<String, Map<String, CowScoreTier>> workingScores = new LinkedHashMap<>();

    /** In-progress {@link Titan#generalScore()} edits, keyed by titan id - populated lazily like {@link #workingScores}. */
    private final Map<String, CowScoreTier> workingGeneralScores = new LinkedHashMap<>();

    private final DefaultListModel<Titan> titanListModel = new DefaultListModel<>();
    private final JList<Titan> titanList = new JList<>(titanListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public TitanCoreScoreDialog(Frame owner) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        if (!titanListModel.isEmpty()) {
            titanList.setSelectedIndex(0);
        }
        setSize(740, 420);
        setLocationRelativeTo(owner);
    }

    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_SCORES, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_SCORES));
        saveButton.addActionListener(e -> onSaveScores());
        buttons.add(saveButton);
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
            // NORTH, not CENTER: keeps the rows at their natural height instead of spreading them over the whole dialog.
            detailContainer.add(buildDetailPanel(titan), BorderLayout.NORTH);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /**
     * The selected titan's header (avatar, name and element), its {@link
     * Titan#generalScore()} row, and one row per {@link
     * #buffedFortifications} entry - see {@link #buildFortificationRow}.
     */
    private JPanel buildDetailPanel(Titan titan) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel titanNameLabel = new JLabel(titanLabel(titan) + "  (" + elementLabel(titan.element()) + ")",
                IconLoader.iconFor(titan.imagePath(), TITAN_ICON_SIZE), JLabel.LEFT);
        titanNameLabel.setForeground(Color.WHITE);
        titanNameLabel.setFont(titanNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        titanNameLabel.setIconTextGap(8);
        titanNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titanNameLabel);

        JPanel generalScoreRow = buildGeneralScoreRow(titan);
        generalScoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(generalScoreRow);
        panel.add(Box.createVerticalStrut(12));

        JLabel buffFitScoresHeader = new JLabel(LanguageService.displayName(KEY_BUFF_FIT_SCORES_HEADER));
        buffFitScoresHeader.setForeground(Color.WHITE);
        buffFitScoresHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        buffFitScoresHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(buffFitScoresHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, CowScoreTier> titanScores = workingScores.computeIfAbsent(titan.id(),
                id -> new LinkedHashMap<>(titan.buffFitScores()));

        for (Fortification fortification : buffedFortifications) {
            JPanel row = buildFortificationRow(titan, fortification, titanScores);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /** The titan's {@link Titan#generalScore()} row: label plus {@link CowScoreTier} combo box, wired into {@link #workingGeneralScores}. */
    private JPanel buildGeneralScoreRow(Titan titan) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel label = new JLabel(LanguageService.displayName(KEY_GENERAL_SCORE));
        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, label.getPreferredSize().height));
        row.add(label);

        JLabel spacer = new JLabel("");
        spacer.setPreferredSize(new Dimension(ELEMENT_LABEL_WIDTH, spacer.getPreferredSize().height));
        row.add(spacer);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(workingGeneralScores.computeIfAbsent(titan.id(), id -> titan.generalScore()));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            workingGeneralScores.put(titan.id(), selected == null ? CowScoreTier.GOOD : selected);
        });
        row.add(combo);

        return row;
    }

    /**
     * One (titan, fortification) row: the fortification's name, the element
     * its {@link ElementBuff} asks for (with a check mark if this titan has
     * it - purely informational, an override may still go below {@link
     * CowScoreTier#GOOD}), and the {@link CowScoreTier} combo box.
     * Selecting {@link CowScoreTier#GOOD} removes the override again.
     */
    private JPanel buildFortificationRow(Titan titan, Fortification fortification, Map<String, CowScoreTier> titanScores) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));


        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        ElementBuff elementBuff = fortification.buff() instanceof ElementBuff eb ? eb : null;
        boolean elementMatches = elementBuff != null && titan.element() == elementBuff.element();
        JLabel elementTextLabel = new JLabel(elementBuff == null ? ""
                : elementLabel(elementBuff.element()) + (elementMatches ? " ✓" : ""));
        elementTextLabel.setForeground(elementMatches ? IconLoader.GREEN : Color.WHITE);
        elementTextLabel.setPreferredSize(new Dimension(ELEMENT_LABEL_WIDTH, elementTextLabel.getPreferredSize().height));
        row.add(elementTextLabel);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(titanScores.getOrDefault(fortification.id(), CowScoreTier.GOOD));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            if (selected == null || selected == CowScoreTier.GOOD) {
                titanScores.remove(fortification.id());
            } else {
                titanScores.put(fortification.id(), selected);
            }
        });
        row.add(combo);

        return row;
    }

    /** A {@link CowScoreTier} combo box listing all tiers, rendered e.g. as "Good (0.8)" - same as the hero dialog. */
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

    /** Localized element name (language file key {@code titanElement.<NAME>}). */
    private static String elementLabel(TitanElement element) {
        return LanguageService.displayName("titanElement." + element.name());
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    /**
     * Rebuilds every titan with its working scores (untouched for titans
     * never opened in this session) and saves the whole catalog via {@link
     * TitanRepository#saveCowScores} - which only writes {@code
     * titanCowScore.json}, never {@code titans.json}.
     */
    private void onSaveScores() {
        List<Titan> updatedCatalog = titanCatalog.stream()
                .map(titan -> {
                    Map<String, CowScoreTier> scores = workingScores.get(titan.id());
                    Map<String, CowScoreTier> buffFitScores = scores == null ? titan.buffFitScores() : scores;
                    CowScoreTier generalScore = workingGeneralScores.getOrDefault(titan.id(), titan.generalScore());
                    return new Titan(titan.id(), titan.element(), titan.imagePath(), new CowScore(generalScore, buffFitScores));
                })
                .toList();

        try {
            TitanRepository.saveCowScores(updatedCatalog);
            Logger.log("Saved: titanCowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
