package org.c2w;

import org.c2w.data.journal.db.JournalException;
import org.c2w.gui.Cow2Frame;
import org.c2w.gui.SplashWindow;
import org.c2w.gui.common.GuiUtils;
import org.c2w.infra.CatalogVersion;
import org.c2w.infra.Logger;
import org.c2w.infra.UpdateChecker;
import org.c2w.service.AppContext;
import org.c2w.service.WorkspaceBootstrap;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;

public class C2WApp {

    public static final String BASE_TITLE = "Cow2Win";

    public static void main(String[] args) {
        installLookAndFeel();
        installUncaughtExceptionLogging();

        SplashWindow splash = SplashWindow.showSplash();

        // Started first and left running in the background - Cow2Frame reports the
        // result once it is visible, so a slow/missing network never delays startup.
        splash.setStatus("Checking for updates ...");
        CompletableFuture<UpdateChecker.UpdateCheckResult> updateCheck = UpdateChecker.checkAsync();

        Cow2Frame[] frame = new Cow2Frame[1];
        try {
            AppContext context = WorkspaceBootstrap.start(splash::setStatus);
            logCatalogVersion();

            splash.setStatus("Starting database ...");
            openJournal(context);

            splash.setStatus("Building GUI ...");
            GuiUtils.runOnEdtAndWait(() -> frame[0] = new Cow2Frame(context));
        } catch (RuntimeException | Error e) {
            // Without this the (still visible) splash would keep the JVM alive forever.
            splash.close();
            throw e;
        }

        splash.awaitMinimumDisplayTime();
        SwingUtilities.invokeLater(() -> {
            frame[0].showMainWindow(updateCheck);
            splash.close();
        });

        Logger.log("Started: ");
    }

    /**
     * Opens the open guild's journal database (if it has one) already
     * behind the splash screen, instead of lazily on the first journal
     * action. Never prevents startup: a failure (e.g. the database is
     * locked by another running instance) is only logged, and the journal
     * is simply opened (and the error reported) again on first use.
     */
    private static void openJournal(AppContext context) {
        try {
            context.journal().repository(false);
        } catch (JournalException | RuntimeException e) {
            Logger.logException("Could not open the journal database at startup", e);
        }
    }

    /**
     * Installs the Material Design "Oceanic" look and feel (material-ui-swing)
     * for the whole application. This must run before any Swing component is
     * created, so it is the very first thing {@link #main} does - that way even
     * the first-run setup dialogs already use the theme.
     *
     * <p>A failure here must never prevent startup: if the look and feel cannot
     * be installed, the exception is logged and the application simply falls back
     * to the default (cross-platform) Swing look and feel.
     */
    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new MaterialLookAndFeel(new MaterialOceanicTheme()));
        } catch (UnsupportedLookAndFeelException e) {
            Logger.logException("Could not install the MaterialOceanicTheme look and feel", e);
        }
    }

    /**
     * Makes sure that literally every exception reaches {@link Logger}
     * (LogPanel + the persistent log file), even ones that are not already
     * wrapped in a {@code try}/{@code catch} that itself calls
     * {@link Logger#logException} - the last line of defense for "a) alle
     * Exceptions" from the log-file todo item.
     *
     * <p>Two handlers are needed because Swing does not use the JVM-wide
     * uncaught-exception handler for exceptions thrown out of an event
     * listener (a button click, a table edit, ...): those are caught
     * internally by {@code java.awt.EventDispatchThread} and only reach a
     * custom handler that is registered via the "sun.awt.exception.handler"
     * system property - an old but still-supported hook, present in every
     * mainstream OpenJDK build, for exactly this purpose. Any other thread
     * (this one, background tasks, ...) is covered by the ordinary
     * {@link Thread#setDefaultUncaughtExceptionHandler}.
     */
    private static void installUncaughtExceptionLogging() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                Logger.logException("Uncaught exception on thread '" + thread.getName() + "'", throwable));
        System.setProperty("sun.awt.exception.handler", SwingUncaughtExceptionHandler.class.getName());
    }

    /**
     * Registered via the "sun.awt.exception.handler" system property (see
     * {@link #installUncaughtExceptionLogging()}) - Swing's
     * {@code EventDispatchThread} instantiates this reflectively (hence the
     * required public no-arg constructor) and calls {@link #handle} for
     * every exception that escapes an event listener uncaught.
     */
    public static final class SwingUncaughtExceptionHandler {
        public void handle(Throwable throwable) {
            Logger.logException("Uncaught exception on the Swing event thread", throwable);
        }
    }

    /**
     * Logs the {@link CatalogVersion} of heroes.json/titans.json/fortifications.json
     * at startup (visible in the app's log panel), so it's clear at a glance
     * which game/patch state the catalog data was last checked against
     * without having to go dig through the data files themselves.
     */
    private static void logCatalogVersion() {
        String suffix = CatalogVersion.note().isBlank() ? "" : " (" + CatalogVersion.note() + ")";
        Logger.logToFile("Catalog data version: " + CatalogVersion.dataVersion() + suffix);
    }

}
