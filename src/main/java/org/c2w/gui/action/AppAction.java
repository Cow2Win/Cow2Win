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
 */
public class AppAction extends AbstractAction {

    private final ActionId id;
    private final transient Runnable handler;

    public AppAction(ActionId id, Runnable handler) {
        super(LanguageService.displayName(Objects.requireNonNull(id, "id").textKey()));
        this.id = id;
        this.handler = Objects.requireNonNull(handler, "handler");
        putValue(SHORT_DESCRIPTION, LanguageService.displayName(id.textKey()));
    }

    /** Sets the icon shown in front of a menu entry. */
    public AppAction withSmallIcon(Icon icon) {
        putValue(SMALL_ICON, icon);
        return this;
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
