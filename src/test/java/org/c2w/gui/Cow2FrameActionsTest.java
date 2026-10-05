package org.c2w.gui;

import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** After building {@link Cow2Frame} (never shown) every {@link ActionId} is registered. Skipped without a display. */
class Cow2FrameActionsTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private Cow2Frame frame;

    @BeforeEach
    void openWorkspace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
    }

    @AfterEach
    void closeWorkspace() throws Exception {
        if (context == null) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Every action id is registered after the frame is built")
    void everyActionIdIsRegistered() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new Cow2Frame(context));

        MainActions actions = frame.actions();
        for (ActionId id : ActionId.values()) {
            assertTrue(actions.isRegistered(id), id + " is not registered");
        }
    }

    @Test
    @DisplayName("After building the frame, input and strategic concept have a view (clickable tiles), output not; the concept is shown")
    void availableStages() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new Cow2Frame(context));

        assertTrue(frame.processBar().isStageAvailable(Stage.INPUT));
        assertTrue(frame.processBar().isStageAvailable(Stage.CONCEPT));
        assertFalse(frame.processBar().isStageAvailable(Stage.OUTPUT));
        assertEquals(Stage.CONCEPT, frame.processBar().activeStage());
    }

    @Test
    @DisplayName("Menu entries that have a toolbar icon show it in the menu as well")
    void toolbarActionsHaveMenuIcons() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new Cow2Frame(context));

        for (ActionId id : List.of(ActionId.OPEN_GUILD_EDITOR, ActionId.OPEN_GUILD_TEAM_ENTRY,
                ActionId.RUN_ALGORITHM, ActionId.COMPARE_LINEUPS, ActionId.SHOW_TEAMS,
                ActionId.OPEN_CHANGE_PLAN, ActionId.GENERATE_REPORT)) {
            assertNotNull(frame.actions().get(id).getValue(Action.SMALL_ICON), id + " has no menu icon");
        }
    }
}
