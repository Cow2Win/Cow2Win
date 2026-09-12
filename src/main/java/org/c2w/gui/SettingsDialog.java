package org.c2w.gui;

import org.c2w.util.Config;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog opened from Cow2Frame's "Settings" > "Configs" menu item (see
 * Cow2Frame#buildMenuBar). Currently only lets the user change the display
 * language, but is named generically since more application-wide settings
 * are expected to move in here later.
 */
public class SettingsDialog extends JDialog {

    private final JComboBox<String> languageComboBox = new JComboBox<>();
    private final JButton okButton = new JButton("OK");
    private final JButton cancelButton = new JButton("Cancel");

    private boolean confirmed = false;

    public SettingsDialog(Frame owner) {
        super(owner, "Cow2Win - Settings", true);
        buildUi();
        preselectCurrentLanguage();
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(formPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> onCancel());
    }

    /**
     * Selects whichever {@link LanguageService#AVAILABLE_LANGUAGES} entry
     * matches the currently configured language file, defaulting to the
     * first entry if none matches (e.g. no config saved yet).
     */
    private void preselectCurrentLanguage() {
        String configured = Config.getLanguage();
        String[][] languages = LanguageService.AVAILABLE_LANGUAGES;
        for (int i = 0; i < languages.length; i++) {
            if (languages[i][1].equals(configured)) {
                languageComboBox.setSelectedIndex(i);
                return;
            }
        }
        languageComboBox.setSelectedIndex(0);
    }

    private void onOk() {
        String selectedLanguageFile = LanguageService.AVAILABLE_LANGUAGES[languageComboBox.getSelectedIndex()][1];
        boolean changed = !selectedLanguageFile.equals(Config.getLanguage());
        Config.setLanguage(selectedLanguageFile);
        Config.save();
        // So that any lookups happening right after this dialog closes
        // already see the new language, even though most of the UI (built
        // once at startup, see ToolbarPanel/TeamsOverviewPanel) still needs
        // a restart to actually re-render with it - see the notice below.
        LanguageService.resetCache();
        confirmed = true;
        Frame owner = getOwner() instanceof Frame ? (Frame) getOwner() : null;
        setVisible(false);
        dispose();
        if (changed) {
            JOptionPane.showMessageDialog(owner,
                    "Language changed. Restart Cow2Win for the change to take full effect.",
                    "Settings", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onCancel() {
        confirmed = false;
        setVisible(false);
        dispose();
    }

    /** True if the dialog was confirmed via the OK button (rather than Cancel or the window close button). */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Shows the dialog modally and waits for user input.
     *
     * @param owner parent window (may be null)
     * @return the dialog after it was closed - check {@link #isConfirmed()} to see whether the user saved a change
     */
    public static SettingsDialog show(Frame owner) {
        SettingsDialog dialog = new SettingsDialog(owner);
        dialog.setVisible(true);
        return dialog;
    }
}
