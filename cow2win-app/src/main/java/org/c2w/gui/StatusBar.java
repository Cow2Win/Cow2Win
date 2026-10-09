package org.c2w.gui;

import org.c2w.i18n.LanguageService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;

/**
 * The status bar at the bottom of the main window: one line in small, muted type on the dark
 * background of the {@link ContextBar} - "Data status" followed by the parts set by the
 * {@link DataStatusController} (newest defense log, outdated teams, lineup status), separated
 * by " · ".
 */
public class StatusBar extends JPanel {

    private static final String KEY_LABEL = "statusBar.label";
    private static final String SEPARATOR = "  ·  ";
    private static final Color MUTED = new Color(255, 255, 255, 150);

    private final JLabel title = new JLabel(LanguageService.displayName(KEY_LABEL));
    private final JLabel text = new JLabel();

    public StatusBar() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 3));
        setOpaque(true);
        setBackground(ContextBar.barBackground());
        setBorder(new MatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 40)));
        Font base = title.getFont();
        Font small = base.deriveFont(Font.PLAIN, base.getSize2D() - 1f);
        title.setFont(small.deriveFont(Font.BOLD));
        title.setForeground(MUTED);
        text.setFont(small);
        text.setForeground(MUTED);
        add(title);
        add(text);
    }

    /** Shows {@code parts} (missing values already left out) after the "Data status" label. */
    public void setParts(List<String> parts) {
        text.setText(String.join(SEPARATOR, parts));
        revalidate();
        repaint();
    }

    /** The shown text after the label - for tests. */
    String text() {
        return text.getText();
    }
}
