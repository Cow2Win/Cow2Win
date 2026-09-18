package org.c2w.gui;

import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.Config;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Dialog opened from Cow2Frame's "File" > "Settings" menu item (see
 * Cow2Frame#buildMenuBar). Lets the user change the display language, the
 * default lineup algorithm (see {@link org.c2w.gui.ToolbarPanel#onRunAlgorithm}),
 * and the backup directory (see org.c2w.util.BackupService); named
 * generically since more application-wide settings are expected to move in
 * here later.
 */
public class SettingsDialog extends JDialog {

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "save" toolbar button (see {@link #onOk()}). */
    private static final String KEY_SAVE_SETTINGS = "settingsDialog.saveSettings";

    /** Language file key for the language combo box's label (see {@link #buildUi()}). */
    private static final String KEY_LANGUAGE = "settingsDialog.language";

    /** Language file key for the algorithm combo box's label (see {@link #buildUi()}). */
    private static final String KEY_DEFAULT_ALGORITHM = "settingsDialog.defaultAlgorithm";

    /** Language file key for the backup directory field's label (see {@link #buildUi()}). */
    private static final String KEY_BACKUP_DIRECTORY = "settingsDialog.backupDirectory";

    /** Classpath path of the "save" button's icon - same icon every other save {@link FlatButton} in the app uses. */
    private static final String ICON_SAVE_SETTINGS = "/images/app/save.png";

    /** Target size of the toolbar icon. */
    private static final int TOOLBAR_ICON_SIZE = 20;

    private final JComboBox<String> languageComboBox = new JComboBox<>();

    /**
     * Lists {@link LineupAlgorithms#ALL} by {@link LineupAlgorithm#displayName()}
     * (added 2026-09-15, see Cow2Win todos 3.4) - a plain {@code JComboBox<String>}
     * like {@link #languageComboBox} rather than a {@code JComboBox<LineupAlgorithm>},
     * since {@link LineupAlgorithm} has no {@code toString()} of its own and this
     * avoids a custom cell renderer just for one combo box. The selected display
     * name is what actually gets persisted via {@link Config#setDefaultAlgorithm}
     * (see {@link #onOk()}) - simple and consistent with how {@link #languageComboBox}
     * persists a language name rather than an index.
     */
    private final JComboBox<String> algorithmComboBox = new JComboBox<>();
    private final JTextField backupDirField = new JTextField(20);
    private final JButton browseBackupDirButton = new JButton("...");

    private boolean confirmed = false;

    public SettingsDialog(Frame owner) {
        super(owner, LanguageService.displayName("menu.settings"), true);
        setLayout(new BorderLayout());
        add(buildToolbarPanel(), BorderLayout.NORTH);
        buildUi();
        preselectCurrentValues();
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_SETTINGS, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_SETTINGS));
        saveButton.addActionListener(e -> onOk());
        buttons.add(saveButton);
        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    private void buildUi() {
        for (String language : LanguageService.availableLanguages()) {
            languageComboBox.addItem(language);
        }
        for (LineupAlgorithm algorithm : LineupAlgorithms.ALL) {
            algorithmComboBox.addItem(algorithm.displayName());
        }

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel(LanguageService.displayName(KEY_LANGUAGE)), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(languageComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(LanguageService.displayName(KEY_DEFAULT_ALGORITHM)), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(algorithmComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(LanguageService.displayName(KEY_BACKUP_DIRECTORY)), gbc);

        JPanel backupDirPanel = new JPanel(new BorderLayout(4, 0));
        backupDirPanel.add(backupDirField, BorderLayout.CENTER);
        backupDirPanel.add(browseBackupDirButton, BorderLayout.EAST);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(backupDirPanel, gbc);

        add(formPanel, BorderLayout.CENTER);

        browseBackupDirButton.addActionListener(e -> onBrowseBackupDir());
    }

    /**
     * Selects whichever {@link LanguageService#availableLanguages()} entry
     * matches the currently configured language (via {@link
     * LanguageService#configuredLanguage()}, so an older, pre-restructuring
     * config value is matched correctly too), defaulting to the first entry
     * if none matches (e.g. no config saved yet); selects whichever {@link
     * LineupAlgorithms#ALL} entry matches {@link Config#getDefaultAlgorithm()}
     * the same way (added 2026-09-15, see Cow2Win todos 3.4); and fills in
     * the currently configured backup directory (see {@link Config#getBackupDir()}).
     */
    private void preselectCurrentValues() {
        String configured = LanguageService.configuredLanguage();
        int matchedIndex = -1;
        for (int i = 0; i < languageComboBox.getItemCount(); i++) {
            if (languageComboBox.getItemAt(i).equals(configured)) {
                matchedIndex = i;
                break;
            }
        }
        languageComboBox.setSelectedIndex(matchedIndex >= 0 ? matchedIndex : 0);

        String configuredAlgorithm = Config.getDefaultAlgorithm();
        boolean algorithmMatched = false;
        for (int i = 0; i < algorithmComboBox.getItemCount(); i++) {
            if (algorithmComboBox.getItemAt(i).equals(configuredAlgorithm)) {
                algorithmComboBox.setSelectedIndex(i);
                algorithmMatched = true;
                break;
            }
        }
        if (!algorithmMatched && algorithmComboBox.getItemCount() > 0) {
            algorithmComboBox.setSelectedIndex(0);
        }

        backupDirField.setText(Config.getBackupDir());
    }

    private void onBrowseBackupDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select backup directory");
        String currentPath = backupDirField.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            // getAbsoluteFile() so a relative path (e.g. the default "backup")
            // still resolves to somewhere sensible even if it doesn't exist yet.
            chooser.setCurrentDirectory(currentDir.getAbsoluteFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            backupDirField.setText(chooser.getSelectedFile().getPath());
        }
    }

    private void onOk() {
        String selectedLanguage = (String) languageComboBox.getSelectedItem();
        boolean languageChanged = !selectedLanguage.equals(LanguageService.configuredLanguage());
        Config.setLanguage(selectedLanguage);
        if (algorithmComboBox.getSelectedItem() != null) {
            Config.setDefaultAlgorithm((String) algorithmComboBox.getSelectedItem());
        }
        Config.setBackupDir(backupDirField.getText().trim());
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
        if (languageChanged) {
            JOptionPane.showMessageDialog(owner,
                    "Language changed. Restart Cow2Win for the change to take full effect.",
                    "Settings", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** True if the dialog was confirmed via the save button (rather than the window close button). */
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
