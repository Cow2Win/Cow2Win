package org.c2w.gui.stage;

import org.c2w.gui.common.IconLoader;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The building blocks of the info panels of the stage views: sections with a small heading,
 * left-aligned lines, muted (wrapping) hint texts, and number/date formatting in the
 * configured language.
 */
final class InfoSections {

    static final Color HEADING_COLOR = new Color(255, 255, 255, 150);
    static final Color MUTED_COLOR = new Color(255, 255, 255, 130);

    /** Width a muted text wraps at - fits the info panel (see {@link StageView#INFO_PANEL_WIDTH}). */
    private static final int WRAP_WIDTH = StageView.INFO_PANEL_WIDTH - 50;

    private InfoSections() {
    }

    /** The panel holding the sections: vertical, transparent, with padding. */
    static JPanel infoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return panel;
    }

    /** An empty, transparent body of a section - filled by the view and refreshed as needed. */
    static JPanel sectionBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        return body;
    }

    /** Adds a section to {@code panel}: the heading (capitals, muted, bold) and its {@code body}. */
    static void addSection(JPanel panel, String headingKey, JPanel body) {
        JLabel heading = new JLabel(LanguageService.displayName(headingKey).toUpperCase(JournalTexts.locale()));
        heading.setForeground(HEADING_COLOR);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() - 1f));
        heading.setBorder(BorderFactory.createEmptyBorder(panel.getComponentCount() == 0 ? 0 : 14, 0, 6, 0));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(body);
    }

    /** Adds {@code line} left-aligned to {@code section}. */
    static void addLine(JPanel section, Component line) {
        if (line instanceof JComponent component) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        section.add(line);
    }

    /** A muted label that wraps its text within the narrow info panel. */
    static JLabel mutedLabel(String text) {
        // An HTML label ignores the foreground color - the color goes into a font tag instead
        // (a color in the body style would stop the width from wrapping the text).
        JLabel label = wrappingLabel(colored(escape(text), MUTED_COLOR));
        label.setForeground(MUTED_COLOR);
        return label;
    }

    /** A label in {@code color} that wraps its text within the narrow info panel. */
    static JLabel coloredLabel(String text, Color color) {
        JLabel label = wrappingLabel(colored(escape(text), color));
        label.setForeground(color);
        return label;
    }

    /** An HTML label wrapping {@code htmlContent} within the narrow info panel. */
    private static JLabel wrappingLabel(String htmlContent) {
        return new WrappingLabel("<html>" + htmlContent + "</html>");
    }

    /**
     * An HTML label of fixed width ({@link #WRAP_WIDTH}) whose height fits the wrapped text. A CSS
     * width in the HTML would not do: Swing scales CSS pixels, so "250px" ends up about a third
     * wider - wider than the info panel - and a short text is cut instead of wrapped.
     */
    private static final class WrappingLabel extends JLabel {

        WrappingLabel(String html) {
            super(html);
        }

        @Override
        public Dimension getPreferredSize() {
            Object view = getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(view instanceof javax.swing.text.View htmlView)) {
                return super.getPreferredSize();
            }
            Insets insets = getInsets();
            int width = WRAP_WIDTH - insets.left - insets.right;
            htmlView.setSize(width, 0);
            int height = (int) Math.ceil(htmlView.getPreferredSpan(javax.swing.text.View.Y_AXIS));
            return new Dimension(WRAP_WIDTH, height + insets.top + insets.bottom);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    private static String colored(String htmlContent, Color color) {
        return "<font color='" + cssColor(color) + "'>" + htmlContent + "</font>";
    }

    /** "→" - or "->" if the label font cannot display the arrow. */
    static String arrow() {
        Font font = UIManager.getFont("Label.font");
        return font == null || font.canDisplay('→') ? "→" : "->";
    }

    /** {@code color} as CSS hex - a translucent color blended over the look and feel's panel background. */
    private static String cssColor(Color color) {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            background = Color.DARK_GRAY;
        }
        float alpha = color.getAlpha() / 255f;
        int red = Math.round(color.getRed() * alpha + background.getRed() * (1 - alpha));
        int green = Math.round(color.getGreen() * alpha + background.getGreen() * (1 - alpha));
        int blue = Math.round(color.getBlue() * alpha + background.getBlue() * (1 - alpha));
        return String.format("#%02x%02x%02x", red, green, blue);
    }

    /** {@code text} followed by the colored difference: green for a gain, red for a loss (the colors of the "changes" view). */
    static JComponent valueLine(String text, int diff) {
        // One wrapping HTML label: the difference moves to the next line if the column is too narrow.
        Color diffColor = diff > 0 ? IconLoader.GREEN : diff < 0 ? IconLoader.RED : MUTED_COLOR;
        return wrappingLabel(escape(text) + " &nbsp; " + colored((diff > 0 ? "+" : "") + number(diff), diffColor));
    }

    /** A bold label, a little larger - e.g. the name of the selected fortification or member. */
    static JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 1f));
        return label;
    }

    /** Lays out and repaints {@code section} after its content changed. */
    static void relayout(JPanel section) {
        section.revalidate();
        section.repaint();
    }

    /** Every label text below {@code container} - for tests. */
    static void collectTexts(Container container, List<String> texts) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                texts.add(label.getText());
            }
            if (child instanceof Container nested) {
                collectTexts(nested, texts);
            }
        }
    }

    /** {@code value} with grouping in the configured language. */
    static String number(long value) {
        return NumberFormat.getIntegerInstance(JournalTexts.locale()).format(value);
    }

    /** {@code date} in the short format of the configured language, "" for null. */
    static String shortDate(LocalDate date) {
        return date == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(JournalTexts.locale()).format(date);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
