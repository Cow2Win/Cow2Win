package org.c2w.gui.journal;

import org.c2w.data.repository.Catalog;
import org.c2w.gui.SettingsDialogAccess;
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

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link JournalActions}: the journal actions are only enabled for a guild master's guild - headless. */
class JournalActionsTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;

    @BeforeEach
    void openWorkspace() {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("All six journal actions are enabled only while a guild master's guild is open")
    void enabledOnlyForGuildMaster() throws Exception {
        MainActions actions = new MainActions();
        new JournalActions(null, context, guildService, folder -> false).registerActions(actions);
        List<ActionId> journalIds = Arrays.stream(ActionId.values()).filter(id -> id.stage() == Stage.JOURNAL).toList();
        assertEquals(6, journalIds.size());
        assertTrue(journalIds.stream().noneMatch(id -> actions.get(id).isEnabled()), "no guild open");

        guildService.createGuild("Plain", false);
        guildService.createGuild("Master", true);
        guildService.switchToGuild("Plain");
        assertTrue(journalIds.stream().noneMatch(id -> actions.get(id).isEnabled()), "guild without the flag");

        guildService.switchToGuild("Master");
        assertTrue(journalIds.stream().allMatch(id -> actions.get(id).isEnabled()));

        guildService.switchToGuild("Plain");
        assertTrue(journalIds.stream().noneMatch(id -> actions.get(id).isEnabled()));
    }

    @Test
    @DisplayName("The import chooser: same size as the settings' directory chooser, several files, the CSV filter preselected")
    void importChooser() {
        JFileChooser chooser = JournalActions.createImportChooser(workspace.toFile());

        assertEquals(SettingsDialogAccess.directoryChooserSize(), chooser.getPreferredSize());
        assertTrue(chooser.isMultiSelectionEnabled());
        FileNameExtensionFilter filter = assertInstanceOf(FileNameExtensionFilter.class, chooser.getFileFilter());
        assertArrayEquals(new String[]{"csv"}, filter.getExtensions());
        assertTrue(chooser.isAcceptAllFileFilterUsed());
        assertEquals(workspace.toFile().getAbsoluteFile(), chooser.getCurrentDirectory().getAbsoluteFile());
    }

    @Test
    @DisplayName("The guild master flag of another guild is read from its file without opening it (import guild switch)")
    void guildMasterFlagOfAnotherGuild() {
        guildService.createGuild("Plain", false);
        guildService.createGuild("Master", true);

        assertFalse(guildService.isGuildMasterGuild("Plain"));
        assertTrue(guildService.isGuildMasterGuild("Master"));
        assertFalse(guildService.isGuildMasterGuild("Missing"));
        assertNull(context.guild(), "no guild was opened");
    }
}
