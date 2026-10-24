package org.c2w.gui.common;

import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Opens a link in the system's browser - or, for a {@code mailto:} link, in its mail program. A
 * failure is shown as a message over {@code parent}; for a mail link it names the address, so it
 * can be copied by hand.
 */
public final class WebLinks {

    private static final String MAILTO = "mailto";

    private WebLinks() {
    }

    /** Opens {@code url} (a web page or a {@code mailto:} link), see {@link WebLinks}. */
    public static void open(Component parent, String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            showError(parent, LanguageService.displayName("mainFrame.web.openError", url) + "\n" + e.getMessage(),
                    "mainFrame.web.errorTitle");
            return;
        }
        if (isMail(uri)) {
            mail(parent, uri);
        } else {
            browse(parent, uri);
        }
    }

    /** True for a {@code mailto:} link. */
    static boolean isMail(URI uri) {
        return MAILTO.equalsIgnoreCase(uri.getScheme());
    }

    /** The address of a {@code mailto:} link, without any {@code ?subject=...}. */
    static String mailAddress(URI uri) {
        String address = uri.getSchemeSpecificPart();
        int query = address.indexOf('?');
        return query < 0 ? address : address.substring(0, query);
    }

    private static void browse(Component parent, URI uri) {
        if (!isSupported(Desktop.Action.BROWSE)) {
            showError(parent, LanguageService.displayName("mainFrame.web.noBrowser"), "mainFrame.web.errorTitle");
            return;
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException | RuntimeException e) {
            showError(parent, LanguageService.displayName("mainFrame.web.openError", uri) + "\n" + e.getMessage(),
                    "mainFrame.web.errorTitle");
        }
    }

    private static void mail(Component parent, URI uri) {
        String message = LanguageService.displayName("mainFrame.web.mailError", mailAddress(uri));
        if (!isSupported(Desktop.Action.MAIL)) {
            showError(parent, message, "mainFrame.web.mailErrorTitle");
            return;
        }
        try {
            Desktop.getDesktop().mail(uri);
        } catch (IOException | RuntimeException e) {
            showError(parent, message + "\n" + e.getMessage(), "mainFrame.web.mailErrorTitle");
        }
    }

    private static boolean isSupported(Desktop.Action action) {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
    }

    private static void showError(Component parent, String message, String titleKey) {
        JOptionPane.showMessageDialog(parent, message, LanguageService.displayName(titleKey), JOptionPane.ERROR_MESSAGE);
    }
}
