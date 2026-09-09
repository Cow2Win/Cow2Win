package org.c2w.gui;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

public class ReportViewerDialog extends JDialog {

    private static final Dimension PREFERRED_SIZE = new Dimension(900, 700);

    public ReportViewerDialog(Frame owner, Path reportFile) {
        super(owner, "Cow2Win - Report", false);
        if (reportFile == null) {
            throw new IllegalArgumentException("ReportViewerDialog needs a reportFile");
        }
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        try {
            editorPane.setPage(reportFile.toUri().toURL());
        } catch (IOException e) {
            editorPane.setText("<html><body>Could not display report:<br>" + e.getMessage() + "</body></html>");
        }

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(PREFERRED_SIZE);
        add(scrollPane, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
    }
}
