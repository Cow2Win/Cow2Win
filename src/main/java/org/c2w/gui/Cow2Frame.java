package org.c2w.gui;

import org.c2w.data.repository.LineupFiles;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.cowscore.CowScoreDialog;
import org.c2w.gui.cowscore.CowScoreTab;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildEditorDialog;
import org.c2w.gui.journal.JournalActions;
import org.c2w.gui.stage.ConceptStageView;
import org.c2w.gui.stage.InputStageView;
import org.c2w.gui.stage.StageView;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.AppVersion;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.infra.UpdateChecker;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.c2w.C2WApp.BASE_TITLE;

public class Cow2Frame extends JFrame {

   /** Classpath-absolute path to the frame icon (see {@link #loadFrameIcon()}). */
    private static final String FRAME_ICON_PATH = "/images/app/cow.png";

    private static final String HERO_WARS_URL = "https://www.hero-wars.com/";

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

    /** Icon paths of the menu entries (see {@link #registerActions}). */
    private static final String ICON_HERO_WARS = "/images/app/herowars32.png";
    private static final String ICON_NEW_GUILD = "/images/app/guild-new.png";
    private static final String ICON_REMOVE_GUILD = "/images/app/guild-remove.png";
    private static final String ICON_OPEN_GUILD_EDITOR = "/images/app/guild.png";

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
    /** The stage views in the center (a {@link CardLayout}, one card per stage name) - see {@link #showStage}. */
    private final JPanel stageViews;
    /** The view of each stage that has one in {@link #stageViews}. */
    private final Map<Stage, StageView> viewsByStage = new EnumMap<>(Stage.class);
    /** The process bar (one tile per process stage) and the handlers of the stage actions - see {@link ActionBar}. */
    private final ActionBar actionBar;
    /** Guild, fortification type and lineup selection - see {@link ContextBar}. */
    private final ContextBar contextBar;
    private final LogPanel logPanel;
    private JDialog logDialog;
    /** The "Weltenschlacht Journal" actions and windows. */
    private final JournalActions journalActions;
    /** Every action of menu bar and toolbar - see the constructor. */
    private final MainActions actions;

    public Cow2Frame(AppContext appContext) {
        super(BASE_TITLE);
        // Not EXIT_ON_CLOSE: that close operation exits the
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
        // Listener notifications always on the Swing thread - also for changes made in background tasks
        // (e.g. the journal import in a SwingWorker).
        appContext.setEventDispatcher(GuiUtils::runOnEdtAndWait);
        // The fortification type selected last is active again, and every change is remembered.
        appContext.setFortificationType(Config.getLastFortificationType());
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void fortificationTypeChanged() {
                Config.setLastFortificationType(appContext.fortificationType());
                Config.save();
            }
        });
        this.guildService = new GuildService(appContext);
        this.lineupService = new LineupService(appContext);
        this.background = IconLoader.getBackgroundImage();
        updateTitle();
        loadFrameIcon().ifPresent(icon -> setIconImage(icon.getImage()));
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                updateTitle();
            }

            @Override
            public void dirtyStateChanged() {
                updateTitle();
            }
        });

        this.fortificationMapPanel = new FortificationMapPanel(appContext);
        this.logPanel = new LogPanel();

        // First every action (with its handler in its owner), then menu bar, context bar and action bar.
        this.actions = new MainActions();
        registerActions(actions);
        this.actionBar = new ActionBar(appContext, actions);
        this.journalActions = new JournalActions(this, appContext, guildService, this::switchGuildFromJournal);
        journalActions.registerActions(actions);
        setJMenuBar(new MainMenuBar(actions));
        this.contextBar = new ContextBar(appContext, actions);
        actionBar.buildBar();

        // Two rows on top: what is selected (context bar), and the process bar (action bar).
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.setOpaque(false);
        topArea.add(contextBar, BorderLayout.NORTH);
        topArea.add(actionBar, BorderLayout.CENTER);

        // The center: one stage view per process stage, switched by the process bar's tiles.
        // Input and strategic concept have a view; output follows (M3c). Start with the concept.
        this.stageViews = new JPanel(new CardLayout());
        stageViews.setOpaque(false);
        addStageView(new InputStageView(appContext, actions));
        addStageView(new ConceptStageView(appContext, actions, fortificationMapPanel));
        ProcessBar processBar = actionBar.processBar();
        processBar.addStageSelectionListener(this::showStage);
        showStage(Stage.CONCEPT);

        setContentPane(new BackgroundPanel(new BorderLayout(), background));
        getContentPane().add(topArea, BorderLayout.NORTH);
        getContentPane().add(stageViews, BorderLayout.CENTER);

        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    /** Adds {@code view} as a card of {@link #stageViews} and makes its stage's tile clickable. */
    private void addStageView(StageView view) {
        stageViews.add(view, view.stage().name());
        viewsByStage.put(view.stage(), view);
        actionBar.processBar().setStageAvailable(view.stage(), true);
    }

    /** Shows the view of {@code stage} (if it has one), highlights its tile and lets the view refresh itself. */
    private void showStage(Stage stage) {
        StageView view = viewsByStage.get(stage);
        if (view == null) {
            return;
        }
        ((CardLayout) stageViews.getLayout()).show(stageViews, stage.name());
        actionBar.processBar().setActiveStage(stage);
        view.onShown();
    }

    /** The stage views in the center - package-visible for tests. */
    JPanel stageViews() {
        return stageViews;
    }

    /** The process bar - package-visible for tests. */
    ProcessBar processBar() {
        return actionBar.processBar();
    }

    /**
     * Shows the (fully built) frame and reports the startup update check
     * once it completes - see {@link #checkForUpdatesAtStartup}. Kept out of
     * the constructor so {@code C2WApp} can build the frame behind the
     * splash screen and only reveal it when startup is done.
     *
     * @param startupCheck the update check started by {@code C2WApp} at the very beginning of startup
     */
    public void showMainWindow(CompletableFuture<UpdateChecker.UpdateCheckResult> startupCheck) {
        setVisible(true);
        checkForUpdatesAtStartup(startupCheck);
    }

    /**
     * Registers the actions whose handlers live in this frame (file, guild and lineup
     * menus) - see {@link MainMenuBar} for where they show up.
     */
    private void registerActions(MainActions actions) {
        actions.register(new AppAction(ActionId.SETTINGS, this::onOpenSettings));
        // Independent of the open guild/lineup - the catalogs are shared by every guild.
        actions.register(new AppAction(ActionId.COWSCORE,
                () -> CowScoreDialog.open(this, appContext.catalog(),
                        CowScoreTab.forFortificationType(appContext.fortificationType()))));
        actions.register(new AppAction(ActionId.SHOW_LOG, this::onShowLog));
        actions.register(new AppAction(ActionId.OPEN_HERO_WARS, () -> onOpenWeb(HERO_WARS_URL))
                .withSmallIcon(IconLoader.iconFor(ICON_HERO_WARS, ActionBar.TOOLBAR_ICON_SIZE)));

        actions.register(new AppAction(ActionId.NEW_GUILD, this::onNewGuild)
                .withSmallIcon(IconLoader.iconFor(ICON_NEW_GUILD, ActionBar.TOOLBAR_ICON_SIZE, IconLoader.GREEN)));
        actions.register(new AppAction(ActionId.OPEN_GUILD_EDITOR, this::onOpenGuildEditor)
                .withSmallIcon(IconLoader.iconForButton(ICON_OPEN_GUILD_EDITOR)));
        actions.register(new AppAction(ActionId.REMOVE_GUILD, this::onRemoveGuild)
                .withSmallIcon(IconLoader.iconFor(ICON_REMOVE_GUILD, ActionBar.TOOLBAR_ICON_SIZE, IconLoader.RED)));

        actions.register(new AppAction(ActionId.NEW_LINEUP, this::onNewLineup)
                .withSmallIcon(IconLoader.iconFor(ICON_NEW_LINEUP, ActionBar.TOOLBAR_ICON_SIZE, IconLoader.GREEN)));
        actions.register(new AppAction(ActionId.REMOVE_LINEUP, this::onRemoveLineup)
                .withSmallIcon(IconLoader.iconFor(ICON_REMOVE_LINEUP, ActionBar.TOOLBAR_ICON_SIZE, IconLoader.RED)));
        actions.register(new AppAction(ActionId.CLEAR_LINEUP, this::onClearLineup)
                .withSmallIcon(IconLoader.iconForButton(ICON_CLEAR_LINEUP)));
    }

    /** The actions of menu bar and toolbar - package-visible for tests. */
    MainActions actions() {
        return actions;
    }

    /**
     * Switches to another guild for the journal import ("switch to guild ..."), with the
     * usual "unsaved changes" prompt - see {@link ContextBar#confirmDiscardUnsavedChanges()}.
     */
    private boolean switchGuildFromJournal(String folderName) {
        return contextBar.confirmDiscardUnsavedChanges() && contextBar.switchToGuild(folderName);
    }

    /**
     * Creates a new guild folder and switches to it (see {@link
     * GuildService#createGuild}). {@link #contextBar} owns the
     * "unsaved changes" prompt and the error handling of a guild switch, so
     * this reuses its {@link ContextBar#confirmDiscardUnsavedChanges()}/
     * {@link ContextBar#switchToGuild} instead of duplicating them.
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
        if (ActionBar.containsIllegalFilenameChar(name)) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("common.illegalFilenameChars", ActionBar.ILLEGAL_FILENAME_CHARS),
                    LanguageService.displayName("mainFrame.newGuild.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (guildService.guildExists(name)) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("mainFrame.newGuild.alreadyExists", name),
                    LanguageService.displayName("mainFrame.newGuild.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!contextBar.confirmDiscardUnsavedChanges()) {
            return;
        }

        guildService.createGuild(name);
        contextBar.switchToGuild(name);
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
        String journalNote = journalActions.removeGuildJournalNote(folderName);
        if (!journalNote.isEmpty()) {
            confirmMessage += "\n" + journalNote;
        }
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
            contextBar.switchToGuild(nextFolderName);
        }
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
        if (ActionBar.containsIllegalFilenameChar(name)) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("common.illegalFilenameChars", ActionBar.ILLEGAL_FILENAME_CHARS),
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
                LanguageService.displayName("mainFrame.removeLineup.confirmMessage", ContextBar.stripLineupSuffix(fileName)),
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
     * Silent, best-effort update check started once at startup (by
     * {@code C2WApp}, behind the splash screen) and reported once the frame
     * is visible (Cow2Win todos 1.1) - only ever surfaces a dialog when a
     * newer release was actually found ({@link #handleUpdateCheckResult}),
     * so a missing internet connection or an unreachable GitHub never
     * bothers the user on every single startup. Every outcome is still
     * logged (see {@link Logger}), so "did it even check" is visible in the
     * log panel/file if needed.
     */
    private void checkForUpdatesAtStartup(CompletableFuture<UpdateChecker.UpdateCheckResult> startupCheck) {
        startupCheck.thenAccept(result ->
                SwingUtilities.invokeLater(() -> handleUpdateCheckResult(result)));
    }

    /**
     * Reports one {@link UpdateChecker.UpdateCheckResult} - always logs it,
     * and shows a dialog only for {@link UpdateChecker.UpdateCheckResult.Status#UPDATE_AVAILABLE}
     * (offering to open the release page via {@link #onOpenWeb}).
     */
    private void handleUpdateCheckResult(UpdateChecker.UpdateCheckResult result) {
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
            case UP_TO_DATE ->
                    Logger.log("Update check: already up to date (" + result.currentVersion() + ")");
            case CHECK_FAILED ->
                    Logger.log("Update check failed or could not be evaluated (installed: " + result.currentVersion() + ")");
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
        appContext.journal().closeCurrent();
        System.exit(0);
    }

    /** Sets the window title for the open guild, see {@link #titleFor}. */
    private void updateTitle() {
        var guild = appContext.guild();
        setTitle(titleFor(guildDisplayName(guild.name(), guild.id()), AppVersion.current(),
                appContext.hasUnsavedChanges()));
    }

    /** The guild's name for the window title, or its id if the name is blank. */
    static String guildDisplayName(String guildName, String guildId) {
        return guildName.isBlank() ? guildId : guildName;
    }

    /**
     * The window title, e.g. "Cow2Win 1.0.2 - Testgilde" - prefixed with "*"
     * while the guild or the lineup has unsaved changes (see
     * {@link AppContext#hasUnsavedChanges()}). GUI-free so it can be tested
     * directly.
     *
     * @param guildDisplayName the guild's name, or its id if the name is blank
     */
    static String titleFor(String guildDisplayName, String version, boolean unsaved) {
        return (unsaved ? "*" : "") + BASE_TITLE + " " + version + " - " + guildDisplayName;
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
     * current size, once, before any child is painted, so the same background
     * shows behind the whole window - every panel/scroll pane in between
     * (see the constructor and FortificationMapPanel) is kept non-opaque so it
     * is actually visible through them.
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

