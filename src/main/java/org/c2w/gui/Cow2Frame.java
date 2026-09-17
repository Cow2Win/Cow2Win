package org.c2w.gui;

import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildEditorDialog;
import org.c2w.gui.hero.HeroBuffFitScoresDialog;
import org.c2w.util.AppContext;
import org.c2w.util.AppVersion;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;
import org.c2w.util.UpdateChecker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import static org.c2w.C2WApp.BASE_TITLE;

public class Cow2Frame extends JFrame {

   /** Classpath-absolute path to the frame icon (see {@link #loadFrameIcon()}). */
    private static final String FRAME_ICON_PATH = "/images/app/cow.png";

    /** Fraction of the window width given to the left side of the split pane by default. */
    private static final double LEFT_SPLIT_RATIO = 2.0 / 3.0;

    private static final String HERO_WARS_URL = "https://www.hero-wars.com/";

    private final JSplitPane splitPane;
    private final AppContext appContext;
    private final FortificationMapPanel fortificationMapPanel;
    private final ToolbarPanel toolbarPanel;
    private final TeamsOverviewPanel teamsOverviewPanel;
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
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });
        this.appContext = appContext;
        updateTitle();
        loadFrameIcon().ifPresent(icon -> setIconImage(icon.getImage()));
        setJMenuBar(buildMenuBar());

        this.fortificationMapPanel = new FortificationMapPanel(appContext);

        // teamsOverviewPanel is built before toolbarPanel (unlike before
        // 2026-09-09) since toolbarPanel now needs a direct reference to it
        // for the "save guild"/"run algorithm" controls moved into it from
        // teamsOverviewPanel's own (now removed) toolbar - see ToolbarPanel's
        // class-level KEY_SAVE_GUILD Javadoc.
        this.teamsOverviewPanel = new TeamsOverviewPanel(appContext, fortificationMapPanel);
        this.toolbarPanel = new ToolbarPanel(appContext, fortificationMapPanel, teamsOverviewPanel,
                this::onOpenGuildEditor, this::onGuildSwitched);
        // FortificationMapPanel is built before teamsOverviewPanel exists (see
        // above), so it cannot take this as a constructor argument - wired up
        // via a setter instead, so a FortificationEntryDialog save (see
        // FortificationPanel#openEntryDialog) also refreshes teamsOverviewPanel's
        // own tables instead of leaving them stale (which used to cause a save
        // error there afterwards - guildWithCurrentSelection() indexes into the
        // stale rows by position).
        fortificationMapPanel.setOnGuildChangedElsewhere(teamsOverviewPanel::refreshFromContext);
        // LogPanel is still created here (so it starts listening to Logger right
        // away, see LogPanel's constructor / Logger#addListener), but - since
        // 2026-09-16 - it is no longer permanently docked into the main window
        // (it used to take up a fixed SOUTH strip here, which Thorsten found ate
        // too much screen space for something rarely needed). It is shown
        // on demand instead, in a lazily-created dialog - see #onShowLog.
        // Logger#addListener replays the full in-memory history to a newly
        // registered listener, so nothing is lost by not displaying it from the
        // start; nothing here changes that registration.
        this.logPanel = new LogPanel();
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JScrollPane(teamsOverviewPanel), BorderLayout.CENTER);

        JScrollPane leftScrollPane = new JScrollPane(fortificationMapPanel);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScrollPane, rightPanel);
        splitPane.setResizeWeight(LEFT_SPLIT_RATIO);
        splitPane.setOneTouchExpandable(true);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolbarPanel, BorderLayout.NORTH);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Fallback bounds in case the platform/window manager does not honor
        // MAXIMIZED_BOTH (some Linux window managers don't) - without this,
        // the frame would otherwise fall back to its (tiny) preferred size.
        // getMaximumWindowBounds() already excludes taskbars/docks.
        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setVisible(true);

        // The divider location must be set once the frame has its real,
        // maximized size, which is not yet reliably available synchronously
        // right after setVisible(true) on every platform - so this is
        // deferred to the next event queue cycle.
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(LEFT_SPLIT_RATIO));

        checkForUpdatesAtStartup();
    }

    /**
     * Builds the frame's menu bar: "Settings" > "Configs" (see
     * {@link #onOpenSettings()}) and "Tools" > "Hero Buff Fit Scores" (see
     * {@link #onOpenHeroBuffFitScores()} - moved here 2026-09-11 from a
     * {@code ToolbarPanel} toolbar button, since maintaining the hero
     * catalog's buff fit scores is an infrequent, guild-independent task
     * that doesn't need a permanently visible button).
     */
    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu settingsMenu = new JMenu(LanguageService.displayName("menu.settings"));
        JMenuItem configsItem = new JMenuItem(LanguageService.displayName("menu.configuration"));
        configsItem.addActionListener(e -> onOpenSettings());
        settingsMenu.add(configsItem);


        JMenuItem heroBuffFitScoresItem = new JMenuItem("CowScore");
        heroBuffFitScoresItem.addActionListener(e -> onOpenHeroBuffFitScores());
        settingsMenu.add(heroBuffFitScoresItem);

        JMenuItem checkForUpdatesItem = new JMenuItem(LanguageService.displayName("menu.checkForUpdates"));
        checkForUpdatesItem.addActionListener(e -> onCheckForUpdates());
        settingsMenu.add(checkForUpdatesItem);
        menuBar.add(settingsMenu);

        JMenu hwMenu = new JMenu("Hero wars");
        JMenuItem hwWebItem = new JMenuItem(HERO_WARS_URL);
        hwWebItem.addActionListener(e -> onOpenWeb(HERO_WARS_URL));
        hwMenu.add(hwWebItem);

        JMenuItem hwFandomItem = new JMenuItem("Hero wars Fandom");
        hwFandomItem.addActionListener(e -> onOpenWeb("https://hero-wars.fandom.com/wiki/Guild/Clash_of_Worlds"));
        hwMenu.add(hwFandomItem);

        JMenuItem hwNexterItem = new JMenuItem("Nexters");
        hwNexterItem.addActionListener(e -> onOpenWeb("https://support-hwde.nexters.com/hc/en-us/articles/7829409962386-Clash-of-Worlds"));
        hwMenu.add(hwNexterItem);


        menuBar.add(hwMenu);

        JMenu viewMenu = new JMenu(LanguageService.displayName("menu.view"));
        JMenuItem showLogItem = new JMenuItem(LanguageService.displayName("menu.showLog"));
        showLogItem.addActionListener(e -> onShowLog());
        viewMenu.add(showLogItem);
        menuBar.add(viewMenu);

        return menuBar;
    }

    /** Opens {@link SettingsDialog} (currently: choosing the display language). */
    private void onOpenSettings() {
        SettingsDialog.show(this);
    }

    /**
     * Opens {@link HeroBuffFitScoresDialog} - independent of the currently
     * open guild/lineup (see that dialog's class Javadoc), so this only
     * needs the frame itself as owner.
     */
    private void onOpenHeroBuffFitScores() {
        new HeroBuffFitScoresDialog(this).setVisible(true);
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

    /** "Settings" > "Check for Updates" menu item - unlike {@link #checkForUpdatesAtStartup()}, always reports back, including "already up to date" and a failed check. */
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
        teamsOverviewPanel.refreshFromContext();
        updateTitle();
    }

    private void onGuildSwitched() {
        teamsOverviewPanel.refreshFromContext();
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

