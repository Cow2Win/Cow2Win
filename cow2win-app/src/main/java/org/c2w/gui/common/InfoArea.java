package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;

/**
 * An information area at the top of a dialog or tab: wrapped, read-only white text in a
 * accent-tinted frame with rounded corners, like the "Live" label of the context bar (see
 * {@link HintStyle}). Its preferred width is tiny, so the text never decides the window's width,
 * and it is always a fixed number of lines high (plus padding), so changing the text does not
 * move the content below. Put it into {@link #framed(InfoArea)} for the usual space around it.
 */
public final class InfoArea extends JTextArea {
    /** Corner arc of the frame - 14 px radius. */
    private static final int ARC = 28;

    private final int rows;

    /** An information area {@code rows} lines high. */
    public InfoArea(int rows) {
        this.rows = rows;
        setLineWrap(true);
        setWrapStyleWord(true);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        // The look and feel sets text areas in bold - the information reads like a label.
        setFont(UIManager.getFont("Label.font"));
        // Padding between the frame and the text.
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    /** {@code info} in a panel with the space between the frame and the surrounding content. */
    public static JPanel framed(InfoArea info) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        holder.add(info, BorderLayout.CENTER);
        return holder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        HintStyle.paintHintBackground(g, this, AppColors.ACCENT_TEXT, ARC);
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();
        return new Dimension(1, rows * getRowHeight() + insets.top + insets.bottom);
    }
}
