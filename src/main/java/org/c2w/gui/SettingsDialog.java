package org.c2w.gui;

import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.infra.WorkspaceMaintenance;
import org.c2w.service.WorkspaceBootstrap;

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
 * MainMenuBar). Lets the user change the display language, the
 * default hero and titan lineup algorithms (see {@link org.c2w.gui.ActionBar#onRunAlgorithm}),
 * the backup directory (see org.c2w.infra.BackupService) and the workspace
 * directory (see {@link Config#getWorkspaceDir()}) and the stale check of the data status
 * (see {@link Config#isStaleCheckEnabled()}) and the CowScore bonus percentages (see
 * {@link CowScoreBonusesPanel} - they take effect right away, without a restart); named
 * generically since more application-wide settings are expected to move in here later.
 *
 * <p>Two buttons behind the directories act on the workspace itself (see
 * {@link WorkspaceMaintenance}), both on the next start - Cow2Win ends for it:
 * <ul>
 *   <li>"Restore workspace from backup" (behind the backup directory) replaces the workspace with
 *       the daily or weekly backup in the directory shown in the field;</li>
 *   <li>"Move workspace" (behind the workspace directory) moves the data of the workspace to an
 *       empty folder - unlike "...", which only switches to another workspace and leaves the data
 *       where it is.</li>
 * </ul>
 * The dialog is about 50% wider than its packed size, so long paths stay readable; its directory
 * choosers are 50% larger than the default.
 */
public class SettingsDialog extends JDialog {

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "save" toolbar button (see {@link #onOk()}). */
    private static final String KEY_SAVE_SETTINGS = "settingsDialog.saveSettings";

    /** Language file key for the language combo box's label (see {@link #buildUi()}). */
    private static final String KEY_LANGUAGE = "settingsDialog.language";

    /** Language file key for the hero algorithm combo box's label (see {@link #buildUi()}). */
    private static final String KEY_DEFAULT_HERO_ALGORITHM = "settingsDialog.defaultHeroAlgorithm";

    /** Language file key for the titan algorithm combo box's label (see {@link #buildUi()}). */
    private static final String KEY_DEFAULT_TITAN_ALGORITHM = "settingsDialog.defaultTitanAlgorithm";

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
    private static final String WORKSPACE_MARKER_DIR_NAME = WorkspaceMaintenance.WORKSPACE_DIR_NAME;

    /** Classpath path of the "save" button's icon - same icon every other save {@link FlatButton} in the app uses. */
    private static final String ICON_SAVE_SETTINGS = "/images/app/save.png";

    /** Target size of the toolbar icon. */
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Classpath paths of the "restore workspace from backup" and "move workspace" icons. */
    private static final String ICON_RESTORE_WORKSPACE = "/images/app/restore.png";
    private static final String ICON_MOVE_WORKSPACE = "/images/app/move.png";

    /** The dialog and its directory choosers are this much larger than their packed/default size. */
    static final double SIZE_FACTOR = 1.5;

    /** How the dialog ends the application for a scheduled restore or move - implemented by the main window. */
    public interface AppExit {
        /** True if nothing is unsaved or the user agrees to close anyway. */
        boolean confirmUnsavedChanges();

        /** Ends the application without asking. */
        void exit();
    }

    private final JComboBox<String> languageComboBox = new JComboBox<>();

    /**
     * Lists {@link LineupAlgorithms#HERO} by {@link LineupAlgorithm#displayName()} - a plain {@code JComboBox<String>} like {@link
     * #languageComboBox} rather than a {@code JComboBox<LineupAlgorithm>}, since
     * {@link LineupAlgorithm} has no {@code toString()} of its own. The selected
     * display name is what actually gets persisted via {@link
     * Config#setDefaultHeroAlgorithm} (see {@link #onOk()}).
     */
    private final JComboBox<String> heroAlgorithmComboBox = new JComboBox<>();

    /** Titan counterpart of {@link #heroAlgorithmComboBox}, listing {@link LineupAlgorithms#TITAN}. */
    private final JComboBox<String> titanAlgorithmComboBox = new JComboBox<>();
    private final JTextField backupDirField = new JTextField(20);
    private final JButton browseBackupDirButton = new JButton("...");
    private final JTextField workspaceDirField = new JTextField(20);
    private final JButton browseWorkspaceDirButton = new JButton("...");
    private final FlatButton restoreWorkspaceButton = new FlatButton(
            IconLoader.iconFor(ICON_RESTORE_WORKSPACE, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
    private final FlatButton moveWorkspaceButton = new FlatButton(
            IconLoader.iconFor(ICON_MOVE_WORKSPACE, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
    private final AppExit appExit;
    /** Width of the dialog after {@code pack()}, before it is widened by {@link #SIZE_FACTOR}. */
    private final int packedWidth;
    /** Stale check of the data status: mark teams as outdated by age (see {@link Config#isStaleCheckEnabled()}). */
    /** The adjustable CowScore bonus percentages, see {@link CowScoreBonusesPanel}. */
    private final CowScoreBonusesPanel cowScoreBonusesPanel = new CowScoreBonusesPanel(Config.getCowScoreBonuses());
    private final JCheckBox staleCheckBox = new JCheckBox(LanguageService.displayName("settings.staleCheck"));
    private final JSpinner staleAfterDaysSpinner = new JSpinner(new SpinnerNumberModel(Config.DEFAULT_STALE_AFTER_DAYS,
            Config.MIN_STALE_AFTER_DAYS, Config.MAX_STALE_AFTER_DAYS, 1));
    /** Whether the user operated the stale check here - then it is never switched on automatically again. */
    private boolean staleCheckTouched;

    private boolean confirmed = false;

    public SettingsDialog(Frame owner, AppExit appExit) {
        super(owner, LanguageService.displayTitle("menu.settings"), true);
        this.appExit = appExit;
        setLayout(new BorderLayout());
        add(buildToolbarPanel(), BorderLayout.NORTH);
        buildUi();
        preselectCurrentValues();
        pack();
        // Wider than packed, so long paths stay readable - computed from the packed width, so it
        // fits every language and font. The extra width goes to the second column (weightx).
        packedWidth = getWidth();
        setSize((int) Math.round(packedWidth * SIZE_FACTOR), getHeight());
        sizeToScreen(owner);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    /** At most as high as the usable screen - then the form scrolls vertically. */
    private void sizeToScreen(Window owner) {
        GraphicsConfiguration screen = owner != null ? owner.getGraphicsConfiguration() : getGraphicsConfiguration();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        int usableHeight = screen.getBounds().height - screenInsets.top - screenInsets.bottom;
        if (getHeight() > usableHeight) {
            // Room for the vertical scroll bar, so no horizontal one is needed.
            int scrollBar = new JScrollBar(JScrollBar.VERTICAL).getPreferredSize().width;
            setSize(getWidth() + scrollBar, usableHeight);
        }
    }

    /** The CowScore bonuses area - package-visible for tests. */
    CowScoreBonusesPanel cowScoreBonusesPanel() {
        return cowScoreBonusesPanel;
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
        for (LineupAlgorithm algorithm : LineupAlgorithms.HERO) {
            heroAlgorithmComboBox.addItem(algorithm.displayName());
        }
        for (LineupAlgorithm algorithm : LineupAlgorithms.TITAN) {
            titanAlgorithmComboBox.addItem(algorithm.displayName());
        }
        // The combo boxes hold the stable (English) algorithm name that gets
        // persisted - only the rendered text is localized.
        heroAlgorithmComboBox.setRenderer(new LocalizedAlgorithmRenderer());
        titanAlgorithmComboBox.setRenderer(new LocalizedAlgorithmRenderer());

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(formPanel, gbc, 0, KEY_LANGUAGE, languageComboBox);
        addRow(formPanel, gbc, 1, KEY_DEFAULT_HERO_ALGORITHM, heroAlgorithmComboBox);
        addRow(formPanel, gbc, 2, KEY_DEFAULT_TITAN_ALGORITHM, titanAlgorithmComboBox);
        restoreWorkspaceButton.setToolTipText(LanguageService.displayName("settingsDialog.restoreWorkspace"));
        addRow(formPanel, gbc, 3, KEY_BACKUP_DIRECTORY,
                directoryPanel(backupDirField, browseBackupDirButton, restoreWorkspaceButton));
        moveWorkspaceButton.setToolTipText(LanguageService.displayName("settingsDialog.moveWorkspace"));
        addRow(formPanel, gbc, 4, KEY_WORKSPACE_DIRECTORY,
                directoryPanel(workspaceDirField, browseWorkspaceDirButton, moveWorkspaceButton));

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JPanel stalePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        stalePanel.add(staleCheckBox);
        stalePanel.add(Box.createHorizontalStrut(12));
        stalePanel.add(new JLabel(LanguageService.displayName("settings.staleAfterDays")));
        stalePanel.add(Box.createHorizontalStrut(6));
        stalePanel.add(staleAfterDaysSpinner);
        formPanel.add(stalePanel, gbc);

        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(16, 4, 4, 4);
        formPanel.add(cowScoreBonusesPanel, gbc);
        gbc.gridwidth = 1;

        // Scrolls only if the screen is too low for the whole form (see sizeToScreen).
        JScrollPane formScrollPane = new JScrollPane(formPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(formScrollPane, BorderLayout.CENTER);

        browseBackupDirButton.addActionListener(e -> onBrowseBackupDir());
        browseWorkspaceDirButton.addActionListener(e -> onBrowseWorkspaceDir());
        restoreWorkspaceButton.addActionListener(e -> onRestoreWorkspace());
        moveWorkspaceButton.addActionListener(e -> onMoveWorkspace());
        staleCheckBox.addActionListener(e -> {
            staleCheckTouched = true;
            staleAfterDaysSpinner.setEnabled(staleCheckBox.isSelected());
        });
        staleAfterDaysSpinner.addChangeListener(e -> staleCheckTouched = true);
    }

    /** One form row: label in the first column, {@code component} in the second, which takes all extra width. */
    private static void addRow(JPanel formPanel, GridBagConstraints gbc, int row, String labelKey, JComponent component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(LanguageService.displayName(labelKey)), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(component, gbc);
    }

    /** Directory field with its "..." button and, behind it, an icon button of the same height. */
    private static JPanel directoryPanel(JTextField field, JButton browseButton, JButton iconButton) {
        int height = browseButton.getPreferredSize().height;
        Dimension size = new Dimension(Math.max(iconButton.getPreferredSize().width, height), height);
        iconButton.setPreferredSize(size);
        iconButton.setMaximumSize(size);
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setOpaque(false);
        buttons.add(browseButton);
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(iconButton);
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(field, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    /**
     * Selects whichever {@link LanguageService#availableLanguages()} entry
     * matches the currently configured language (via {@link
     * LanguageService#configuredLanguage()}, so an older, pre-restructuring
     * config value is matched correctly too), defaulting to the first entry
     * if none matches (e.g. no config saved yet); selects whichever {@link
     * LineupAlgorithms#HERO}/{@link LineupAlgorithms#TITAN} entry matches {@link
     * Config#getDefaultHeroAlgorithm()}/{@link Config#getDefaultTitanAlgorithm()}
     * the same way; and fills in
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

        preselectAlgorithm(heroAlgorithmComboBox, Config.getDefaultHeroAlgorithm());
        preselectAlgorithm(titanAlgorithmComboBox, Config.getDefaultTitanAlgorithm());

        backupDirField.setText(Config.getBackupDir());
        workspaceDirField.setText(Config.getWorkspacePath());
        staleCheckBox.setSelected(Config.isStaleCheckEnabled());
        staleAfterDaysSpinner.setValue(Config.getStaleAfterDays());
        staleAfterDaysSpinner.setEnabled(staleCheckBox.isSelected());
        staleCheckTouched = false;
    }

    /** Selects the entry of {@code comboBox} equal to {@code configuredAlgorithm}, or the first entry if none matches. */
    private static void preselectAlgorithm(JComboBox<String> comboBox, String configuredAlgorithm) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (comboBox.getItemAt(i).equals(configuredAlgorithm)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        if (comboBox.getItemCount() > 0) {
            comboBox.setSelectedIndex(0);
        }
    }

    private void onBrowseBackupDir() {
        String chosen = browseForDirectory(backupDirField.getText(), LanguageService.displayName("settingsDialog.selectBackupDirectory"));
        if (chosen != null) {
            backupDirField.setText(chosen);
        }
    }

    private void onBrowseWorkspaceDir() {
        String chosen = browseForDirectory(workspaceDirField.getText(), LanguageService.displayName("settingsDialog.selectWorkspaceDirectory"));
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
        JFileChooser chooser = createDirectoryChooser(dialogTitle);
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

    /** A directory-only chooser {@link #SIZE_FACTOR} times as wide and high as the default - for long paths. */
    static JFileChooser createDirectoryChooser(String dialogTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(dialogTitle);
        Dimension size = chooser.getPreferredSize();
        chooser.setPreferredSize(new Dimension((int) Math.round(size.width * SIZE_FACTOR),
                (int) Math.round(size.height * SIZE_FACTOR)));
        return chooser;
    }

    /**
     * "Restore workspace from backup": offers the daily and weekly backup of the backup directory
     * in the field (also a value not saved yet), asks for confirmation, schedules the restore for
     * the next start and ends Cow2Win - without asking about unsaved changes (the restore replaces
     * them anyway) and without saving the other values of this dialog.
     */
    private void onRestoreWorkspace() {
        String backupDirText = backupDirField.getText().trim();
        Path backupDir = backupDirText.isEmpty() ? Config.getBackupDirPath() : Paths.get(backupDirText);
        List<WorkspaceMaintenance.BackupChoice> backups = WorkspaceMaintenance.availableBackups(backupDir);
        String title = LanguageService.displayName("settingsDialog.restoreWorkspace");
        if (backups.isEmpty()) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("workspaceMaintenance.restore.noBackup"),
                    title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] labels = backups.stream().map(SettingsDialog::backupLabel).toArray(String[]::new);
        Object selected = JOptionPane.showInputDialog(this, LanguageService.displayName("workspaceMaintenance.restore.select"),
                title, JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        if (selected == null) {
            return;
        }
        WorkspaceMaintenance.BackupChoice backup = backups.get(List.of(labels).indexOf(selected));
        String confirmLabel = LanguageService.displayName("workspaceMaintenance.restore.confirmButton");
        if (!confirmed(LanguageService.displayName("workspaceMaintenance.restore.confirm",
                WorkspaceMaintenance.BEFORE_RESTORE_FILE_NAME), title, confirmLabel)) {
            return;
        }
        Config.setPendingRestoreZip(backup.zip().toString());
        Config.save();
        dispose();
        appExit.exit();
    }

    /** "Daily backup of 2026-10-06 19:12". */
    private static String backupLabel(WorkspaceMaintenance.BackupChoice backup) {
        String key = backup.kind() == WorkspaceMaintenance.BackupKind.DAILY
                ? "workspaceMaintenance.restore.daily" : "workspaceMaintenance.restore.weekly";
        return LanguageService.displayName(key, WorkspaceBootstrap.formatBackupTime(backup.time()));
    }

    /**
     * "Move workspace": lets the user choose an empty folder for the data of the workspace in use,
     * checks it, asks for confirmation and about unsaved changes (they can still be saved - no data
     * is lost here), schedules the move for the next start and ends Cow2Win. The other values of
     * this dialog are not saved.
     */
    private void onMoveWorkspace() {
        Path workspace = Config.getWorkspaceDir().toAbsolutePath().normalize();
        Path parent = workspace.getParent();
        String chosen = browseForDirectory(parent == null ? workspace.toString() : parent.toString(),
                LanguageService.displayName("settingsDialog.selectMoveTarget"));
        if (chosen == null) {
            return;
        }
        Path target = WorkspaceMaintenance.normalizeWorkspaceDir(Paths.get(chosen.trim())).toAbsolutePath().normalize();
        String title = LanguageService.displayName("settingsDialog.moveWorkspace");
        WorkspaceMaintenance.MoveProblem problem = WorkspaceMaintenance.checkMoveTarget(workspace, target);
        if (problem != null) {
            JOptionPane.showMessageDialog(this, WorkspaceBootstrap.moveProblemText(problem, workspace, target),
                    title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        String confirmLabel = LanguageService.displayName("workspaceMaintenance.move.confirmButton");
        if (!confirmed(LanguageService.displayName("workspaceMaintenance.move.confirm", workspace.toString(), target.toString()),
                title, confirmLabel)) {
            return;
        }
        if (!appExit.confirmUnsavedChanges()) {
            return;
        }
        Config.setPendingWorkspaceMove(target.toString());
        Config.save();
        dispose();
        appExit.exit();
    }

    /** Asks with the buttons "{@code confirmLabel}" and "Cancel"; true for the first. */
    private boolean confirmed(String message, String title, String confirmLabel) {
        Object[] options = {confirmLabel, LanguageService.displayName("workspaceMaintenance.cancel")};
        int choice = JOptionPane.showOptionDialog(this, message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
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
        Path workspace = WorkspaceMaintenance.normalizeWorkspaceDir(Paths.get(chosen.trim()));
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
        if (heroAlgorithmComboBox.getSelectedItem() != null) {
            Config.setDefaultHeroAlgorithm((String) heroAlgorithmComboBox.getSelectedItem());
        }
        if (titanAlgorithmComboBox.getSelectedItem() != null) {
            Config.setDefaultTitanAlgorithm((String) titanAlgorithmComboBox.getSelectedItem());
        }
        Config.setBackupDir(backupDirField.getText().trim());
        Config.setWorkspacePath(workspacePath);
        Config.setStaleCheckEnabled(staleCheckBox.isSelected());
        Config.setStaleAfterDays((Integer) staleAfterDaysSpinner.getValue());
        if (staleCheckTouched) {
            Config.setStaleCheckUserSet(true);
        }
        CowScoreBonuses oldBonuses = TeamScoreCalculator.bonuses();
        if (Config.setCowScoreBonuses(cowScoreBonusesPanel.bonuses())) {
            CowScoreBonuses newBonuses = Config.getCowScoreBonuses();
            Logger.log("CowScore bonuses changed: " + oldBonuses + " -> " + newBonuses);
        }
        // Take effect right away - no restart needed.
        TeamScoreCalculator.setBonuses(Config.getCowScoreBonuses());
        Config.save();
        // So that any lookups happening right after this dialog closes
        // already see the new language, even though most of the UI (built
        // once at startup, see ActionBar/Cow2Frame) still needs
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
            restartNotices.add(LanguageService.displayName("settingsDialog.languageChanged"));
        }
        if (workspaceChanged) {
            restartNotices.add(LanguageService.displayName("settingsDialog.workspaceChanged"));
        }
        if (!restartNotices.isEmpty()) {
            restartNotices.add(LanguageService.displayName("settingsDialog.restartHint"));
            JOptionPane.showMessageDialog(owner,
                    String.join(" ", restartNotices),
                    LanguageService.displayTitle("menu.settings"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Shows an algorithm combo box entry (a stable algorithm name) by its localized name. */
    private static final class LocalizedAlgorithmRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String algorithmName) {
                setText(AlgorithmDescriptions.localizedName(algorithmName));
            }
            return this;
        }
    }

    /** Width after {@code pack()}, before widening - package-visible for tests. */
    int packedWidth() {
        return packedWidth;
    }

    /** The "..." buttons and the restore/move buttons behind them - package-visible for tests. */
    JButton browseBackupDirButton() {
        return browseBackupDirButton;
    }

    JButton browseWorkspaceDirButton() {
        return browseWorkspaceDirButton;
    }

    JButton restoreWorkspaceButton() {
        return restoreWorkspaceButton;
    }

    JButton moveWorkspaceButton() {
        return moveWorkspaceButton;
    }

    /** True if the dialog was confirmed via the save button (rather than the window close button). */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Shows the dialog modally and waits for user input.
     *
     * @param owner parent window (may be null)
     * @param appExit how a scheduled restore or move ends the application
     * @return the dialog after it was closed - check {@link #isConfirmed()} to see whether the user saved a change
     */
    public static SettingsDialog show(Frame owner, AppExit appExit) {
        SettingsDialog dialog = new SettingsDialog(owner, appExit);
        dialog.setVisible(true);
        return dialog;
    }
}
