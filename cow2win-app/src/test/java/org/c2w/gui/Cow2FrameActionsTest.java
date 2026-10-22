package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.infra.AppVersion;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        guildService.createGuild("Alpha", false);
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
    @DisplayName("After building the frame, all three stages have a view (clickable tiles); the input stage is shown")
    void availableStages() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new Cow2Frame(context));

        assertTrue(frame.processBar().isStageAvailable(Stage.INPUT));
        assertTrue(frame.processBar().isStageAvailable(Stage.CONCEPT));
        assertTrue(frame.processBar().isStageAvailable(Stage.OUTPUT));
        assertEquals(Stage.INPUT, frame.processBar().activeStage());
    }

    @Test
    @DisplayName("The window title stays \"Cow2Win {version}\" - with unsaved changes too")
    void titleStaysFixed() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new Cow2Frame(context));
        String expected = Cow2Frame.titleFor(AppVersion.current());
        assertEquals(expected, frame.getTitle());

        SwingUtilities.invokeAndWait(() -> context.setGuildDirty(true));
        assertEquals(expected, frame.getTitle());
    }

    @Test
    @DisplayName("\"Change plan\" switches to the output stage instead of opening a dialog")
    void changePlanShowsOutputStage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new Cow2Frame(context);
            frame.actions().get(ActionId.OPEN_CHANGE_PLAN).actionPerformed(null);
        });

        assertEquals(Stage.OUTPUT, frame.processBar().activeStage());
        assertTrue(java.util.Arrays.stream(java.awt.Window.getWindows()).noneMatch(w -> w instanceof JDialog && w.isVisible()));
    }

    @Test
    @DisplayName("Menu entries that have a toolbar icon show it in the menu as well")
    void toolbarActionsHaveMenuIcons() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new Cow2Frame(context));

        for (ActionId id : List.of(ActionId.RUN_ALGORITHM, ActionId.COMPARE_LINEUPS, ActionId.SHOW_TEAMS,
                ActionId.OPEN_CHANGE_PLAN, ActionId.GENERATE_REPORT, ActionId.OPEN_GUILD_TEAM_ENTRY,
                ActionId.COPY_LINEUP, ActionId.CLEAR_LINEUP)) {
            assertNotNull(frame.actions().get(id).getValue(Action.SMALL_ICON), id + " has no menu icon");
        }
    }

    @Test
    @DisplayName("\"Clear lineup\" and \"Copy lineup\" change their icon with the fortification type and back")
    void lineupMenuIconsFollowFortificationType() throws Exception {
        // The frame restores the type selected last - start from heroes explicitly.
        SwingUtilities.invokeAndWait(() -> {
            frame = new Cow2Frame(context);
            context.setFortificationType(FortificationType.HERO);
        });

        for (ActionId id : List.of(ActionId.CLEAR_LINEUP, ActionId.COPY_LINEUP)) {
            Object heroIcon = frame.actions().get(id).getValue(Action.SMALL_ICON);
            SwingUtilities.invokeAndWait(() -> context.setFortificationType(FortificationType.TITAN));
            Object titanIcon = frame.actions().get(id).getValue(Action.SMALL_ICON);
            SwingUtilities.invokeAndWait(() -> context.setFortificationType(FortificationType.HERO));

            assertNotNull(heroIcon, id + " hero icon");
            assertNotNull(titanIcon, id + " titan icon");
            assertNotSame(heroIcon, titanIcon, id.toString());
            assertSame(heroIcon, frame.actions().get(id).getValue(Action.SMALL_ICON), id + " back to heroes");
        }
    }
}
