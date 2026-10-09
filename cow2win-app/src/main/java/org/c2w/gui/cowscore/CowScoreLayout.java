package org.c2w.gui.cowscore;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/** Layout values and pieces shared by every tab of {@link CowScoreDialog}. */
public final class CowScoreLayout {

    /** Bounds of the width of the list on the left - the same in every tab, see {@link #listWidth}. */
    public static final int MIN_LIST_WIDTH = 220;
    public static final int MAX_LIST_WIDTH = 320;

    /**
     * Preferred height of the list's scroll pane - small, so the (long) list never decides the
     * dialog's height; the detail area does (see {@code CowScoreDialog}).
     */
    public static final int LIST_PREFERRED_HEIGHT = 100;

    /** Room for the cell border and a little air around the longest entry. */
    private static final int LIST_TEXT_MARGIN = 24;

    private CowScoreLayout() {
    }

    /**
     * The width of the list on the left: the widest of {@code labels} in the list font, plus
     * margin and a vertical scroll bar - at least {@link #MIN_LIST_WIDTH}, at most {@link #MAX_LIST_WIDTH}.
     */
    public static int listWidth(Collection<String> labels) {
        JList<String> probe = new JList<>();
        FontMetrics metrics = probe.getFontMetrics(probe.getFont());
        int widest = labels.stream().mapToInt(metrics::stringWidth).max().orElse(0);
        int scrollBar = new JScrollBar(JScrollBar.VERTICAL).getPreferredSize().width;
        return Math.clamp(widest + LIST_TEXT_MARGIN + scrollBar, MIN_LIST_WIDTH, MAX_LIST_WIDTH);
    }

    /** The header of a detail area: icon(s) and the name in a large bold font. */
    public static JLabel nameLabel(String name, Icon icon) {
        JLabel nameLabel = new JLabel(name, icon, JLabel.LEFT);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, AbstractCowScorePanel.NAME_FONT_SIZE));
        nameLabel.setIconTextGap(8);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return nameLabel;
    }
}
