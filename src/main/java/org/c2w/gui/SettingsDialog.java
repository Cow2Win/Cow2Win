package org.c2w.gui;

import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.Config;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog opened from Cow2Frame's "File" > "Settings" menu item (see
 * Cow2Frame#buildMenuBar). Lets the user change the display language, the
 * default lineup algorithm (see {@link org.c2w.gui.ToolbarPanel#onRunAlgorithm}),
 * the backup directory (see org.c2w.util.BackupService) and the workspace
 * directory (see {@link Config#getWorkspaceDir()}); named generically since
 * more application-wide settings are expected to move in here later.
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

    /** Language file key for the workspace directory field's label (see {@link #buildUi()}). */
    private static final String KEY_WORKSPACE_DIRECTORY = "settingsDialog.workspaceDirectory";

    /**
     * Name of the dedicated workspace marker folder created inside a chosen
     * directory when the user selects a workspace that is not itself such a
     * folder (see {@link #normalizeWorkspaceDir(String)}). Keeping Cow2Win's
     * data in its own {@value #WORKSPACE_MARKER_DIR_NAME} subfolder avoids
     * mixing guilds/lineups/log into an arbitrary user-picked directory (e.g.
     * a Documents folder that already holds unrelated files).
     */
    private static final String WORKSPACE_MARKER_DIR_NAME = ".cow2Win";

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
    private final JTextField workspaceDirField = new JTextField(20);
    private final JButton browseWorkspaceDirButton = new JButton("...");

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

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(LanguageService.displayName(KEY_WORKSPACE_DIRECTORY)), gbc);

        JPanel workspaceDirPanel = new JPanel(new BorderLayout(4, 0));
        workspaceDirPanel.add(workspaceDirField, BorderLayout.CENTER);
        workspaceDirPanel.add(browseWorkspaceDirButton, BorderLayout.EAST);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(workspaceDirPanel, gbc);

        add(formPanel, BorderLayout.CENTER);

        browseBackupDirButton.addActionListener(e -> onBrowseBackupDir());
        browseWorkspaceDirButton.addActionListener(e -> onBrowseWorkspaceDir());
    }

    /**
     * Selects whichever {@link LanguageService#availableLanguages()} entry
     * matches the currently configured language (via {@link
     * LanguageService#configuredLanguage()}, so an older, pre-restructuring
     * config value is matched correctly too), defaulting to the first entry
     * if none matches (e.g. no config saved yet); selects whichever {@link
     * LineupAlgorithms#ALL} entry matches {@link Config#getDefaultAlgorithm()}
     * the same way (added 2026-09-15, see Cow2Win todos 3.4); and fills in
     * the currently configured backup directory (see {@link Config#getBackupDir()})
     * and workspace directory (see {@link Config#getWorkspacePath()}).
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
        workspaceDirField.setText(Config.getWorkspacePath());
    }

    private void onBrowseBackupDir() {
        String chosen = browseForDirectory(backupDirField.getText(), "Select backup directory");
        if (chosen != null) {
            backupDirField.setText(chosen);
        }
    }

    private void onBrowseWorkspaceDir() {
        String chosen = browseForDirectory(workspaceDirField.getText(), "Select workspace directory");
        if (chosen != null) {
            workspaceDirField.setText(chosen);
        }
    }

    /**
     * Shared by {@link #onBrowseBackupDir()} and {@link #onBrowseWorkspaceDir()}:
     * opens a directory-only chooser starting at {@code currentPath} (falling
     * back to sensibly wherever that resolves, even if it doesn't exist yet -
     * see {@code getAbsoluteFile()} below - since both fields' defaults are
     * relative paths), and returns the chosen path, or {@code null} if the
     * dialog was cancelled.
     */
    private String browseForDirectory(String currentPath, String dialogTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(dialogTitle);
        String trimmedPath = currentPath.trim();
        if (!trimmedPath.isEmpty()) {
            File currentDir = new File(trimmedPath);
            chooser.setCurrentDirectory(currentDir.getAbsoluteFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getPath();
        }
        return null;
    }

    /**
     * Normalizes a user-selected workspace directory so it always ends in a
     * dedicated {@value #WORKSPACE_MARKER_DIR_NAME} folder: if the chosen
     * folder is not itself named {@value #WORKSPACE_MARKER_DIR_NAME} (compared
     * case-insensitively), a {@value #WORKSPACE_MARKER_DIR_NAME} subfolder is
     * created inside it and that subfolder becomes the workspace. The folder is
     * created eagerly here (rather than lazily on first write) so the returned
     * path is a real, usable directory the moment it is saved. A blank
     * selection is passed through unchanged, letting
     * {@link Config#setWorkspacePath(String)} fall back to its default.
     */
    private static String normalizeWorkspaceDir(String chosen) {
        if (chosen == null || chosen.isBlank()) {
            return chosen;
        }
        Path selected = Paths.get(chosen.trim());
        Path folderName = selected.getFileName();
        boolean alreadyMarker = folderName != null
                && folderName.toString().equalsIgnoreCase(WORKSPACE_MARKER_DIR_NAME);
        Path workspace = alreadyMarker ? selected : selected.resolve(WORKSPACE_MARKER_DIR_NAME);
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            Logger.logException("Could not create workspace directory " + workspace, e);
        }
        return workspace.toString();
    }

    private void onOk() {
        String selectedLanguage = (String) languageComboBox.getSelectedItem();
        boolean languageChanged = !selectedLanguage.equals(LanguageService.configuredLanguage());
        String workspacePath = normalizeWorkspaceDir(workspaceDirField.getText());
        boolean workspaceChanged = !workspacePath.equals(Config.getWorkspacePath());
        // Reflect the normalized (possibly .cow2Win-appended) path back into the
        // field, so what is shown always matches what actually gets saved.
        workspaceDirField.setText(workspacePath);
        Config.setLanguage(selectedLanguage);
        if (algorithmComboBox.getSelectedItem() != null) {
            Config.setDefaultAlgorithm((String) algorithmComboBox.getSelectedItem());
        }
        Config.setBackupDir(backupDirField.getText().trim());
        Config.setWorkspacePath(workspacePath);
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
        // Both the loaded guild/lineup (see AppContext, built once at
        // startup from the *old* workspace) and the just-run BackupService
        // check only reflect a workspace change after a restart, so a
        // changed workspace gets the same kind of notice as a changed
        // language rather than looking like it silently took effect.
        List<String> restartNotices = new ArrayList<>();
        if (languageChanged) {
            restartNotices.add("Language changed.");
        }
        if (workspaceChanged) {
            restartNotices.add("Workspace directory changed.");
        }
        if (!restartNotices.isEmpty()) {
            restartNotices.add("Restart Cow2Win for the change to take full effect.");
            JOptionPane.showMessageDialog(owner,
                    String.join(" ", restartNotices),
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
