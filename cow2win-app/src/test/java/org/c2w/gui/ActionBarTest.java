package org.c2w.gui;

import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.cowscore.CowScoreTestSupport;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@link ActionBar} shows the {@link ProcessBar}; the save buttons sit in
 * {@link ContextBar} - headless, never shown.
 */
class ActionBarTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("The bar shows only the process bar - three stage tiles, no visible buttons")
    void barShowsTheProcessBar() {
        MainActions actions = new MainActions();
        ActionBar bar = new ActionBar(context, actions, new AlgorithmSelection());
        bar.buildBar();

        ProcessBar processBar = bar.processBar();
        assertEquals(List.of(processBar), List.of(bar.getComponents()));
        assertEquals(List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT),
                processBar.tiles().stream().map(ProcessBar.StageTile::stage).toList());
        assertTrue(CowScoreTestSupport.findAll(bar, AbstractButton.class).stream().noneMatch(Component::isVisible),
                "no visible buttons");
    }

    @Test
    @DisplayName("Only one team assignment dialog: the open one is reused, a new one only after it was closed")
    void oneTeamEntryDialog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        ActionBar bar = new ActionBar(context, new MainActions(), new AlgorithmSelection());
        SwingUtilities.invokeAndWait(() -> {
            JDialog first = bar.teamEntryDialog(ActionBarTest::packedDialog);
            try {
                assertSame(first, bar.teamEntryDialog(ActionBarTest::packedDialog), "the open dialog is reused");
                first.dispose();
                JDialog second = bar.teamEntryDialog(ActionBarTest::packedDialog);
                assertNotSame(first, second, "a new dialog once the first one was closed");
                second.dispose();
            } finally {
                first.dispose();
            }
        });
    }

    /** A dialog as the team assignment leaves it once shown: displayable (packed), but never on screen here. */
    private static JDialog packedDialog() {
        JDialog dialog = new JDialog();
        dialog.pack();
        return dialog;
    }

    @Test
    @DisplayName("The save buttons sit in the context bar, each right after its combo box")
    void saveButtonsFollowTheirComboBoxes() {
        MainActions actions = new MainActions();
        new ActionBar(context, actions, new AlgorithmSelection());
        ContextBar contextBar = new ContextBar(context, actions);

        Container left = (Container) ((BorderLayout) contextBar.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        List<Component> components = List.of(left.getComponents());
        List<Component> combos = components.stream().filter(c -> c instanceof JComboBox<?>).toList();
        assertEquals(2, combos.size(), "guild and lineup combo box");
        assertEquals(ActionId.SAVE_GUILD, actionIdAfter(components, combos.get(0)));
        assertEquals(ActionId.SAVE_LINEUP, actionIdAfter(components, combos.get(1)));
    }

    private static ActionId actionIdAfter(List<Component> components, Component component) {
        Component next = components.get(components.indexOf(component) + 1);
        assertInstanceOf(AbstractButton.class, next);
        return ((AppAction) ((AbstractButton) next).getAction()).id();
    }
}
