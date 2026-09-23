package org.c2w.gui;

import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shows a generated lineup report and lets the user save it themselves.
 *
 * <p>Until 2026-09-23 the report was written to disk the moment it was
 * generated and this dialog just displayed that file. Per the user's
 * explicit request it is no longer auto-saved: the dialog now receives the
 * raw report HTML (see {@link org.c2w.util.ReportGenerator#buildReportHtml})
 * plus a suggested file name, renders the HTML in a read-only pane, and
 * offers a "Save report..." toolbar button that opens a directory chooser
 * and writes the report into the chosen directory.
 */
public class ReportViewerDialog extends JDialog {

    private static final Dimension PREFERRED_SIZE = new Dimension(900, 700);

    private final String reportHtml;
    private final String suggestedFileName;

    public ReportViewerDialog(Frame owner, String reportHtml, String suggestedFileName) {
        super(owner, "Cow2Win - Report", false);
        if (reportHtml == null) {
            throw new IllegalArgumentException("ReportViewerDialog needs reportHtml");
        }
        if (suggestedFileName == null || suggestedFileName.isBlank()) {
            throw new IllegalArgumentException("ReportViewerDialog needs a suggestedFileName");
        }
        this.reportHtml = reportHtml;
        this.suggestedFileName = suggestedFileName;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolBar(), BorderLayout.NORTH);
        add(buildReportScrollPane(), BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
    }

    private JToolBar buildToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        JButton saveButton = new JButton("Save report...");
        saveButton.setToolTipText("Choose a directory and save this report as an HTML file");
        saveButton.addActionListener(e -> onSave());
        toolBar.add(saveButton);
        return toolBar;
    }

    private JScrollPane buildReportScrollPane() {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setText(reportHtml);
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(PREFERRED_SIZE);
        return scrollPane;
    }

    /**
     * Opens a directory-only chooser and, once the user confirms, writes the
     * report HTML into the chosen directory under {@link #suggestedFileName}
     * (asking before overwriting an existing file). A cancelled chooser does
     * nothing; any write failure is shown in an error dialog rather than
     * thrown, since this is a UI action with no caller to handle it.
     */
    private void onSave() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a directory to save the report in");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path targetFile = chooser.getSelectedFile().toPath().resolve(suggestedFileName);
        if (Files.exists(targetFile)) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "This file already exists:\n" + targetFile + "\n\nOverwrite it?",
                    "Overwrite report?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            Files.writeString(targetFile, reportHtml, StandardCharsets.UTF_8);
            Logger.log("Report saved: " + targetFile);
            JOptionPane.showMessageDialog(this, "Report saved to:\n" + targetFile,
                    "Report saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save the report:\n" + e.getMessage(),
                    "Error while saving report", JOptionPane.ERROR_MESSAGE);
        }
    }
}
