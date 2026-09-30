package org.c2w.gui;

import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.flag.WarFlagCoreScoreDialog;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildEditorDialog;
import org.c2w.gui.hero.HeroCoreScoreDialog;
import org.c2w.gui.pet.PetCoreScoreDialog;
import org.c2w.gui.titan.TitanCoreScoreDialog;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;
import org.c2w.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
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

    /** Language file key for the title of the error dialog shown when {@link GuildService#deleteGuild} fails in {@link #onRemoveGuild()}. */
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
    private final GuildService guildService;
    private final LineupService lineupService;
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
        this.guildService = new GuildService(appContext);
        this.lineupService = new LineupService(appContext);
        this.background = IconLoader.getBackgroundImage();
        updateTitle();
        loadFrameIcon().ifPresent(icon -> setIconImage(icon.getImage()));
        setJMenuBar(buildMenuBar());
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                updateTitle();
            }
        });

        this.fortificationMapPanel = new FortificationMapPanel(appContext);
        this.toolbarPanel = new ToolbarPanel(appContext, fortificationMapPanel);
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

        JMenuItem heroBuffFitScoresItem = new JMenuItem(LanguageService.displayName("menu.cowScore"));
        heroBuffFitScoresItem.addActionListener(e -> onOpenHeroBuffFitScores());
        fileMenu.add(heroBuffFitScoresItem);

        JMenuItem titanBuffFitScoresItem = new JMenuItem(LanguageService.displayName("menu.titanCowScore"));
        titanBuffFitScoresItem.addActionListener(e -> onOpenTitanBuffFitScores());
        fileMenu.add(titanBuffFitScoresItem);

        JMenuItem petBuffFitScoresItem = new JMenuItem(LanguageService.displayName("menu.petCowScore"));
        petBuffFitScoresItem.addActionListener(e -> onOpenPetBuffFitScores());
        fileMenu.add(petBuffFitScoresItem);

        JMenuItem warFlagBuffFitScoresItem = new JMenuItem(LanguageService.displayName("menu.warFlagCowScore"));
        warFlagBuffFitScoresItem.addActionListener(e -> onOpenWarFlagBuffFitScores());
        fileMenu.add(warFlagBuffFitScoresItem);

        JMenuItem showLogItem = new JMenuItem(LanguageService.displayName("menu.showLog"));
        showLogItem.addActionListener(e -> onShowLog());
        fileMenu.add(showLogItem);

        JMenuItem hwWebItem = new JMenuItem(LanguageService.displayName("toolbar.heroWars"));
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
        JMenu guildMenu = new JMenu(LanguageService.displayName("menu.guild"));

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
     * Creates a new guild folder and switches to it (see {@link
     * GuildService#createGuild}). {@link #toolbarPanel} owns the
     * "unsaved changes" prompt and the error handling of a guild switch, so
     * this reuses its {@link ToolbarPanel#confirmDiscardUnsavedChanges()}/
     * {@link ToolbarPanel#switchToGuild} instead of duplicating them.
     */
    private void onNewGuild() {
        String input = JOptionPane.showInputDialog(this, LanguageService.displayName("mainFrame.newGuild.prompt"),
                LanguageService.displayName("mainFrame.newGuild.title"),
                JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;
        }
        String name = input.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.enterName"),
                    LanguageService.displayName("mainFrame.newGuild.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (ToolbarPanel.containsIllegalFilenameChar(name)) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("common.illegalFilenameChars", ToolbarPanel.ILLEGAL_FILENAME_CHARS),
                    LanguageService.displayName("mainFrame.newGuild.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (guildService.guildExists(name)) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.newGuild.alreadyExists", name),
                    LanguageService.displayName("mainFrame.newGuild.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!toolbarPanel.confirmDiscardUnsavedChanges()) {
            return;
        }

        guildService.createGuild(name);
        toolbarPanel.switchToGuild(name);
    }

    /**
     * Deletes the currently open guild folder from disk (see
     * {@link GuildService#deleteGuild}) and switches to whichever guild takes
     * its place. Mirrors {@link #onRemoveLineup()}: a confirmation dialog and
     * a guard against removing the last guild left in the workspace.
     */
    private void onRemoveGuild() {
        String folderName = guildService.currentGuildFolderName();
        if (folderName == null) {
            return;
        }
        List<String> folderNames = guildService.listGuildFolderNames();
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

        try {
            guildService.deleteGuild(folderName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName(KEY_REMOVE_GUILD_ERROR) + "\n" + e.getMessage(),
                    LanguageService.displayName(KEY_REMOVE_GUILD_ERROR_TITLE), JOptionPane.ERROR_MESSAGE);
            return;
        }

        String nextFolderName = guildService.guildAfterRemoval(index);
        if (nextFolderName != null) {
            toolbarPanel.switchToGuild(nextFolderName);
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
        JMenu lineupMenu = new JMenu(LanguageService.displayName("menu.lineup"));

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

    /** Creates a new ".lineup" file in the current guild folder and opens it - see {@link LineupService#createLineup}. */
    private void onNewLineup() {
        // Pre-filled with today's date rather than left empty, still a plain,
        // freely editable text field though (selectionValues == null, see
        // JOptionPane's 7-arg showInputDialog javadoc: a null
        // selectionValues array with a non-null initialSelectionValue
        // renders as a JTextField seeded with that value).
        Object result = JOptionPane.showInputDialog(this, LanguageService.displayName("mainFrame.newLineup.prompt"),
                LanguageService.displayName("mainFrame.newLineup.title"),
                JOptionPane.PLAIN_MESSAGE, null, null, LocalDate.now().toString());
        if (result == null) {
            return;
        }
        String input = result.toString();
        String name = input.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.enterName"),
                    LanguageService.displayName("mainFrame.newLineup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (ToolbarPanel.containsIllegalFilenameChar(name)) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("common.illegalFilenameChars", ToolbarPanel.ILLEGAL_FILENAME_CHARS),
                    LanguageService.displayName("mainFrame.newLineup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = LineupService.toLineupFileName(name);
        if (LineupFiles.isOriginalFileName(fileName)) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("mainFrame.newLineup.reservedName"),
                    LanguageService.displayName("mainFrame.newLineup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lineupService.lineupExists(fileName)) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.newLineup.alreadyExists", fileName),
                    LanguageService.displayName("mainFrame.newLineup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            lineupService.createLineup(fileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.newLineup.createError") + "\n" + e.getMessage(),
                    LanguageService.displayName("mainFrame.newLineup.createErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Deletes the currently open ".lineup" file from disk (see
     * {@link LineupService#deleteLineup}) and opens whichever file takes its
     * place. A confirmation dialog and a guard against removing the last
     * lineup left in the guild folder, mirroring {@link #onRemoveGuild()}.
     */
    private void onRemoveLineup() {
        String fileName = appContext.lineupFilePath().getFileName().toString();
        if (LineupFiles.isOriginalFileName(fileName)) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("mainFrame.removeLineup.originalMessage"),
                    LanguageService.displayName(KEY_REMOVE_LINEUP), JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> fileNames = lineupService.listLineupFileNames();
        int index = fileNames.indexOf(fileName);
        if (fileNames.size() <= 1) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.removeLineup.lastMessage"),
                    LanguageService.displayName(KEY_REMOVE_LINEUP), JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName("mainFrame.removeLineup.confirmMessage", ToolbarPanel.stripLineupSuffix(fileName)),
                LanguageService.displayName(KEY_REMOVE_LINEUP), JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            lineupService.deleteLineup(fileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.removeLineup.deleteError") + "\n" + e.getMessage(),
                    LanguageService.displayName("mainFrame.removeLineup.deleteErrorTitle"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        String nextFileName = lineupService.lineupAfterRemoval(index);
        if (nextFileName == null) {
            return;
        }
        try {
            lineupService.selectLineup(nextFileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("mainFrame.removeLineup.loadNextError", nextFileName) + "\n" + e.getMessage(),
                    LanguageService.displayName("common.loadLineupErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Clears every team assignment from the currently open lineup (in
     * memory only, not saved to disk until the lineup is saved) - see
     * {@link LineupService#clearLineup()}.
     */
    private void onClearLineup() {
        if (lineupService.isOriginalOpen()) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("common.originalReadOnly"),
                    LanguageService.displayName(KEY_CLEAR_LINEUP), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int entryCount = appContext.lineup().entries().size();
        if (entryCount == 0) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName("mainFrame.clearLineup.confirmMessage", entryCount),
                LanguageService.displayName(KEY_CLEAR_LINEUP), JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        lineupService.clearLineup();
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

    /** Opens {@link TitanCoreScoreDialog} - the titan counterpart of {@link #onOpenHeroBuffFitScores()}, likewise independent of the open guild/lineup. */
    private void onOpenTitanBuffFitScores() {
        new TitanCoreScoreDialog(this).setVisible(true);
    }

    /** Opens {@link PetCoreScoreDialog} - the pet counterpart of {@link #onOpenHeroBuffFitScores()}, likewise independent of the open guild/lineup. */
    private void onOpenPetBuffFitScores() {
        new PetCoreScoreDialog(this).setVisible(true);
    }

    /** Opens {@link WarFlagCoreScoreDialog} - the war flag counterpart of {@link #onOpenHeroBuffFitScores()}, likewise independent of the open guild/lineup. */
    private void onOpenWarFlagBuffFitScores() {
        new WarFlagCoreScoreDialog(this).setVisible(true);
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
                        LanguageService.displayName("mainFrame.update.availableMessage", result.latestVersion(), result.currentVersion()),
                        LanguageService.displayName("mainFrame.update.availableTitle"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    onOpenWeb(result.releaseUrl());
                }
            }
            case UP_TO_DATE -> {
                Logger.log("Update check: already up to date (" + result.currentVersion() + ")");
                if (alwaysShowDialog) {
                    JOptionPane.showMessageDialog(this,
                            LanguageService.displayName("mainFrame.update.upToDateMessage", result.currentVersion()),
                            LanguageService.displayName("menu.checkForUpdates"), JOptionPane.INFORMATION_MESSAGE);
                }
            }
            case CHECK_FAILED -> {
                Logger.log("Update check failed or could not be evaluated (installed: " + result.currentVersion() + ")");
                if (alwaysShowDialog) {
                    JOptionPane.showMessageDialog(this,
                            LanguageService.displayName("mainFrame.update.failedMessage"),
                            LanguageService.displayName("menu.checkForUpdates"), JOptionPane.WARNING_MESSAGE);
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
            logDialog = new JDialog(this, LanguageService.displayTitle("mainFrame.logTitle"), false);
            logDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            logDialog.getContentPane().add(logPanel);
            logDialog.setSize(700, 400);
            logDialog.setLocationRelativeTo(this);
        }
        logDialog.setVisible(true);
        logDialog.toFront();
    }

    private void onOpenGuildEditor() {
        GuildEditorDialog dialog = new GuildEditorDialog(this, appContext);
        dialog.setVisible(true);
    }


    private void onWindowClosing() {
        if (appContext.hasUnsavedChanges()) {
            String messageKey = appContext.isGuildDirty() && appContext.isLineupDirty() ? "mainFrame.unsaved.closeGuildAndLineup"
                    : appContext.isGuildDirty() ? "mainFrame.unsaved.closeGuild" : "mainFrame.unsaved.closeLineup";
            int choice = JOptionPane.showConfirmDialog(this,
                    LanguageService.displayName(messageKey),
                    LanguageService.displayName("common.unsavedChangesTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.web.noBrowser"),
                    LanguageService.displayName("mainFrame.web.errorTitle"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.web.openError", url) + "\n" + e.getMessage(),
                    LanguageService.displayName("mainFrame.web.errorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }



}

