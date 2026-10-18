package org.c2w.gui.guild;

import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildNoteRepository;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildLog;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** {@link GuildNoteDialog} with a real {@link AppContext} and a workspace of two guilds. */
class GuildNoteDialogTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private GuildNoteDialog dialog;

    @BeforeEach
    void openWorkspace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.createGuild("Beta", false);
        guildService.switchToGuild("Alpha");
    }

    @AfterEach
    void closeWorkspace() throws Exception {
        if (context == null) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            if (GuildNoteDialog.openInstance() != null) {
                GuildNoteDialog.openInstance().dispose();
            }
        });
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    private Path guildDir(String name) {
        return workspace.resolve(name);
    }

    private void openDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = GuildNoteDialog.open(null, context));
    }

    private void setText(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog.textArea().setText(text));
    }

    @Test
    @DisplayName("Opening shows the existing note; a second open returns the same dialog")
    void opensOnceWithNote() throws Exception {
        GuildNoteRepository.write(guildDir("Alpha"), "Alpha note");
        openDialog();

        assertTrue(dialog.isShowing());
        assertEquals("Alpha note", dialog.textArea().getText());
        assertEquals(LanguageService.displayName("guildNote.title", "Alpha"), dialog.getTitle());
        assertFalse(dialog.hasUnsavedChanges());

        GuildNoteDialog[] second = new GuildNoteDialog[1];
        SwingUtilities.invokeAndWait(() -> second[0] = GuildNoteDialog.open(null, context));
        assertSame(dialog, second[0]);
    }

    @Test
    @DisplayName("Changing the text makes it unsaved; saving writes the file, clears the state and logs it")
    void saveWritesFileAndLogs() throws Exception {
        openDialog();
        String unsavedSuffix = LanguageService.displayName("toolbar.unsavedSuffix");
        assertFalse(dialog.saveButton().getToolTipText().contains(unsavedSuffix));

        setText("Regeln für die Saison");
        assertTrue(dialog.hasUnsavedChanges());
        assertTrue(dialog.saveButton().getToolTipText().contains(unsavedSuffix));

        AtomicBoolean saved = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> saved.set(dialog.save()));
        assertTrue(saved.get());
        assertFalse(dialog.hasUnsavedChanges());
        assertFalse(dialog.saveButton().getToolTipText().contains(unsavedSuffix));
        assertEquals("Regeln für die Saison", GuildNoteRepository.read(guildDir("Alpha")));
        String log = Files.readString(guildDir("Alpha").resolve(GuildLog.FILE_NAME), StandardCharsets.UTF_8);
        assertTrue(log.contains(LanguageService.displayName("guildLog.guildNoteSaved")), log);

        setText("");
        SwingUtilities.invokeAndWait(() -> dialog.save());
        assertFalse(Files.exists(GuildNoteRepository.fileOf(guildDir("Alpha"))));
        log = Files.readString(guildDir("Alpha").resolve(GuildLog.FILE_NAME), StandardCharsets.UTF_8);
        assertTrue(log.contains(LanguageService.displayName("guildLog.guildNoteCleared")), log);
    }

    @Test
    @DisplayName("Changing the text and changing it back is not unsaved")
    void revertedTextIsNotUnsaved() throws Exception {
        GuildNoteRepository.write(guildDir("Alpha"), "original");
        openDialog();

        setText("original plus");
        assertTrue(dialog.hasUnsavedChanges());
        setText("original");
        assertFalse(dialog.hasUnsavedChanges());
    }

    @Test
    @DisplayName("A guild switch without changes shows the new guild's note and title")
    void followsGuildSwitch() throws Exception {
        GuildNoteRepository.write(guildDir("Alpha"), "Alpha note");
        GuildNoteRepository.write(guildDir("Beta"), "Beta note");
        openDialog();

        SwingUtilities.invokeAndWait(() -> {
            try {
                guildService.switchToGuild("Beta");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertSame(dialog, GuildNoteDialog.openInstance());
        assertEquals("Beta note", dialog.textArea().getText());
        assertEquals(LanguageService.displayName("guildNote.title", "Beta"), dialog.getTitle());
        assertTrue(GuildLog.sameDir(guildDir("Beta"), dialog.guildDir()));
    }

    @Test
    @DisplayName("confirmLeave with unsaved changes: discard allows leaving and keeps the file, cancel does not")
    void confirmLeave() throws Exception {
        assertTrue(GuildNoteDialog.confirmLeave(() -> fail("no dialog open, no question")));

        GuildNoteRepository.write(guildDir("Alpha"), "kept");
        openDialog();
        assertTrue(GuildNoteDialog.confirmLeave(() -> fail("nothing unsaved, no question")));

        setText("changed");
        AtomicBoolean result = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> result.set(GuildNoteDialog.confirmLeave(() -> JOptionPane.CANCEL_OPTION)));
        assertFalse(result.get());
        SwingUtilities.invokeAndWait(() -> result.set(GuildNoteDialog.confirmLeave(() -> JOptionPane.NO_OPTION)));
        assertTrue(result.get());
        assertEquals("kept", GuildNoteRepository.read(guildDir("Alpha")));
    }
}
