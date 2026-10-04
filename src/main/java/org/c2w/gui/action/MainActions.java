package org.c2w.gui.action;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of the main window's {@link AppAction}s, one per {@link ActionId}. The
 * owners of the handlers ({@code Cow2Frame}, {@code ToolbarPanel},
 * {@code JournalActions}) register their actions here; menu bar and toolbar are
 * then built from it.
 */
public final class MainActions {

    private final Map<ActionId, AppAction> actions = new EnumMap<>(ActionId.class);

    /**
     * Registers {@code action} under its {@link AppAction#id()}.
     *
     * @return the action, for chaining
     * @throws IllegalStateException if an action with that id is already registered
     */
    public AppAction register(AppAction action) {
        if (actions.putIfAbsent(action.id(), action) != null) {
            throw new IllegalStateException("Action already registered: " + action.id());
        }
        return action;
    }

    /**
     * @throws IllegalStateException if no action is registered for {@code id}
     */
    public AppAction get(ActionId id) {
        AppAction action = actions.get(id);
        if (action == null) {
            throw new IllegalStateException("Action not registered: " + id);
        }
        return action;
    }

    public boolean isRegistered(ActionId id) {
        return actions.containsKey(id);
    }
}
