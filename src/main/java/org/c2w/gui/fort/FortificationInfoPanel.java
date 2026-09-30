package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.data.repository.HeroRepository;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.BuffTexts;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only information about one fortification (buff, prerequisites,
 * strategic importance and the hero CowScore groups), shown at the top of
 * {@link FortificationEntryDialog}. The fortification's values are fixed -
 * nothing here can be edited (see {@code FortificationRepository}).
 */
public class FortificationInfoPanel extends JPanel {

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the header above the {@link FortMark#NEGATIVE} hero group (see {@link #buildHeroScorePanel()}). */
    private static final String KEY_NEGATIVE_HEROES_HEADER = "fortificationDetail.negativeHeroesHeader";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the header above the {@link FortMark#POSITIVE} hero group (see {@link #buildHeroScorePanel()}). */
    private static final String KEY_GOOD_HEROES_HEADER = "fortificationDetail.goodHeroesHeader";

    /** Avatar size for the hero groups built by {@link #buildHeroScorePanel()} - smaller than the 32px used for editable hero pickers elsewhere (e.g. {@code MemberEditorPanel}), since these are purely informational thumbnails. */
    private static final int HERO_ICON_SIZE = 28;

    /** Column count for the hero avatar grids built by {@link #buildHeroGroupPanel} - keeps their width predictable regardless of how many heroes fall into a group. */
    private static final int HERO_GROUP_COLUMNS = 12;

    /** Fixed viewport size of each hero group's scroll pane (see {@link #buildHeroGroupPanel}), so a large hero catalog grows scrollbars instead of blowing up this panel's (and thus {@link FortificationEntryDialog}'s) preferred size. */
    private static final Dimension HERO_GROUP_SCROLL_SIZE = new Dimension(260, 32);

    private final Fortification fortification;

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
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int y = 0;

        addInfoRow(panel, gbc, y++, LanguageService.displayName("fortificationDetail.buff"), buffText());
        addInfoRow(panel, gbc, y++, LanguageService.displayName("fortificationDetail.prerequisites"), prerequisitesText());

        gbc.gridx = 0;
        gbc.gridy = y;
        panel.add(new JLabel(LanguageService.displayName("fortificationDetail.strategicImportance")), gbc);
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
        return BuffTexts.describe(buff);
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
     * read-only hero avatar groups for {@link #fortification} - heroes marked
     * {@link FortMark#NEGATIVE} for it on top ("rather avoid these"), heroes
     * marked {@link FortMark#POSITIVE} below ("good fits"). Purely
     * informational - rebuilt fresh from the current hero catalog every time
     * this panel is constructed.
     */
    private JPanel buildHeroScorePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        List<Hero> heroes = HeroRepository.findAll();
        List<Hero> negativeHeroes = heroesMarked(heroes, FortMark.NEGATIVE);
        List<Hero> goodHeroes = heroesMarked(heroes, FortMark.POSITIVE);

        JPanel negativeGroup = buildHeroGroupPanel(LanguageService.displayName(KEY_NEGATIVE_HEROES_HEADER), negativeHeroes);
        JPanel goodGroup = buildHeroGroupPanel(LanguageService.displayName(KEY_GOOD_HEROES_HEADER), goodHeroes);
        negativeGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        goodGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(negativeGroup);
        panel.add(Box.createVerticalStrut(8));
        panel.add(goodGroup);

        return panel;
    }

    /** Every hero carrying {@code mark} for {@link #fortification}, sorted by display name. */
    private List<Hero> heroesMarked(List<Hero> heroes, FortMark mark) {
        return heroes.stream()
                .filter(hero -> hero.fortMark(fortification.id()) == mark)
                .sorted(Comparator.comparing(FortificationInfoPanel::heroLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
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

    /** One hero's avatar (see {@link IconLoader#iconFor(String, int)}), with its display name and {@link FortMark} as a tooltip. */
    private JLabel buildHeroIconLabel(Hero hero) {
        JLabel label = new JLabel(IconLoader.iconFor(hero.imagePath(), HERO_ICON_SIZE));
        label.setToolTipText(heroLabel(hero) + " (" + fortMarkLabel(hero.fortMark(fortification.id())) + ")");
        return label;
    }

    /** The localized display name for a {@link FortMark} (language file key {@code fortMark.<NAME>}, {@code fortMark.NONE} for null). */
    private static String fortMarkLabel(FortMark mark) {
        return LanguageService.displayName("fortMark." + (mark == null ? "NONE" : mark.name()));
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

}

