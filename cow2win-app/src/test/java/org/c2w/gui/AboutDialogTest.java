package org.c2w.gui;

import org.c2w.i18n.LanguageService;
import org.c2w.infra.AppVersion;
import org.c2w.infra.GitHubProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** {@link AboutDialog} (never shown): version, the link targets and its size. Skipped without a display. */
class AboutDialogTest {

    private AboutDialog dialog;

    @BeforeEach
    void createDialog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        SwingUtilities.invokeAndWait(() -> dialog = new AboutDialog(null));
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
    @DisplayName("Title and version come from the language file and AppVersion; modal, not resizable")
    void titleAndVersion() {
        assertEquals(LanguageService.displayName("about.title"), dialog.getTitle());
        assertEquals(LanguageService.displayName("about.version", AppVersion.current()), dialog.versionLabel().getText());
        assertTrue(dialog.versionLabel().getText().contains(AppVersion.current()));
        assertTrue(dialog.isModal());
        assertFalse(dialog.isResizable());
    }

    @Test
    @DisplayName("The five links in order: contact, project, license, icons, look and feel - each with the URL as tooltip and a hand cursor")
    void links() {
        List<String> targets = dialog.links().stream().map(AboutDialog::linkTarget).toList();
        assertEquals(List.of("mailto:cow2win@gmail.com", GitHubProject.PROJECT_PAGE_URL, GitHubProject.LICENSE_URL,
                "https://phosphoricons.com", "https://github.com/vincenzopalazzo/material-ui-swing"), targets);
        for (JLabel link : dialog.links()) {
            assertEquals(AboutDialog.linkTarget(link), link.getToolTipText());
            assertEquals(Cursor.HAND_CURSOR, link.getCursor().getType());
        }
        assertEquals("https://github.com/Cow2Win/Cow2Win/blob/master/LICENSE", GitHubProject.LICENSE_URL);
    }

    @Test
    @DisplayName("The packed dialog fits on 1024 x 768 (the release runner's screen)")
    void fitsSmallScreen() {
        assertTrue(dialog.getWidth() <= 1024, "width " + dialog.getWidth());
        assertTrue(dialog.getHeight() <= 768 - 40, "height " + dialog.getHeight());
    }
}
