package org.c2w.gui.action;

import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Objects;

/**
 * A main window function as a Swing {@link Action}: name and tooltip come from
 * {@link LanguageService} (the {@link ActionId#textKey()}), the work is done by a
 * plain {@link Runnable} owned by whoever creates the action. Menu entries show
 * {@link #SMALL_ICON}, toolbar buttons {@link #LARGE_ICON_KEY}.
 *
 * <p>The name (shown by menu entries only - toolbar buttons hide it) ends with "…" when
 * the action {@link ActionId#opensWindow() opens a window}, see {@link #menuText}; the
 * tooltip is the plain text.
 */
public class AppAction extends AbstractAction {

    /** Appended to the menu text of an action that opens a window - with a space, like the texts that already end with it. */
    private static final String ELLIPSIS = "…";

    private final ActionId id;
    private final transient Runnable handler;

    public AppAction(ActionId id, Runnable handler) {
        this.id = Objects.requireNonNull(id, "id");
        this.handler = Objects.requireNonNull(handler, "handler");
        setText(id.textKey());
    }

    /** The menu entry text of {@code id}: its text, plus " …" if it opens a window and the text does not end with "…" yet. */
    public static String menuText(ActionId id) {
        return menuText(id, id.textKey());
    }

    /** {@link #menuText(ActionId)} for another text key of the same action, see {@link #setText}. */
    public static String menuText(ActionId id, String textKey) {
        String text = LanguageService.displayName(textKey);
        return id.opensWindow() && !text.endsWith(ELLIPSIS) ? text + " " + ELLIPSIS : text;
    }

    /**
     * Sets name and tooltip from {@code textKey} - for an action whose text follows a
     * selection, e.g. the fortification type. Bound menu entries and buttons update themselves.
     */
    public void setText(String textKey) {
        putValue(NAME, menuText(id, textKey));
        putValue(SHORT_DESCRIPTION, LanguageService.displayName(textKey));
    }

    /** Sets the icon shown in front of a menu entry. */
    public AppAction withSmallIcon(Icon icon) {
        putValue(SMALL_ICON, icon);
        return this;
    }

    /** Sets {@code icon} for both a menu entry and a toolbar button - for actions offered in both places. */
    public AppAction withIcon(Icon icon) {
        return withSmallIcon(icon).withLargeIcon(icon);
    }

    /** Sets the icon shown on a toolbar button. */
    public AppAction withLargeIcon(Icon icon) {
        putValue(LARGE_ICON_KEY, icon);
        return this;
    }

    public ActionId id() {
        return id;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        handler.run();
    }
}
