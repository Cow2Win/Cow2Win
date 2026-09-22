package org.c2w.gui;

import org.c2w.C2WApp;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildEditorDialog;
import org.c2w.gui.hero.HeroCoreScoreDialog;
import org.c2w.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.c2w.C2WApp.BASE_TITLE;

public class Cow2Frame extends JFrame {

   /** Classpath-absolute path to the frame icon (see {@link #loadFrameIcon()}). */
    private static final String FRAME_ICON_PATH = "/images/app/cow.png";

    private static final String HERO_WARS_URL = "https://www.hero-wars.com/";

    /**
     * Language file keys and icon paths for the "Guild" menu (see
     * {@link #buildGuildMenu()}) - moved here 2026-09-19 from {@code
     * ToolbarPanel}'s {@code newGuildButton}/{@code openGuildEditorButton}/
     * {@code removeGuildButton} {@link org.c2w.gui.common.FlatButton}s,
     * together with the logic behind them ({@link #onNewGuild()}/
     * {@link #onRemoveGuild()}/{@link #onOpenGuildEditor()}), so the icons
     * shown before each menu item's text are exactly the ones those buttons
     * used to show.
     */
    private static final String KEY_NEW_GUILD = "toolbar.newGuild";
    private static final String KEY_REMOVE_GUILD = "toolbar.removeGuild";
    private static final String KEY_OPEN_GUILD_EDITOR = "teamsOverview.openGuildEditor";

    /** Language file key for the (shared) title of every {@link #onRemoveGuild()} dialog - the confirmation and the "only guild left" warning alike. */
    private static final String KEY_REMOVE_GUILD_DIALOG_TITLE = "toolbar.removeGuild.dialogTitle";

    /** Language file key for {@link #onRemoveGuild()}'s confirmation question - contains a literal {@code "{0}"} placeholder for the guild folder name, replaced in {@link #onRemoveGuild()} itself. */
    private static final String KEY_REMOVE_GUILD_CONFIRM_MESSAGE = "toolbar.removeGuild.confirmMessage";

    /** Language file key for the message shown instead of the confirmation when the selected guild is the only one left (see {@link #onRemoveGuild()}). */
    private static final String KEY_REMOVE_GUILD_LAST_MESSAGE = "toolbar.removeGuild.lastMessage";

    /** Language file key for the title of the error dialog shown when {@link GuildRepository#delete} fails in {@link #onRemoveGuild()}. */
    private static final String KEY_REMOVE_GUILD_ERROR_TITLE = "toolbar.removeGuild.errorTitle";

    /** Language file key for the message prefix (followed by the exception's own message) of that same error dialog. */
    private static final String KEY_REMOVE_GUILD_ERROR = "toolbar.removeGuild.error";

    private static final String ICON_HERO_WARS = "/images/app/herowars32.png";
    private static final String ICON_NEW_GUILD = "/images/app/guild-new.png";
    private static final String ICON_REMOVE_GUILD = "/images/app/guild-remove.png";
    private static final String ICON_OPEN_GUILD_EDITOR = "/images/app/guild.png";

    private static final String KEY_NEW_LINEUP = "toolbar.newLineup";
    private static final String KEY_REMOVE_LINEUP = "toolbar.removeLineup";
    private static final String KEY_CLEAR_LINEUP = "toolbar.clearLineup";

    private static final String ICON_NEW_LINEUP = "/images/app/lineup-new.png";
    private static final String ICON_REMOVE_LINEUP = "/images/app/lineup-remove.png";
    private static final String ICON_CLEAR_LINEUP = "/images/app/lineup-clean.png";

    private final AppContext appContext;
    /** Background image painted by {@link #getContentPane()} (a {@link BackgroundPanel}) - loaded once in the constructor, see {@link IconLoader#getBackgroundImage()}. */
    private final Image background;
    private final FortificationMapPanel fortificationMapPanel;
    private final ToolbarPanel toolbarPanel;
    private final LogPanel logPanel;
    private JDialog logDialog;

    public Cow2Frame(AppContext appContext) {
        super(BASE_TITLE);
        // Was EXIT_ON_CLOSE until 2026-09-04: that close operation exits the
        // JVM unconditionally once the window-closing event has been
        // dispatched to any listeners, regardless of what they do - so a
        // listener has no way to warn about unsaved changes and let the user
        // back out. DO_NOTHING_ON_CLOSE instead leaves closing entirely up
        // to the WindowAdapter below (see #onWindowClosing), which performs
        // the exit itself once it is safe to do so.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        GuiUtils.setGUIConstants();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });
        this.appContext = appContext;
        this.background = IconLoader.getBackgroundImage();
        updateTitle();
        loadFrameIcon().ifPresent(icon -> setIconImage(icon.getImage()));
        setJMenuBar(buildMenuBar());

        this.fortificationMapPanel = new FortificationMapPanel(appContext);
        this.toolbarPanel = new ToolbarPanel(appContext, fortificationMapPanel, this::onGuildSwitched);
        this.logPanel = new LogPanel();

        JScrollPane fortificationScrollPane = new JScrollPane(fortificationMapPanel);
        fortificationScrollPane.setOpaque(false);
        fortificationScrollPane.getViewport().setOpaque(false);

        setContentPane(new BackgroundPanel(new BorderLayout(), background));
        getContentPane().add(toolbarPanel, BorderLayout.NORTH);
        getContentPane().add(fortificationScrollPane, BorderLayout.CENTER);

        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setVisible(true);

        checkForUpdatesAtStartup();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu(LanguageService.displayName("menu.file"));

        JMenuItem checkForUpdatesItem = new JMenuItem(LanguageService.displayName("menu.checkForUpdates"));
        checkForUpdatesItem.addActionListener(e -> onCheckForUpdates());
        fileMenu.add(checkForUpdatesItem);
        menuBar.add(fileMenu);

        JMenuItem settingsItem = new JMenuItem(LanguageService.displayName("menu.settings"));
        settingsItem.addActionListener(e -> onOpenSettings());
        fileMenu.add(settingsItem);

        JMenuItem heroBuffFitScoresItem = new JMenuItem("CowScore");
        heroBuffFitScoresItem.addActionListener(e -> onOpenHeroBuffFitScores());
        fileMenu.add(heroBuffFitScoresItem);

        JMenuItem showLogItem = new JMenuItem(LanguageService.displayName("menu.showLog"));
        showLogItem.addActionListener(e -> onShowLog());
        fileMenu.add(showLogItem);

        JMenuItem hwWebItem = new JMenuItem(HERO_WARS_URL);
        hwWebItem.setIcon(IconLoader.iconFor(ICON_HERO_WARS, ToolbarPanel.TOOLBAR_ICON_SIZE));
        hwWebItem.addActionListener(e -> onOpenWeb(HERO_WARS_URL));
        fileMenu.add(hwWebItem);

        menuBar.add(buildGuildMenu());
        menuBar.add(buildLineupMenu());



        return menuBar;
    }

    /**
     * Builds the "Guild" menu - the three menu items here replace {@code
     * ToolbarPanel}'s {@code newGuildButton}/{@code openGuildEditorButton}/
     * {@code removeGuildButton} {@link org.c2w.gui.common.FlatButton}s (moved
     * here 2026-09-19, together with {@link #onNewGuild()}/
     * {@link #onRemoveGuild()}/{@link #onOpenGuildEditor()}), each menu item
     * showing the same icon the corresponding button used to show, via
     * {@link JMenuItem#setIcon} (before the item's text, like every Swing
     * menu item icon).
     */
    private JMenu buildGuildMenu() {
        JMenu guildMenu = new JMenu("Guild");

        JMenuItem newGuildItem = new JMenuItem(LanguageService.displayName(KEY_NEW_GUILD));
        newGuildItem.setIcon(IconLoader.iconFor(ICON_NEW_GUILD, ToolbarPanel.TOOLBAR_ICON_SIZE, IconLoader.GREEN));
        newGuildItem.addActionListener(e -> onNewGuild());
        guildMenu.add(newGuildItem);

        JMenuItem openGuildEditorItem = new JMenuItem(LanguageService.displayName(KEY_OPEN_GUILD_EDITOR));
        openGuildEditorItem.setIcon(IconLoader.iconForButton(ICON_OPEN_GUILD_EDITOR));
        openGuildEditorItem.addActionListener(e -> onOpenGuildEditor());
        guildMenu.add(openGuildEditorItem);

        JMenuItem removeGuildItem = new JMenuItem(LanguageService.displayName(KEY_REMOVE_GUILD));
        removeGuildItem.setIcon(IconLoader.iconFor(ICON_REMOVE_GUILD, ToolbarPanel.TOOLBAR_ICON_SIZE, IconLoader.RED));
        removeGuildItem.addActionListener(e -> onRemoveGuild());
        guildMenu.add(removeGuildItem);

        return guildMenu;
    }

    /**
     * Creates a new guild folder and switches to it - moved here 2026-09-19
     * from {@code ToolbarPanel} together with {@link #onRemoveGuild()} (see
     * {@link #buildGuildMenu()}). {@link #toolbarPanel} still owns {@code
     * guildCombo} and the guild-switching mechanics, so this delegates to
     * its (package-visible) {@link ToolbarPanel#workspaceDir()}/
     * {@link ToolbarPanel#confirmDiscardUnsavedChanges()}/
     * {@link ToolbarPanel#switchToGuild} instead of duplicating them.
     */
    private void onNewGuild() {
        String input = JOptionPane.showInputDialog(this, "Name of the new guild:", "New guild",
                JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;
        }
        String name = input.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name.", "New guild", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (ToolbarPanel.containsIllegalFilenameChar(name)) {
            JOptionPane.showMessageDialog(this,
                    "The name must not contain any of these characters: " + ToolbarPanel.ILLEGAL_FILENAME_CHARS,
                    "New guild", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path guildDir = toolbarPanel.workspaceDir().resolve(name);
        if (Files.exists(guildDir)) {
            JOptionPane.showMessageDialog(this, "A guild folder named \"" + name + "\" already exists.",
                    "New guild", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!toolbarPanel.confirmDiscardUnsavedChanges()) {
            return;
        }

        Path guildFilePath = C2WApp.createInitialGuildFile(name, guildDir);
        C2WApp.createInitialLineupFile(name, guildDir);
        if (toolbarPanel.switchToGuild(guildDir, guildFilePath)) {
            Logger.log("Created guild: " + guildFilePath);
        }
    }

    /**
     * Deletes the currently selected guild folder from disk (see
     * {@link GuildRepository#delete}) and switches to whichever guild takes
     * its place - moved here 2026-09-19 from {@code ToolbarPanel} together
     * with {@link #onNewGuild()}, see that method's Javadoc and
     * {@link #buildGuildMenu()}. Mirrors {@code ToolbarPanel#onRemoveLineup()}:
     * a confirmation dialog and a guard against removing the last guild left
     * in the workspace.
     */
    private void onRemoveGuild() {
        String folderName = toolbarPanel.selectedGuildFolderName();
        if (folderName == null) {
            return;
        }
        List<String> folderNames = toolbarPanel.listGuildFolderNames();
        int index = folderNames.indexOf(folderName);
        if (folderNames.size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName(KEY_REMOVE_GUILD_LAST_MESSAGE),
                    LanguageService.displayName(KEY_REMOVE_GUILD_DIALOG_TITLE), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String confirmMessage = LanguageService.displayName(KEY_REMOVE_GUILD_CONFIRM_MESSAGE)
                .replace("{0}", folderName);
        int confirm = JOptionPane.showConfirmDialog(this, confirmMessage,
                LanguageService.displayName(KEY_REMOVE_GUILD_DIALOG_TITLE), JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Path guildDir = toolbarPanel.workspaceDir().resolve(folderName);
        try {
            GuildRepository.delete(guildDir);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName(KEY_REMOVE_GUILD_ERROR) + "\n" + e.getMessage(),
                    LanguageService.displayName(KEY_REMOVE_GUILD_ERROR_TITLE), JOptionPane.ERROR_MESSAGE);
            return;
        }
        Logger.log("Removed guild: " + guildDir);

        List<String> remaining = toolbarPanel.listGuildFolderNames();
        int nextIndex = Math.min(index, remaining.size() - 1);
        String nextFolderName = remaining.get(nextIndex);
        Path nextGuildDir = toolbarPanel.workspaceDir().resolve(nextFolderName);
        Path nextGuildFilePath = nextGuildDir.resolve(C2WApp.GUILD_FILE_NAME);
        if (toolbarPanel.switchToGuild(nextGuildDir, nextGuildFilePath)) {
            Logger.log("Switched to guild: " + nextGuildFilePath);
        }
    }

    /**
     * Builds the "Lineup" menu - the three menu items here replace {@code
     * ToolbarPanel}'s {@code newLineupButton}/{@code removeLineupButton}/
     * {@code clearLineupButton} {@link org.c2w.gui.common.FlatButton}s
     * (moved here 2026-09-19, together with {@link #onNewLineup()}/
     * {@link #onRemoveLineup()}/{@link #onClearLineup()}), each menu item
     * showing the same icon the corresponding button used to show - see
     * {@link #buildGuildMenu()}.
     */
    private JMenu buildLineupMenu() {
        JMenu lineupMenu = new JMenu("Lineup");

        JMenuItem newLineupItem = new JMenuItem(LanguageService.displayName(KEY_NEW_LINEUP));
        newLineupItem.setIcon(IconLoader.iconFor(ICON_NEW_LINEUP, ToolbarPanel.TOOLBAR_ICON_SIZE, IconLoader.GREEN));
        newLineupItem.addActionListener(e -> onNewLineup());
        lineupMenu.add(newLineupItem);

        JMenuItem removeLineupItem = new JMenuItem(LanguageService.displayName(KEY_REMOVE_LINEUP));
        removeLineupItem.setIcon(IconLoader.iconFor(ICON_REMOVE_LINEUP, ToolbarPanel.TOOLBAR_ICON_SIZE, IconLoader.RED));
        removeLineupItem.addActionListener(e -> onRemoveLineup());
        lineupMenu.add(removeLineupItem);

        JMenuItem clearLineupItem = new JMenuItem(LanguageService.displayName(KEY_CLEAR_LINEUP));
        clearLineupItem.setIcon(IconLoader.iconForButton(ICON_CLEAR_LINEUP));
        clearLineupItem.addActionListener(e -> onClearLineup());
        lineupMenu.add(clearLineupItem);

        return lineupMenu;
    }

    /**
     * Creates a new ".lineup" file and switches to it - moved here
     * 2026-09-19 from {@code ToolbarPanel} together with
     * {@link #onRemoveLineup()}/{@link #onClearLineup()} (see
     * {@link #buildLineupMenu()}). {@link #toolbarPanel} still owns {@code
     * lineupCombo}, so this delegates to its (package-visible)
     * {@link ToolbarPanel#populateLineupCombo()} to refresh it, instead of
     * duplicating that mechanic.
     */
    private void onNewLineup() {
        // Pre-filled with today's date rather than left empty, still a plain,
        // freely editable text field though (selectionValues == null, see
        // JOptionPane's 7-arg showInputDialog javadoc: a null
        // selectionValues array with a non-null initialSelectionValue
        // renders as a JTextField seeded with that value) - unchanged from
        // ToolbarPanel#onNewLineup()'s original behavior.
        Object result = JOptionPane.showInputDialog(this, "Name of the new lineup:", "New lineup",
                JOptionPane.PLAIN_MESSAGE, null, null, LocalDate.now().toString());
        if (result == null) {
            return;
        }
        String input = result.toString();
        String name = input.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name.", "New lineup", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (ToolbarPanel.containsIllegalFilenameChar(name)) {
            JOptionPane.showMessageDialog(this,
                    "The name must not contain any of these characters: " + ToolbarPanel.ILLEGAL_FILENAME_CHARS,
                    "New lineup", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = name.endsWith(ToolbarPanel.LINEUP_FILE_SUFFIX) ? name : name + ToolbarPanel.LINEUP_FILE_SUFFIX;
        Path guildDir = appContext.guildFilePath().getParent();
        Path lineupPath = guildDir.resolve(fileName);
        if (Files.exists(lineupPath)) {
            JOptionPane.showMessageDialog(this, "A lineup file named \"" + fileName + "\" already exists.",
                    "New lineup", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Guild currentGuild = appContext.guild();
        Lineup lineup = new Lineup(currentGuild.id(), currentGuild.name(), "", LocalDateTime.now(), List.of());
        try {
            LineupRepository.save(lineup, lineupPath);
            appContext.set(lineup, lineupPath);
            Config.setLastLineUpPath(lineupPath.toString());
            Config.save();
            GuiUtils.editedLineup = false;
            fortificationMapPanel.refresh(lineup);
            toolbarPanel.populateLineupCombo();
            Logger.log("Created: " + lineupPath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not create lineup:\n" + e.getMessage(),
                    "Error while creating lineup", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Deletes the currently selected ".lineup" file from disk (see
     * {@link LineupRepository#delete}) and loads whichever file takes its
     * place - moved here 2026-09-19 from {@code ToolbarPanel} together with
     * {@link #onNewLineup()}, see that method's Javadoc and
     * {@link #buildLineupMenu()}. A confirmation dialog and a guard against
     * removing the last lineup left in the guild folder, mirroring
     * {@link #onRemoveGuild()}.
     */
    private void onRemoveLineup() {
        String fileName = toolbarPanel.selectedLineupFileName();
        if (fileName == null) {
            return;
        }
        List<String> fileNames = toolbarPanel.listLineupFileNames();
        int index = fileNames.indexOf(fileName);
        if (fileNames.size() <= 1) {
            JOptionPane.showMessageDialog(this, "This is the only lineup left and cannot be removed.",
                    "Remove lineup", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Really remove lineup \"" + ToolbarPanel.stripLineupSuffix(fileName) + "\"? This also deletes the file.",
                "Remove lineup", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Path guildDir = appContext.guildFilePath().getParent();
        Path lineupPath = guildDir.resolve(fileName);
        try {
            LineupRepository.delete(lineupPath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not delete lineup:\n" + e.getMessage(),
                    "Error while removing lineup", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> remaining = toolbarPanel.listLineupFileNames();
        int nextIndex = Math.min(index, remaining.size() - 1);
        String nextFileName = remaining.get(nextIndex);
        Path nextLineupPath = guildDir.resolve(nextFileName);
        try {
            Lineup lineup = LineupRepository.load(nextLineupPath);
            appContext.set(lineup, nextLineupPath);
            Config.setLastLineUpPath(nextLineupPath.toString());
            Config.save();
            GuiUtils.editedLineup = false;
            fortificationMapPanel.refresh(lineup);
            toolbarPanel.populateLineupCombo();
            Logger.log("Removed: " + fileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Lineup deleted, but could not load \"" + nextFileName + "\":\n" + e.getMessage(),
                    "Error while loading lineup", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Clears every team assignment from the currently open lineup (in
     * memory only, not saved to disk until the lineup is saved) - moved
     * here 2026-09-19 from {@code ToolbarPanel}, see
     * {@link #buildLineupMenu()}. Self-contained (needs neither {@code
     * lineupCombo} nor any other {@link #toolbarPanel} state), unlike
     * {@link #onNewLineup()}/{@link #onRemoveLineup()}.
     */
    private void onClearLineup() {
        Lineup currentLineup = appContext.lineup();
        if (currentLineup.entries().isEmpty()) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Really clear the current lineup? This removes all " + currentLineup.entries().size()
                        + " team assignment(s) (not saved to disk until you save the lineup).",
                "Clear lineup", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Lineup clearedLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                currentLineup.algorithmName(), currentLineup.createdAt(), List.of());
        appContext.setLineup(clearedLineup);
        GuiUtils.editedLineup = true;
        fortificationMapPanel.refresh(clearedLineup);
        Logger.log("Cleared: " + appContext.lineupFilePath());
    }

    /** Opens {@link SettingsDialog} (currently: choosing the display language). */
    private void onOpenSettings() {
        SettingsDialog.show(this);
    }

    /**
     * Opens {@link HeroCoreScoreDialog} - independent of the currently
     * open guild/lineup (see that dialog's class Javadoc), so this only
     * needs the frame itself as owner.
     */
    private void onOpenHeroBuffFitScores() {
        new HeroCoreScoreDialog(this).setVisible(true);
    }

    /**
     * Silent, best-effort update check run once after the frame becomes
     * visible (Cow2Win todos 1.1) - only ever surfaces a dialog when a
     * newer release was actually found ({@link #handleUpdateCheckResult}),
     * so a missing internet connection or an unreachable GitHub never
     * bothers the user on every single startup. Every outcome is still
     * logged (see {@link Logger}), so "did it even check" is visible in the
     * log panel/file if needed.
     */
    private void checkForUpdatesAtStartup() {
        UpdateChecker.checkAsync(result -> handleUpdateCheckResult(result, false));
    }

    /** "File" > "Check for Updates" menu item - unlike {@link #checkForUpdatesAtStartup()}, always reports back, including "already up to date" and a failed check. */
    private void onCheckForUpdates() {
        UpdateChecker.checkAsync(result -> handleUpdateCheckResult(result, true));
    }

    /**
     * Reports one {@link UpdateChecker.UpdateCheckResult} - always logs it,
     * and shows a dialog for {@link UpdateChecker.UpdateCheckResult.Status#UPDATE_AVAILABLE}
     * (offering to open the release page via {@link #onOpenWeb}) always, or
     * for the other two outcomes only when {@code alwaysShowDialog} is true
     * (i.e. only for the explicit, user-triggered check - see
     * {@link #onCheckForUpdates()} vs. {@link #checkForUpdatesAtStartup()}).
     */
    private void handleUpdateCheckResult(UpdateChecker.UpdateCheckResult result, boolean alwaysShowDialog) {
        switch (result.status()) {
            case UPDATE_AVAILABLE -> {
                Logger.log("Update available: " + result.latestVersion() + " (installed: " + result.currentVersion() + ")");
                int choice = JOptionPane.showConfirmDialog(this,
                        "A newer version of Cow2Win is available: " + result.latestVersion()
                                + " (you have " + result.currentVersion() + ").\n\nOpen the release page?",
                        "Update available", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    onOpenWeb(result.releaseUrl());
                }
            }
            case UP_TO_DATE -> {
                Logger.log("Update check: already up to date (" + result.currentVersion() + ")");
                if (alwaysShowDialog) {
                    JOptionPane.showMessageDialog(this,
                            "Cow2Win is up to date (version " + result.currentVersion() + ").",
                            "Check for Updates", JOptionPane.INFORMATION_MESSAGE);
                }
            }
            case CHECK_FAILED -> {
                Logger.log("Update check failed or could not be evaluated (installed: " + result.currentVersion() + ")");
                if (alwaysShowDialog) {
                    JOptionPane.showMessageDialog(this,
                            "Could not check for updates. Please check your internet connection and try again later.",
                            "Check for Updates", JOptionPane.WARNING_MESSAGE);
                }
            }
        }
    }

    /**
     * Shows {@link #logPanel} in a small, non-modal, lazily-created dialog
     * (created once, then just re-shown/raised on subsequent calls - see
     * {@link #logDialog}) instead of it being permanently docked in the main
     * window. HIDE_ON_CLOSE (not the default DISPOSE_ON_CLOSE) so closing the
     * dialog only hides it - logPanel itself, and its Logger listener
     * registration, are unaffected either way, but this also avoids
     * recreating the native dialog peer on every open.
     */
    private void onShowLog() {
        if (logDialog == null) {
            logDialog = new JDialog(this, "Log", false);
            logDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            logDialog.getContentPane().add(logPanel);
            logDialog.setSize(700, 400);
            logDialog.setLocationRelativeTo(this);
        }
        logDialog.setVisible(true);
        logDialog.toFront();
    }

    private void onOpenGuildEditor() {
        GuildEditorDialog dialog = new GuildEditorDialog(this, appContext, this::onGuildSaved);
        dialog.setVisible(true);
    }

    /** Called after {@link GuildEditorDialog} saves a change to the current guild. */
    private void onGuildSaved() {
        updateTitle();
    }

    private void onGuildSwitched() {
        updateTitle();
    }


    private void onWindowClosing() {
        if (GuiUtils.editedGuild || GuiUtils.editedLineup) {
            String what = GuiUtils.editedGuild && GuiUtils.editedLineup ? "guild and lineup"
                    : GuiUtils.editedGuild ? "guild" : "lineup";
            int choice = JOptionPane.showConfirmDialog(this,
                    "There are unsaved " + what + " changes. Close anyway?",
                    "Unsaved changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        System.exit(0);
    }

    private void updateTitle() {
        var guild = appContext.guild();
        String displayName = guild.name().isBlank() ? guild.id() : guild.name();
        setTitle(BASE_TITLE + " " + AppVersion.current() + " - " + displayName);
    }


    private static Optional<ImageIcon> loadFrameIcon() {
        URL resource = Cow2Frame.class.getResource(FRAME_ICON_PATH);
        if (resource == null) {
            Logger.log("Frame icon not found on classpath: " + FRAME_ICON_PATH);
            return Optional.empty();
        }
        return Optional.of(new ImageIcon(resource));
    }

    /**
     * The frame's content pane (installed in the constructor via
     * {@code setContentPane}): paints {@link #background} scaled to its own
     * current size, once, before any child is painted. Moved up here from
     * FortificationMapPanel on 2026-09-17 so the same background shows behind
     * the whole window instead of just behind the fortification map - every
     * panel/scroll pane in between (see the constructor and
     * FortificationMapPanel) is kept non-opaque so it is actually visible
     * through them, the same way FortificationMapPanel used to paint
     * directly over its own opaque black background.
     */
    private static final class BackgroundPanel extends JPanel {

        private final Image background;

        BackgroundPanel(LayoutManager layout, Image background) {
            super(layout);
            this.background = background;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (background != null) {
                g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
            }
        }
    }

    private void onOpenWeb(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(this, "This system has no default browser Cow2 can open.",
                    "Could not open Hero Wars", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            JOptionPane.showMessageDialog(this, "Could not open " + url + ":\n" + e.getMessage(),
                    "Could not open Hero Wars", JOptionPane.ERROR_MESSAGE);
        }
    }



}

