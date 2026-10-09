package org.c2w.gui.action;

import org.c2w.gui.common.FlatButton;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** {@link AppAction} - headless, the components are only created, never shown. */
class AppActionTest {

    @Test
    @DisplayName("Name and tooltip come from LanguageService, actionPerformed runs the handler")
    void nameTooltipAndHandler() {
        AtomicInteger calls = new AtomicInteger();
        AppAction action = new AppAction(ActionId.RUN_ALGORITHM, calls::incrementAndGet);

        String text = LanguageService.displayName(ActionId.RUN_ALGORITHM.textKey());
        assertEquals(text, action.getValue(Action.NAME));
        assertEquals(text, action.getValue(Action.SHORT_DESCRIPTION));
        assertSame(ActionId.RUN_ALGORITHM, action.id());

        action.actionPerformed(null);
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("An action that opens a window gets \" …\" in its name only, the tooltip stays plain")
    void ellipsisForWindowActions() {
        String text = LanguageService.displayName(ActionId.GENERATE_REPORT.textKey());
        AppAction action = new AppAction(ActionId.GENERATE_REPORT, () -> { });

        assertEquals(text + " …", action.getValue(Action.NAME));
        assertEquals(text, action.getValue(Action.SHORT_DESCRIPTION));
    }

    @Test
    @DisplayName("Every menu text of a window-opening action ends with exactly one \"…\", the others with none")
    void menuTextsEndWithEllipsisOnce() {
        for (ActionId id : ActionId.values()) {
            String menuText = AppAction.menuText(id);
            assertEquals(id.opensWindow(), menuText.endsWith("…"), id + ": " + menuText);
            assertFalse(menuText.endsWith("… …") || menuText.endsWith("……"), id + ": " + menuText);
        }
    }

    @Test
    @DisplayName("setEnabled(false) disables a bound menu item and button")
    void enabledStateReachesBoundComponents() {
        AppAction action = new AppAction(ActionId.RUN_ALGORITHM, () -> { });
        JMenuItem item = new JMenuItem(action);
        JButton button = FlatButton.forAction(action);
        assertTrue(item.isEnabled());
        assertTrue(button.isEnabled());

        action.setEnabled(false);
        assertFalse(item.isEnabled());
        assertFalse(button.isEnabled());

        action.setEnabled(true);
        assertTrue(item.isEnabled());
        assertTrue(button.isEnabled());
    }

    @Test
    @DisplayName("Changing LARGE_ICON_KEY / SHORT_DESCRIPTION updates a bound button; its text stays hidden")
    void iconAndTooltipReachBoundButton() {
        Icon blue = new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
        Icon red = new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
        AppAction action = new AppAction(ActionId.SAVE_GUILD, () -> { }).withLargeIcon(blue);
        FlatButton button = FlatButton.forAction(action);
        assertSame(blue, button.getIcon());
        assertTrue(button.getHideActionText());
        assertTrue(button.getText() == null || button.getText().isEmpty());

        action.putValue(Action.LARGE_ICON_KEY, red);
        action.putValue(Action.SHORT_DESCRIPTION, "unsaved");
        assertSame(red, button.getIcon());
        assertEquals("unsaved", button.getToolTipText());
    }

    @Test
    @DisplayName("A menu item shows the small icon")
    void menuItemShowsSmallIcon() {
        Icon small = new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
        AppAction action = new AppAction(ActionId.NEW_GUILD, () -> { }).withSmallIcon(small);
        assertSame(small, new JMenuItem(action).getIcon());
    }
}
