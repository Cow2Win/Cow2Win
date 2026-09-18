package org.c2w.gui;

import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;

public class LogPanel extends JPanel {

    /** Preferred height of the scrollable log area (width is stretched to fill {@link java.awt.BorderLayout#SOUTH}). */
    private static final int PREFERRED_HEIGHT = 140;

    private final JTextArea textArea = new JTextArea();

    public LogPanel() {
        super(new BorderLayout());
        textArea.setEditable(false);
        textArea.setLineWrap(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(0, PREFERRED_HEIGHT));
        add(scrollPane, BorderLayout.CENTER);

        Logger.addListener(this::append);
    }

    /**
     * Appends one already-formatted {@link Logger} entry as a new line and
     * scrolls the text area to the end - always performed on the AWT event
     * dispatch thread (see {@link SwingUtilities#invokeLater}) since
     * {@link Logger#log} does not guarantee it is only ever called from
     * there (see {@link Logger} class Javadoc).
     */
    private void append(String entry) {
        Runnable appendOnEdt = () -> {
            textArea.append(entry + System.lineSeparator());
            textArea.setCaretPosition(textArea.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            appendOnEdt.run();
        } else {
            SwingUtilities.invokeLater(appendOnEdt);
        }
    }
}
