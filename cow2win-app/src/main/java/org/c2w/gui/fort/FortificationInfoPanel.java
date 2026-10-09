package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.BuffTexts;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only information about one fortification (buff, prerequisites,
 * strategic importance and the marked heroes/titans), shown at the top of
 * {@link FortificationEntryDialog}. The fortification's values are fixed -
 * nothing here can be edited (see {@code FortificationRepository}).
 */
public class FortificationInfoPanel extends JPanel {

    /** Language file keys for the headers above the {@link FortMark#NEGATIVE} / {@link FortMark#POSITIVE} hero groups (see {@link #buildMarkedPanel}). */
    private static final String KEY_NEGATIVE_HEROES_HEADER = "fortificationDetail.negativeHeroesHeader";
    private static final String KEY_GOOD_HEROES_HEADER = "fortificationDetail.goodHeroesHeader";

    /** Language file keys for the headers above the {@link FortMark#NEGATIVE} / {@link FortMark#POSITIVE} titan groups (see {@link #buildMarkedPanel}). */
    private static final String KEY_NEGATIVE_TITANS_HEADER = "fortificationDetail.negativeTitansHeader";
    private static final String KEY_GOOD_TITANS_HEADER = "fortificationDetail.goodTitansHeader";

    /** Avatar size for the groups built by {@link #buildMarkedPanel} - smaller than the 32px used for editable pickers elsewhere (e.g. the team assignment), since these are purely informational thumbnails. */
    private static final int ICON_SIZE = 28;

    /** Column count for the avatar grids built by {@link #buildGroupPanel} - keeps their width predictable regardless of how many entries fall into a group. */
    private static final int GROUP_COLUMNS = 12;

    /** Fixed viewport size of each group's scroll pane (see {@link #buildGroupPanel}), so a large catalog grows scrollbars instead of blowing up this panel's (and thus {@link FortificationEntryDialog}'s) preferred size. */
    private static final Dimension GROUP_SCROLL_SIZE = new Dimension(260, 32);

    /** One catalog entry (hero or titan) as far as this panel is concerned: id (= language key), avatar and its mark for {@link #fortification}. */
    private record MarkedEntry(String id, String imagePath, FortMark mark) {
    }

    /** Column count and scroll size of the avatar grids in the {@linkplain #FortificationInfoPanel(Fortification, List, List, boolean) compact} layout - fits a narrow side panel. */
    private static final int COMPACT_GROUP_COLUMNS = 8;
    private static final Dimension COMPACT_GROUP_SCROLL_SIZE = new Dimension(250, 32);

    private final Fortification fortification;

    /**
     * The layout of {@link FortificationEntryDialog}: facts and avatar groups side by side.
     *
     * @param heroes the hero catalog - shown for a {@link FortificationType#HERO} fortification
     * @param titans the titan catalog - shown for a {@link FortificationType#TITAN} fortification
     */
    public FortificationInfoPanel(Fortification fortification, List<Hero> heroes, List<Titan> titans) {
        this(fortification, heroes, titans, false);
    }

    /**
     * @param compact true for a narrow column (e.g. the info panel of a stage view): facts and
     *                avatar groups stacked, narrower avatar grids; false for the side-by-side
     *                layout of {@link FortificationEntryDialog}
     */
    public FortificationInfoPanel(Fortification fortification, List<Hero> heroes, List<Titan> titans, boolean compact) {
        if (fortification == null) {
            throw new IllegalArgumentException("FortificationInfoPanel needs a fortification");
        }
        if (heroes == null || titans == null) {
            throw new IllegalArgumentException("FortificationInfoPanel needs the hero and titan catalog");
        }
        this.fortification = fortification;

        setLayout(new BoxLayout(this, compact ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
        int columns = compact ? COMPACT_GROUP_COLUMNS : GROUP_COLUMNS;
        Dimension scrollSize = compact ? COMPACT_GROUP_SCROLL_SIZE : GROUP_SCROLL_SIZE;
        JPanel infoPanel = buildInfoPanel();
        JPanel markedPanel = fortification.type() == FortificationType.TITAN
                ? buildMarkedPanel(entries(titans, Titan::id, Titan::imagePath, t -> t.fortMark(fortification.id())),
                        KEY_NEGATIVE_TITANS_HEADER, KEY_GOOD_TITANS_HEADER, columns, scrollSize)
                : buildMarkedPanel(entries(heroes, Hero::id, Hero::imagePath, h -> h.fortMark(fortification.id())),
                        KEY_NEGATIVE_HEROES_HEADER, KEY_GOOD_HEROES_HEADER, columns, scrollSize);
        if (compact) {
            infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            markedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(infoPanel);
            add(Box.createVerticalStrut(8));
        } else {
            infoPanel.setAlignmentY(Component.TOP_ALIGNMENT);
            markedPanel.setAlignmentY(Component.TOP_ALIGNMENT);
            add(infoPanel);
            add(Box.createHorizontalStrut(12));
        }
        add(markedPanel);
    }

    private static <T> List<MarkedEntry> entries(List<T> catalog, Function<T, String> idOf,
                                                 Function<T, String> imagePathOf, Function<T, FortMark> markOf) {
        return catalog.stream()
                .map(e -> new MarkedEntry(idOf.apply(e), imagePathOf.apply(e), markOf.apply(e)))
                .toList();
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
     * read-only avatar groups of the heroes (hero fortification) or titans
     * (titan fortification) marked for {@link #fortification} - those marked
     * {@link FortMark#NEGATIVE} on top ("rather avoid these"), those marked
     * {@link FortMark#POSITIVE} below ("good fits"). Purely informational.
     */
    private JPanel buildMarkedPanel(List<MarkedEntry> entries, String negativeHeaderKey, String goodHeaderKey,
                                    int columns, Dimension scrollSize) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel negativeGroup = buildGroupPanel(LanguageService.displayName(negativeHeaderKey),
                marked(entries, FortMark.NEGATIVE), columns, scrollSize);
        JPanel goodGroup = buildGroupPanel(LanguageService.displayName(goodHeaderKey),
                marked(entries, FortMark.POSITIVE), columns, scrollSize);
        negativeGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        goodGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(negativeGroup);
        panel.add(Box.createVerticalStrut(8));
        panel.add(goodGroup);

        return panel;
    }

    /** Every entry carrying {@code mark} for {@link #fortification}, sorted by display name. */
    private static List<MarkedEntry> marked(List<MarkedEntry> entries, FortMark mark) {
        return entries.stream()
                .filter(entry -> entry.mark() == mark)
                .sorted(Comparator.comparing(FortificationInfoPanel::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * A titled, read-only, scrollable grid of avatars (see
     * {@link #buildIconLabel}) - or {@code common.none} if {@code entries}
     * is empty. The grid is wrapped in a {@link JScrollPane} of fixed size
     * ({@code scrollSize}) rather than sized to fit every entry, so
     * a large catalog scrolls instead of growing this panel unpredictably.
     */
    private static JPanel buildGroupPanel(String title, List<MarkedEntry> entries, int columns, Dimension scrollSize) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));

        if (entries.isEmpty()) {
            wrapper.add(new JLabel(LanguageService.displayName("common.none")), BorderLayout.CENTER);
            return wrapper;
        }

        JPanel grid = new JPanel(new GridLayout(0, columns, 2, 2));
        for (MarkedEntry entry : entries) {
            grid.add(buildIconLabel(entry));
        }
        JPanel gridHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridHolder.add(grid);

        JScrollPane scrollPane = new JScrollPane(gridHolder,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(scrollSize);
        scrollPane.getVerticalScrollBar().setUnitIncrement(ICON_SIZE);
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    /** One entry's avatar (see {@link IconLoader#iconFor(String, int)}), with its display name and {@link FortMark} as a tooltip. */
    private static JLabel buildIconLabel(MarkedEntry entry) {
        JLabel label = new JLabel(IconLoader.iconFor(entry.imagePath(), ICON_SIZE));
        label.setToolTipText(label(entry) + " (" + fortMarkLabel(entry.mark()) + ")");
        return label;
    }

    /** The localized display name for a {@link FortMark} (language file key {@code fortMark.<NAME>}, {@code fortMark.NONE} for null). */
    private static String fortMarkLabel(FortMark mark) {
        return LanguageService.displayName("fortMark." + (mark == null ? "NONE" : mark.name()));
    }

    private static String label(MarkedEntry entry) {
        return LanguageService.displayName(entry.id());
    }

}
