package org.c2w.gui.stage;

import org.c2w.gui.common.AppColors;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.util.List;

/**
 * The one-sentence summary below the strategic concept's algorithm combo box: muted, wrapped at a
 * fixed width. Always as high as the longest summary of the current choices needs, at least two
 * lines - so the actions below do not move when another algorithm is chosen.
 */
final class AlgorithmSummaryLabel extends JLabel {

    private static final int MIN_LINES = 2;

    private final int wrapWidth;
    private String summary = "";
    private List<String> choices = List.of();

    AlgorithmSummaryLabel(int wrapWidth) {
        this.wrapWidth = wrapWidth;
        setVerticalAlignment(SwingConstants.TOP);
        setForeground(AppColors.TEXT_MUTED);
    }

    /** Shows {@code summary}; {@code choices} are the summaries of every choice, for the height. */
    void show(String summary, List<String> choices) {
        this.summary = summary;
        this.choices = List.copyOf(choices);
        setText(InfoSections.mutedHtml(summary));
        revalidate();
        repaint();
    }

    /** The summary shown, as plain text. */
    String summary() {
        return summary;
    }

    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int width = wrapWidth - insets.left - insets.right;
        int height = MIN_LINES * getFontMetrics(getFont()).getHeight();
        for (String choice : choices) {
            height = Math.max(height, htmlHeight(InfoSections.mutedHtml(choice), width));
        }
        return new Dimension(wrapWidth, height + insets.top + insets.bottom);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private int htmlHeight(String html, int width) {
        View view = BasicHTML.createHTMLView(this, html);
        view.setSize(width, 0);
        return (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
    }
}
