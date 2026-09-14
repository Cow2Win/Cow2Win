package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.data.repository.HeroRepository;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FortificationInfoPanel extends JPanel {

    /** Language file key (see resources/language/*.txt) for the header above the {@link ScoreTier#NEGATIVE} hero group (see {@link #buildHeroScorePanel()}). */
    private static final String KEY_NEGATIVE_HEROES_HEADER = "fortificationDetail.negativeHeroesHeader";

    /** Language file key (see resources/language/*.txt) for the header above the {@link ScoreTier#STANDARD}/{@link ScoreTier#ELEVATED} hero group (see {@link #buildHeroScorePanel()}). */
    private static final String KEY_GOOD_HEROES_HEADER = "fortificationDetail.goodHeroesHeader";

    /** Avatar size for the hero groups built by {@link #buildHeroScorePanel()} - smaller than the 32px used for editable hero pickers elsewhere (e.g. {@code MemberEditorPanel}), since these are purely informational thumbnails. */
    private static final int HERO_ICON_SIZE = 28;

    /** Column count for the hero avatar grids built by {@link #buildHeroGroupPanel} - keeps their width predictable regardless of how many heroes fall into a group. */
    private static final int HERO_GROUP_COLUMNS = 12;

    /** Fixed viewport size of each hero group's scroll pane (see {@link #buildHeroGroupPanel}), so a large hero catalog grows scrollbars instead of blowing up this panel's (and thus {@link FortificationEntryDialog}'s) preferred size. */
    private static final Dimension HERO_GROUP_SCROLL_SIZE = new Dimension(260, 32);

    private final Fortification fortification;

    //private final JComboBox<Integer> strategicImportanceCombo = new JComboBox<>(STRATEGIC_IMPORTANCE_VALUES);

    public FortificationInfoPanel(Fortification fortification) {
        if (fortification == null) {
            throw new IllegalArgumentException("FortificationInfoPanel needs a fortification");
        }
        this.fortification = fortification;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        JPanel infoPanel = buildInfoPanel();
        JPanel heroScorePanel = buildHeroScorePanel();
        infoPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        heroScorePanel.setAlignmentY(Component.TOP_ALIGNMENT);
        add(infoPanel);
        add(Box.createHorizontalStrut(12));
        add(heroScorePanel);

        loadFromFortification();
    }

    private void loadFromFortification() {
        //strategicImportanceCombo.setSelectedItem(clampToStrategicImportanceRange(fortification.strategicImportance()));
    }

    private static int clampToStrategicImportanceRange(int value) {
        return Math.max(1, Math.min(10, value));
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int y = 0;

        addInfoRow(panel, gbc, y++, "Buff:", buffText());
        addInfoRow(panel, gbc, y++, LanguageService.displayName("fortificationDetail.prerequisites"), prerequisitesText());

        gbc.gridx = 0;
        gbc.gridy = y;
        panel.add(new JLabel("Strategic Importance:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(fortification.strategicImportance()+""), gbc);
        return panel;
    }

    private static void addInfoRow(JPanel panel, GridBagConstraints gbc, int y, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(value), gbc);
    }

    private String buffText() {
        Buff buff = fortification.buff();
        if (buff == null) {
            return LanguageService.displayName("common.none");
        }
        String text = buff.display();
        if (text == null || text.isBlank()) {
            text = buff.effect().name() + " (" + GuiUtils.NUMBER_FORMAT.format(buff.bonusPercent()) + "%)";
        }
        return text;
    }


    private String prerequisitesText() {
        if (fortification.prerequisites().isEmpty()) {
            return LanguageService.displayName("common.none");
        }
        return fortification.prerequisites().stream()
                .map(LanguageService::displayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    /**
     * Right of {@link #buildInfoPanel()} (see the constructor): two
     * read-only hero avatar groups for {@link #fortification} - heroes with
     * {@link ScoreTier#NEGATIVE} on top ("rather avoid these"), heroes with
     * {@link ScoreTier#STANDARD} or {@link ScoreTier#ELEVATED} below ("good
     * fits"), per {@link #scoreTierFor(Hero)}. Purely informational
     * (no editing hook, unlike {@link #applyEditsTo}) - rebuilt fresh from
     * the current hero catalog every time this panel is constructed.
     */
    private JPanel buildHeroScorePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        List<Hero> heroes = HeroRepository.findAll();
        List<Hero> negativeHeroes = heroes.stream()
                .filter(hero -> scoreTierFor(hero) == ScoreTier.NEGATIVE)
                .sorted(Comparator.comparing(FortificationInfoPanel::heroLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Hero> goodHeroes = heroes.stream()
                .filter(hero -> scoreTierFor(hero) == ScoreTier.STANDARD || scoreTierFor(hero) == ScoreTier.ELEVATED)
                .sorted(Comparator.comparing(FortificationInfoPanel::heroLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        JPanel negativeGroup = buildHeroGroupPanel(LanguageService.displayName(KEY_NEGATIVE_HEROES_HEADER), negativeHeroes);
        JPanel goodGroup = buildHeroGroupPanel(LanguageService.displayName(KEY_GOOD_HEROES_HEADER), goodHeroes);
        negativeGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        goodGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(negativeGroup);
        panel.add(Box.createVerticalStrut(8));
        panel.add(goodGroup);

        return panel;
    }

    /**
     * This hero's {@link ScoreTier} for {@link #fortification}: when the
     * fortification has a {@link RoleBuff}, its buff-specific
     * {@link Hero#buffFitScore(String, boolean)} (role match resolved
     * against {@link RoleBuff#role()}, mirroring
     * {@link HeroTeamBuffFitScore#of(HeroTeam, Fortification)}); otherwise
     * (no buff, or a titan {@link ElementBuff}, for which heroes have no
     * buff-specific score of their own) its {@link Hero#generalScore()}.
     */
    private ScoreTier scoreTierFor(Hero hero) {
        if (fortification.buff() instanceof RoleBuff roleBuff) {
            boolean roleMatches = hero.roles().contains(roleBuff.role());
            return hero.buffFitScore(fortification.id(), roleMatches);
        }
        return hero.generalScore();
    }

    /**
     * A titled, read-only, scrollable grid of hero avatars (see
     * {@link #buildHeroIconLabel}) - or {@code common.none} if
     * {@code heroes} is empty. The grid is wrapped in a {@link JScrollPane}
     * of fixed size ({@link #HERO_GROUP_SCROLL_SIZE}) rather than sized to
     * fit every hero, so a large catalog scrolls instead of growing this
     * panel unpredictably.
     */
    private JPanel buildHeroGroupPanel(String title, List<Hero> heroes) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));

        if (heroes.isEmpty()) {
            wrapper.add(new JLabel(LanguageService.displayName("common.none")), BorderLayout.CENTER);
            return wrapper;
        }

        JPanel grid = new JPanel(new GridLayout(0, HERO_GROUP_COLUMNS, 2, 2));
        for (Hero hero : heroes) {
            grid.add(buildHeroIconLabel(hero));
        }
        JPanel gridHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridHolder.add(grid);

        JScrollPane scrollPane = new JScrollPane(gridHolder,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(HERO_GROUP_SCROLL_SIZE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(HERO_ICON_SIZE);
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    /** One hero's avatar (see {@link IconLoader#iconFor(String, int)}), with its display name and {@link ScoreTier} as a tooltip. */
    private JLabel buildHeroIconLabel(Hero hero) {
        JLabel label = new JLabel(IconLoader.iconFor(hero.imagePath(), HERO_ICON_SIZE));
        label.setToolTipText(heroLabel(hero) + " (" + scoreTierFor(hero).name() + ")");
        return label;
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }


    /**
     * Applies this panel's edits (currently none - {@code strategicImportanceCombo} is
     * commented out above) to {@code base}, returning the resulting {@link Fortification}.
     * Kept as its own method/hook (see {@link org.c2w.gui.fort.FortificationEntryDialog}'s
     * javadoc on its {@code infoPanel} field) for when a catalog field becomes editable here again.
     */
    public Fortification applyEditsTo(Fortification base) {
        return base;
    }
}

