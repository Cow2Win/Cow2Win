package org.c2w.gui;

import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildEditorDialog;
import org.c2w.util.AppContext;
import org.c2w.util.Config;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.Optional;

public class Cow2Frame extends JFrame {

    /** Base window title, prefixed to the guild name/season (see {@link #updateTitle()}). */
    private static final String BASE_TITLE = "Cow2Win";

    /** Classpath-absolute path to the frame icon (see {@link #loadFrameIcon()}). */
    private static final String FRAME_ICON_PATH = "/images/app/cow.png";

    /** Fraction of the window width given to the left side of the split pane by default. */
    private static final double LEFT_SPLIT_RATIO = 2.0 / 3.0;

    private final JSplitPane splitPane;
    private final AppContext appContext;
    private final FortificationMapPanel fortificationMapPanel;
    private final ToolbarPanel toolbarPanel;
    private final TeamsOverviewPanel teamsOverviewPanel;
    private final LogPanel logPanel;

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

        this.fortificationMapPanel = new FortificationMapPanel(appContext);
        this.toolbarPanel = new ToolbarPanel(appContext, fortificationMapPanel, this::onGuildSwitched);

        this.teamsOverviewPanel = new TeamsOverviewPanel(appContext, fortificationMapPanel,
                this::onOpenGuildEditor);
        // FortificationMapPanel is built before teamsOverviewPanel exists (see
        // above), so it cannot take this as a constructor argument - wired up
        // via a setter instead, so a FortificationEntryDialog save (see
        // FortificationPanel#openEntryDialog) also refreshes teamsOverviewPanel's
        // own tables instead of leaving them stale (which used to cause a save
        // error there afterwards - guildWithCurrentSelection() indexes into the
        // stale rows by position).
        fortificationMapPanel.setOnGuildChangedElsewhere(teamsOverviewPanel::refreshFromContext);
        this.logPanel = new LogPanel();
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JScrollPane(teamsOverviewPanel), BorderLayout.CENTER);
        rightPanel.add(logPanel, BorderLayout.SOUTH);

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
        if (Config.editedGuild || Config.editedLineup) {
            String what = Config.editedGuild && Config.editedLineup ? "guild and lineup"
                    : Config.editedGuild ? "guild" : "lineup";
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
        setTitle(BASE_TITLE + " - " + displayName );
    }


    private static Optional<ImageIcon> loadFrameIcon() {
        URL resource = Cow2Frame.class.getResource(FRAME_ICON_PATH);
        if (resource == null) {
            System.err.println("Frame icon not found on classpath: " + FRAME_ICON_PATH);
            return Optional.empty();
        }
        return Optional.of(new ImageIcon(resource));
    }
}

