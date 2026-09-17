package org.c2w.gui.hero;

import org.c2w.data.model.CowScore;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Hero;
import org.c2w.data.model.Role;
import org.c2w.data.model.RoleBuff;
import org.c2w.data.model.CowScoreTier;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.HeroRepository;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for maintaining a hero's {@link CowScore} - {@link
 * Hero#generalScore()} and {@link Hero#buffFitScores()}, the two {@link
 * CowScoreTier}-based scores that replaced the old {@code buffProfits} list
 * (see {@code cow2win-verbesserungsvorschlaege.md}, "Fortification-Scoring:
 * Ablösung von buffProfits", Stufen 1 and 3). Opened from
 * {@link org.c2w.gui.ToolbarPanel}'s toolbar (see
 * {@code ToolbarPanel#onOpenHeroBuffFitScores}) - independent of the
 * currently open guild/lineup, since the hero catalog (like the
 * fortification catalog) is shared across every guild, so this dialog only
 * needs an owner window, not an {@code AppContext}.
 *
 * <p>Left side lists every known hero; picking one shows, at the top, a
 * single {@link CowScoreTier} combo box for that hero's {@link Hero#generalScore()}
 * (used for buff-less fortifications), followed by one row per fortification
 * that is both of type {@link FortificationType#HERO} and has a buff, each
 * with its own {@link CowScoreTier} combo box for that (hero, fortification)
 * pair's {@link Hero#buffFitScores()} entry - buff-less fortifications
 * already use generalScore instead (see its Javadoc) and are therefore not
 * listed among these per-fortification rows.
 *
 * <p>Per the "sparse file" convention already used for {@link
 * Hero#generalScore()} (see {@link CowScore}'s Javadoc - {@link
 * CowScoreTier#GOOD} is the default and is never written to disk), picking
 * {@link CowScoreTier#GOOD} in either kind of combo box is equivalent to
 * having no override at all: for a fortification row, selecting it removes
 * any entry from that hero's working scores (see
 * {@link #buildFortificationRow}); for generalScore there is nothing to
 * remove (it is a single field, not a sparse map), but
 * {@link HeroRepository} drops a {@code STANDARD}-valued
 * {@code generalScore}/{@code buffFitScores} entry on save regardless (see
 * {@code HeroRepository#cowScoreToTree}/{@code #buffFitScoresToTree}) - so
 * {@code cowScore.json} (not {@code heroes.json} - see {@link
 * HeroRepository}'s class Javadoc for why the two are separate files) only
 * ever grows an entry for a deliberately-set, non-default tier.
 */
public final class HeroBuffFitScoresDialog extends JDialog {

    private static final String BASE_TITLE = "Cow2 - Hero Buff Fit Scores";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "save" toolbar button (see {@link #onSaveScores()}). */
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";

    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the {@link Hero#generalScore()} row's label (see {@link #buildDetailPanel}). */
    private static final String KEY_GENERAL_SCORE = "heroBuffFitScores.generalScore";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the small header above the per-fortification rows (see {@link #buildDetailPanel}). */
    private static final String KEY_BUFF_FIT_SCORES_HEADER = "heroBuffFitScores.buffFitScoresHeader";

    /** Width reserved for a row's fortification-name label, so every combo box in the list lines up (see {@link #buildFortificationRow}). Also used for the {@link #KEY_GENERAL_SCORE} label so both combo boxes line up with each other. */
    private static final int NAME_LABEL_WIDTH = 170;

    /** Width reserved for a row's role-match label (see {@link #buildFortificationRow}). */
    private static final int ROLE_LABEL_WIDTH = 120;

    /**
     * Every known hero, sorted by display name - unlike e.g.
     * {@code GuildEditorDialog}'s member list, the hero catalog never
     * changes size while this dialog is open, so it is built once here
     * rather than refreshed.
     */
    private final List<Hero> heroCatalog = HeroRepository.findAll().stream()
            .sorted(Comparator.comparing(HeroBuffFitScoresDialog::heroLabel, String.CASE_INSENSITIVE_ORDER))
            .toList();

    /** Every fortification a hero's {@link Hero#buffFitScores()} can meaningfully apply to - see class Javadoc. */
    private final List<Fortification> buffedFortifications = FortificationRepository.findAll().stream()
            .filter(f -> f.type() == FortificationType.HERO && f.buff() != null)
            .sorted(Comparator.comparing((Fortification f) -> LanguageService.displayName(f.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();

    /**
     * In-progress edits, keyed by hero id, populated lazily (one entry per
     * hero the user has actually looked at - see {@link #buildDetailPanel})
     * from that hero's current {@link Hero#buffFitScores()}. A hero never
     * selected in this dialog session therefore keeps its original map
     * completely untouched on {@link #onSaveScores()}.
     */
    private final Map<String, Map<String, CowScoreTier>> workingScores = new LinkedHashMap<>();

    /**
     * In-progress {@link Hero#generalScore()} edits, keyed by hero id -
     * the single-field counterpart of {@link #workingScores}, populated
     * lazily the same way (see {@link #buildDetailPanel}). A hero never
     * selected in this dialog session keeps its original
     * {@link Hero#generalScore()} untouched on {@link #onSaveScores()}.
     */
    private final Map<String, CowScoreTier> workingGeneralScores = new LinkedHashMap<>();

    private final DefaultListModel<Hero> heroListModel = new DefaultListModel<>();
    private final JList<Hero> heroList = new JList<>(heroListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public HeroBuffFitScoresDialog(Frame owner) {
        super(owner, BASE_TITLE, false);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        showEmptyDetail("No hero selected.");

        setSize(720, 520);
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
        if (hero == null) {
            showEmptyDetail("No hero selected.");
            return;
        }
        detailContainer.removeAll();
        detailContainer.add(buildDetailPanel(hero), BorderLayout.CENTER);
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    private void showEmptyDetail(String message) {
        detailContainer.removeAll();
        detailContainer.add(new JLabel(message, JLabel.CENTER), BorderLayout.CENTER);
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /**
     * Builds the given hero's {@link Hero#generalScore()} row (see
     * {@link #buildGeneralScoreRow}), a small header, and one row per
     * {@link #buffedFortifications} entry, each with a {@link CowScoreTier}
     * combo box wired into {@link #workingScores} - see
     * {@link #buildFortificationRow}.
     */
    private JPanel buildDetailPanel(Hero hero) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel heroNameLabel = new JLabel(heroLabel(hero));
        heroNameLabel.setFont(heroNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        heroNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heroNameLabel);
        panel.add(Box.createVerticalStrut(8));

        JPanel generalScoreRow = buildGeneralScoreRow(hero);
        generalScoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(generalScoreRow);
        panel.add(Box.createVerticalStrut(12));

        JLabel buffFitScoresHeader = new JLabel(LanguageService.displayName(KEY_BUFF_FIT_SCORES_HEADER));
        buffFitScoresHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(buffFitScoresHeader);
        panel.add(Box.createVerticalStrut(4));

        Map<String, CowScoreTier> heroScores = workingScores.computeIfAbsent(hero.id(),
                id -> new LinkedHashMap<>(hero.buffFitScores()));

        for (Fortification fortification : buffedFortifications) {
            JPanel row = buildFortificationRow(hero, fortification, heroScores);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /**
     * The hero's {@link Hero#generalScore()} row: a label plus a
     * {@link CowScoreTier} combo box, pre-selected to
     * {@link #workingGeneralScores}' current value for this hero (seeded
     * from {@link Hero#generalScore()} itself, which already defaults to
     * {@link CowScoreTier#GOOD} - see that field's Javadoc). Unlike
     * {@link #buildFortificationRow}, there is no sparse map entry to
     * remove when {@link CowScoreTier#GOOD} is (re)selected - the value is
     * simply stored as-is, since {@link HeroRepository} already omits a
     * {@code STANDARD} {@code generalScore} on save (see class Javadoc).
     */
    private JPanel buildGeneralScoreRow(Hero hero) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JLabel label = new JLabel(LanguageService.displayName(KEY_GENERAL_SCORE));
        label.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, label.getPreferredSize().height));
        row.add(label);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        CowScoreTier current = workingGeneralScores.computeIfAbsent(hero.id(), id -> hero.generalScore());
        combo.setSelectedItem(current);
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            workingGeneralScores.put(hero.id(), selected == null ? CowScoreTier.GOOD : selected);
        });
        row.add(combo);

        return row;
    }

    /**
     * One (hero, fortification) row: the fortification's display name,
     * whether the hero's own roles satisfy its {@link RoleBuff} (purely
     * informational - an override can still be set below
     * {@link CowScoreTier#GOOD} even with a role match, see
     * {@link Hero#buffFitScore}), and the {@link CowScoreTier} combo box
     * itself, pre-selected to heroScores' current entry for this
     * fortification (or {@link CowScoreTier#GOOD} if none - see class
     * Javadoc for why that is the right "no override" display value).
     * Selecting {@link CowScoreTier#GOOD} again removes the entry from
     * heroScores rather than storing it explicitly, keeping
     * {@link #onSaveScores()} sparse without needing its own filtering
     * (that final filter lives in {@code HeroRepository} regardless, as a
     * second line of defense - see its Javadoc).
     */
    private JPanel buildFortificationRow(Hero hero, Fortification fortification, Map<String, CowScoreTier> heroScores) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setPreferredSize(new Dimension(NAME_LABEL_WIDTH, nameLabel.getPreferredSize().height));
        row.add(nameLabel);

        RoleBuff roleBuff = fortification.buff() instanceof RoleBuff rb ? rb : null;
        boolean roleMatches = roleBuff != null && hero.roles().contains(roleBuff.role());
        JLabel roleTextLabel = new JLabel(roleBuff == null ? "" : roleLabel(roleBuff.role()) + (roleMatches ? " ✓" : ""));
        roleTextLabel.setForeground(roleMatches ? IconLoader.GREEN : IconLoader.GRAY);
        roleTextLabel.setPreferredSize(new Dimension(ROLE_LABEL_WIDTH, roleTextLabel.getPreferredSize().height));
        row.add(roleTextLabel);

        JComboBox<CowScoreTier> combo = buildScoreTierCombo();
        combo.setSelectedItem(heroScores.getOrDefault(fortification.id(), CowScoreTier.GOOD));
        combo.addActionListener(e -> {
            CowScoreTier selected = (CowScoreTier) combo.getSelectedItem();
            if (selected == null || selected == CowScoreTier.GOOD) {
                heroScores.remove(fortification.id());
            } else {
                heroScores.put(fortification.id(), selected);
            }
        });
        row.add(combo);

        return row;
    }

    /** A {@link CowScoreTier} combo box listing all 5 tiers, rendered via {@link #scoreTierLabel} (e.g. "Good (0.8)"). */
    private static JComboBox<CowScoreTier> buildScoreTierCombo() {
        JComboBox<CowScoreTier> combo = new JComboBox<>(CowScoreTier.values());
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CowScoreTier tier) {
                    setText(scoreTierLabel(tier));
                }
                return this;
            }
        });
        return combo;
    }

    /**
     * The localized display text for a {@link CowScoreTier} combo box entry,
     * e.g. "Good (0.8)" - the tier's translated name (language file key
     * {@code scoreTier.<NAME>}, see {@code resources/language/<name>/<name>.properties})
     * followed by its numeric {@link CowScoreTier#value()} in parentheses.
     */
    private static String scoreTierLabel(CowScoreTier tier) {
        return LanguageService.displayName("scoreTier." + tier.name()) + " (" + tier.value() + ")";
    }

    /**
     * The localized display name for a {@link Role} (language file key
     * {@code role.<NAME>}, see {@code resources/language/<name>/<name>.properties})
     * - used instead of {@link Role#name()} so this role indicator is
     * translated like every other UI string, mirroring {@link #scoreTierLabel}.
     */
    private static String roleLabel(Role role) {
        return LanguageService.displayName("role." + role.name());
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    /**
     * Writes every hero's current {@link #workingScores}/
     * {@link #workingGeneralScores} entry back into a fresh {@link Hero}
     * (untouched, i.e. still the hero's original
     * {@link Hero#buffFitScores()}/{@link Hero#generalScore()}, for a hero
     * never opened in this dialog session - see those fields' Javadoc),
     * bundles both into a fresh {@link CowScore}, and saves the whole
     * catalog via {@link HeroRepository#saveCowScores} - which only writes
     * {@code cowScore.json} (never {@code heroes.json}, see {@link
     * HeroRepository}'s class Javadoc) and is also where {@link
     * CowScoreTier#GOOD} entries actually get dropped from the written
     * JSON (see class Javadoc), not here.
     */
    private void onSaveScores() {
        List<Hero> updatedCatalog = heroCatalog.stream()
                .map(hero -> {
                    Map<String, CowScoreTier> scores = workingScores.get(hero.id());
                    Map<String, CowScoreTier> buffFitScores = scores == null ? hero.buffFitScores() : scores;
                    CowScoreTier generalScore = workingGeneralScores.getOrDefault(hero.id(), hero.generalScore());
                    return new Hero(hero.id(), hero.roles(), hero.imagePath(), new CowScore(generalScore, buffFitScores));
                })
                .toList();

        try {
            HeroRepository.saveCowScores(updatedCatalog);
            Logger.log("Saved: cowScore.json");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save heroes:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        }
    }
}
