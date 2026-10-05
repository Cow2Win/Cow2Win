package org.c2w.gui;

import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.service.GuildLog;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shows a generated lineup report and lets the user save it themselves.
 *
 * <p>Nothing is saved automatically: the dialog receives the
 * raw report HTML (see {@link org.c2w.report.ReportGenerator#buildReportHtml})
 * plus a suggested file name, renders the HTML in a read-only pane, and
 * offers a "Save report..." toolbar button that opens a directory chooser
 * and writes the report into the chosen directory.
 */
public class ReportViewerDialog extends JDialog {

    private static final Dimension PREFERRED_SIZE = new Dimension(900, 700);

    private final String reportHtml;
    private final String suggestedFileName;
    /** Folder of the guild the report belongs to - saving it is noted in its guild log; may be null. */
    private final Path guildDir;

    public ReportViewerDialog(Frame owner, String reportHtml, String suggestedFileName, Path guildDir) {
        super(owner, LanguageService.displayTitle("report.title"), false);
        if (reportHtml == null) {
            throw new IllegalArgumentException("ReportViewerDialog needs reportHtml");
        }
        if (suggestedFileName == null || suggestedFileName.isBlank()) {
            throw new IllegalArgumentException("ReportViewerDialog needs a suggestedFileName");
        }
        this.reportHtml = reportHtml;
        this.suggestedFileName = suggestedFileName;
        this.guildDir = guildDir;

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
        JButton saveButton = new JButton(LanguageService.displayName("report.saveButton"));
        saveButton.setToolTipText(LanguageService.displayName("report.saveButtonTooltip"));
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
        chooser.setDialogTitle(LanguageService.displayName("report.chooseDirectory"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path targetFile = chooser.getSelectedFile().toPath().resolve(suggestedFileName);
        if (Files.exists(targetFile)) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    LanguageService.displayName("report.overwriteMessage", targetFile),
                    LanguageService.displayName("report.overwriteTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            Files.writeString(targetFile, reportHtml, StandardCharsets.UTF_8);
            Logger.log("Report saved: " + targetFile);
            GuildLog.event(guildDir, "guildLog.reportSaved", suggestedFileName);
            JOptionPane.showMessageDialog(this, LanguageService.displayName("report.savedMessage", targetFile),
                    LanguageService.displayName("report.savedTitle"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("report.saveError") + "\n" + e.getMessage(),
                    LanguageService.displayName("report.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
