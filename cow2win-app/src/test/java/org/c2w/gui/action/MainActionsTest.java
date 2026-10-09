package org.c2w.gui.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainActionsTest {

    @Test
    @DisplayName("register + get returns the same instance")
    void registerAndGet() {
        MainActions actions = new MainActions();
        AppAction action = new AppAction(ActionId.SETTINGS, () -> { });

        assertSame(action, actions.register(action));
        assertSame(action, actions.get(ActionId.SETTINGS));
        assertTrue(actions.isRegistered(ActionId.SETTINGS));
        assertFalse(actions.isRegistered(ActionId.SHOW_LOG));
    }

    @Test
    @DisplayName("Registering an id twice throws IllegalStateException")
    void duplicateRegistrationThrows() {
        MainActions actions = new MainActions();
        actions.register(new AppAction(ActionId.SETTINGS, () -> { }));

        assertThrows(IllegalStateException.class, () -> actions.register(new AppAction(ActionId.SETTINGS, () -> { })));
    }

    @Test
    @DisplayName("get on an unregistered id throws IllegalStateException")
    void getUnregisteredThrows() {
        assertThrows(IllegalStateException.class, () -> new MainActions().get(ActionId.SHOW_LOG));
    }
}
