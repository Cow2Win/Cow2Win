package org.c2w.gui;

import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;

public class InitialSetupDialog extends JDialog {

    private final JComboBox<String> languageComboBox = new JComboBox<>();
    private final JTextField guildNameField = new JTextField("myGuild",20);
    private final JButton okButton = new JButton("OK");

    private String selectedLanguageFile;
    private String guildName;
    private boolean confirmed = false;

    public InitialSetupDialog(Frame owner) {
        super(owner, "Cow2Win - Initial Setup", true);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    private void buildUi() {
        for (String[] language : LanguageService.AVAILABLE_LANGUAGES) {
            languageComboBox.addItem(language[0]);
        }

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Language:"), gbc);
        gbc.gridx = 1;
        formPanel.add(languageComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Guild name:"), gbc);
        gbc.gridx = 1;
        formPanel.add(guildNameField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new JLabel(
                "<html><body style='padding:8px'>Welcome to Cow2Win Please enter language and guild name once "
                        + "to get started.</body></html>"), BorderLayout.NORTH);
        getContentPane().add(formPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        guildNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateOkButtonState();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateOkButtonState();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateOkButtonState();
            }
        });

        okButton.addActionListener(e -> onOk());
    }

    private void updateOkButtonState() {
        okButton.setEnabled(!guildNameField.getText().trim().isEmpty());
    }

    private void onOk() {
        String enteredName = guildNameField.getText().trim();
        if (enteredName.isEmpty()) {
            return;
        }
        int languageIndex = languageComboBox.getSelectedIndex();
        selectedLanguageFile = LanguageService.AVAILABLE_LANGUAGES[languageIndex][1];
        guildName = enteredName;
        confirmed = true;
        setVisible(false);
        dispose();
    }

    /** True if the dialog was confirmed via the OK button. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** File name of the chosen language properties file, e.g. "deutsch.txt". Only valid after OK. */
    public String getSelectedLanguageFile() {
        return selectedLanguageFile;
    }

    /** The raw, entered guild name, e.g. "MyGuild". Only valid after OK. */
    public String getGuildName() {
        return guildName;
    }

    /**
     * Shows the dialog modally and waits for user input.
     *
     * @param owner parent window (may be null)
     * @return the confirmed dialog (isConfirmed() == true, since the dialog
     *         cannot be cancelled)
     */
    public static InitialSetupDialog show(Frame owner) {
        InitialSetupDialog dialog = new InitialSetupDialog(owner);
        dialog.setVisible(true);
        return dialog;
    }
}
