package org.c2w.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** {@link SettingsDialog} (never shown): width and the restore/move buttons. Skipped without a display. */
class SettingsDialogTest {

    private SettingsDialog dialog;

    @BeforeEach
    void createDialog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        SwingUtilities.invokeAndWait(() -> dialog = new SettingsDialog(null, new SettingsDialog.AppExit() {
            @Override
            public boolean confirmUnsavedChanges() {
                return false;
            }

            @Override
            public void exit() {
                fail("must not exit");
            }
        }));
    }

    @AfterEach
    void disposeDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
    }

    @Test
    @DisplayName("The dialog is about 1.5 times as wide as packed")
    void widerThanPacked() {
        assertEquals(dialog.packedWidth() * SettingsDialog.SIZE_FACTOR, dialog.getWidth(), 1.0);
    }

    @Test
    @DisplayName("Restore and move buttons exist with a tooltip, right behind the respective \"...\" button, same height")
    void buttonsBehindBrowseButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog.validate());
        assertBehind(dialog.browseBackupDirButton(), dialog.restoreWorkspaceButton());
        assertBehind(dialog.browseWorkspaceDirButton(), dialog.moveWorkspaceButton());
    }

    @Test
    @DisplayName("The directory chooser is 1.5 times its default size")
    void largerDirectoryChooser() {
        Dimension standard = new JFileChooser().getPreferredSize();
        Dimension larger = SettingsDialog.createDirectoryChooser("title").getPreferredSize();
        assertEquals(standard.width * SettingsDialog.SIZE_FACTOR, larger.width, 1.0);
        assertEquals(standard.height * SettingsDialog.SIZE_FACTOR, larger.height, 1.0);
        assertEquals(JFileChooser.DIRECTORIES_ONLY, SettingsDialog.createDirectoryChooser("title").getFileSelectionMode());
    }

    private static void assertBehind(JButton browse, JButton iconButton) {
        assertNotNull(iconButton.getIcon());
        assertNotNull(iconButton.getToolTipText());
        assertSame(browse.getParent(), iconButton.getParent());
        Container parent = browse.getParent();
        assertTrue(parent.getComponentZOrder(iconButton) > parent.getComponentZOrder(browse));
        assertTrue(iconButton.getX() > browse.getX());
        assertEquals(browse.getHeight(), iconButton.getHeight());
    }
}
