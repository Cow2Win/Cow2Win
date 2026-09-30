package org.c2w.gui.hero;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.HeroRepository;
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
 * Dialog for maintaining a hero's {@link FortMarks} - one {@link FortMark}
 * (neutral / positive / negative) per fortification of type {@link
 * FortificationType#HERO}, the successor of the former tier-based
 * generalScore/buffFitScores (CowScore concept of 2026-09-30). Opened from
 * the toolbar, independent of the currently open guild/lineup, since the hero
 * catalog is shared across every guild.
 *
 * <p>Left side lists every known hero; picking one shows one row per hero
 * fortification: its name, the role its {@link RoleBuff} asks for with the
 * automatic, read-only BUFF marker when the hero's role matches, and a combo
 * box for the manual mark. "Neutral" means no entry at all. Saving writes
 * every hero to the workspace copy of {@code cowScore.json} (see {@code
 * FortMarkFiles} for the format).
 *
 * <p>The toolbar's "restore defaults" button resets the marks of every hero
 * to the defaults shipped inside the jar - see {@link #onRestoreDefaults()}.
 */
public final class HeroCoreScoreDialog extends JDialog {

    /** Language file key for this dialog's title, shown via {@link LanguageService#displayTitle(String)}. */
    private static final String KEY_TITLE = "heroBuffFitScores.title";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "save" toolbar button (see {@link #onSaveScores()}). */
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";

    /** Language file key for the tooltip of the "restore defaults" toolbar button (see {@link #onRestoreDefaults()}). */
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";

    /** Language file key for the confirmation question of {@link #onRestoreDefaults()}. */
    private static final String KEY_RESTORE_DEFAULTS_CONFIRM = "heroBuffFitScores.restoreDefaultsConfirm";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";

    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";

    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Avatar size for the {@link #buildDetailPanel}'s {@code heroNameLabel} icon. */
    private static final int HERO_ICON_SIZE = 32;

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the small header above the per-fortification rows (see {@link #buildDetailPanel}). */
    private static final String KEY_BUFF_FIT_SCORES_HEADER = "fortMarks.header";

    /** Width reserved for a row's fortification-name label, so every combo box in the list lines up (see {@link #buildFortificationRow}). */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Width reserved for a row's role-match label (see {@link #buildFortificationRow}). */
    private static final int ROLE_LABEL_WIDTH = 150;

    /**
     * Every known hero, sorted by display name - unlike e.g.
     * {@code GuildEditorDialog}'s member list, the hero catalog never
     * changes size while this dialog is open, so it is built once here
     * rather than refreshed.
     */
    private final List<Hero> heroCatalog = HeroRepository.findAll().stream()
            .sorted(Comparator.comparing(HeroCoreScoreDialog::heroLabel, String.CASE_INSENSITIVE_ORDER))
            .toList();

    /** Every fortification a hero can be marked for: all fortifications of type {@link FortificationType#HERO}. */
    private final List<Fortification> heroFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.HERO)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress edits, keyed by hero id, populated lazily (one entry per
     * hero the user has actually looked at - see {@link #buildDetailPanel})
     * from that hero's current {@link Hero#fortMarks()}. A hero never
     * selected in this dialog session therefore keeps its original marks
     * completely untouched on {@link #onSaveScores()}.
     */
    private final Map<String, Map<String, FortMark>> workingMarks = new LinkedHashMap<>();

    private final DefaultListModel<Hero> heroListModel = new DefaultListModel<>();
    private final JList<Hero> heroList = new JList<>(heroListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public HeroCoreScoreDialog(Frame owner) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        heroList.setSelectedIndex(0);
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
        heroList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Hero hero) {
                    setText(heroLabel(hero));
                }
                return this;
            }
        });
        heroCatalog.forEach(heroListModel::addElement);
        heroList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onHeroSelected(heroList.getSelectedValue());
            }
        });
        left.add(new JScrollPane(heroList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(detailContainer));
        split.setDividerLocation(220);
        return split;
    }

    private void onHeroSelected(Hero hero) {
        detailContainer.removeAll();
        detailContainer.add(buildDetailPanel(hero), BorderLayout.CENTER);
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /**
     * Builds the given hero's header (avatar plus {@link #heroLabel(Hero)}),
     * a small header, and one row per {@link #heroFortifications} entry, each
     * wired into {@link #workingMarks} - see {@link #buildFortificationRow}.
     */
    private JPanel buildDetailPanel(Hero hero) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel heroNameLabel = new JLabel(heroLabel(hero), IconLoader.iconFor(hero.imagePath(), HERO_ICON_SIZE), JLabel.LEFT);
        heroNameLabel.setForeground(Color.WHITE);
        heroNameLabel.setFont(heroNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        heroNameLabel.setIconTextGap(8);
        heroNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heroNameLabel);
        panel.add(Box.createVerticalStrut(12));

        JLabel fortificationsHeader = new JLabel(LanguageService.displayName(KEY_BUFF_FIT_SCORES_HEADER));
        fortificationsHeader.setForeground(Color.WHITE);
        fortificationsHeader.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        fortificationsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fortificationsHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, FortMark> heroMarks = workingMarks.computeIfAbsent(hero.id(),
                id -> new LinkedHashMap<>(hero.fortMarks().marks()));

        for (Fortification fortification : heroFortifications) {
            JPanel row = buildFortificationRow(hero, fortification, heroMarks);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /**
     * One (hero, fortification) row: the fortification's display name, the
     * role its {@link RoleBuff} asks for (if any) plus the automatic,
     * read-only BUFF marker when the hero's role matches, and the mark combo
     * box, pre-selected to heroMarks' current entry (null = neutral).
     * Selecting neutral removes the entry.
     */
    private JPanel buildFortificationRow(Hero hero, Fortification fortification, Map<String, FortMark> heroMarks) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        RoleBuff roleBuff = fortification.buff() instanceof RoleBuff rb ? rb : null;
        boolean roleMatches = hero.matchesBuff(fortification.buff());
        String roleText = roleBuff == null ? ""
                : roleLabel(roleBuff.role()) + (roleMatches ? "  " + LanguageService.displayName("fortMark.buffMarker") : "");
        JLabel roleTextLabel = new JLabel(roleText);
        roleTextLabel.setForeground(roleMatches ? IconLoader.GREEN : Color.WHITE);
        roleTextLabel.setPreferredSize(new Dimension(ROLE_LABEL_WIDTH, roleTextLabel.getPreferredSize().height));
        row.add(roleTextLabel);

        JComboBox<FortMark> combo = buildFortMarkCombo();
        combo.setSelectedItem(heroMarks.get(fortification.id()));
        combo.addActionListener(e -> {
            FortMark selected = (FortMark) combo.getSelectedItem();
            if (selected == null) {
                heroMarks.remove(fortification.id());
            } else {
                heroMarks.put(fortification.id(), selected);
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
     * The localized display name for a {@link Role} (language file key
     * {@code role.<NAME>}, see {@code resources/language/<name>/<name>.properties})
     * - used instead of {@link Role#name()} so this role indicator is
     * translated like every other UI string.
     */
    private static String roleLabel(Role role) {
        return LanguageService.displayName("role." + role.name());
    }

    /**
     * Handler of the "restore defaults" toolbar button: after a confirmation,
     * replaces the working values of <em>every</em> hero (not only the ones
     * opened so far) with the shipped defaults from {@link
     * HeroRepository#loadDefaultCowScores()} and refreshes the detail panel
     * of the selected hero. Like every other edit in this dialog, nothing is
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

        Map<String, FortMarks> defaults = HeroRepository.loadDefaultCowScores();
        for (Hero hero : heroCatalog) {
            workingMarks.put(hero.id(), new LinkedHashMap<>(defaults.getOrDefault(hero.id(), FortMarks.NONE).marks()));
        }
        onHeroSelected(heroList.getSelectedValue());
        Logger.log("CowScore dialog: restored the default values for every hero (not saved yet)");
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    /**
     * Writes every hero's current {@link #workingMarks} entry back into a
     * fresh {@link Hero} (untouched for a hero never opened in this dialog
     * session) and saves the whole catalog via {@link
     * HeroRepository#saveCowScores} - which only writes {@code cowScore.json},
     * never {@code heroes.json}.
     */
    private void onSaveScores() {
        List<Hero> updatedCatalog = heroCatalog.stream()
                .map(hero -> {
                    Map<String, FortMark> marks = workingMarks.get(hero.id());
                    FortMarks fortMarks = marks == null ? hero.fortMarks() : new FortMarks(marks);
                    return new Hero(hero.id(), hero.roles(), hero.imagePath(), fortMarks);
                })
                .toList();

        try {
            HeroRepository.saveCowScores(updatedCatalog);
            Logger.log("Saved: cowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("heroBuffFitScores.saveError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
