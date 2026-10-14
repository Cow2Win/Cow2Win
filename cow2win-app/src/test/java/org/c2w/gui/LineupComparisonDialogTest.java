package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.compare.ComparableLineups.Choice;
import org.c2w.i18n.LanguageService;
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
 * {@link LineupComparisonDialog} with a real {@link AppContext} and a workspace of two guilds -
 * regression test for the dialog that never appeared (an exception in its constructor).
 */
class LineupComparisonDialogTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private LineupComparisonDialog dialog;

    @BeforeEach
    void openWorkspace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
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
            if (dialog != null) {
                dialog.dispose();
            }
        });
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("The dialog opens without an exception; a second open brings the same one to front")
    void opensOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = LineupComparisonDialog.open(null, context));
        assertTrue(dialog.isShowing());

        LineupComparisonDialog[] second = new LineupComparisonDialog[1];
        SwingUtilities.invokeAndWait(() -> second[0] = LineupComparisonDialog.open(null, context));
        assertSame(dialog, second[0]);

        SwingUtilities.invokeAndWait(() -> dialog.dispose());
        assertNull(LineupComparisonDialog.openInstance());
        dialog = null;
    }

    @Test
    @DisplayName("Lineups of the other guild are offered, the open one is not; choosing one shows the hint")
    void comparesWithAnotherGuild() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = LineupComparisonDialog.open(null, context));

        List<Choice> choices = dialog.choices();
        assertTrue(choices.stream().noneMatch(c -> c.isAt(context.lineupFilePath())), "the open lineup is on the left");
        Choice beta = choices.stream().filter(c -> c.guildName().equals("Beta")).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> dialog.selectChoiceForTest(beta));
        assertEquals(beta, dialog.selectedChoice());
        assertEquals(LanguageService.displayName("lineupComparison.otherGuildHint"), dialog.hintText());
        assertTrue(dialog.powerChart().isRightAvailable());
    }

    @Test
    @DisplayName("Switching the type shows only its fortifications; unsaved changes get a star")
    void typeAndDirtyState() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = LineupComparisonDialog.open(null, context));
        FortificationType other = context.fortificationType() == FortificationType.HERO
                ? FortificationType.TITAN : FortificationType.HERO;

        SwingUtilities.invokeAndWait(() -> dialog.selectTypeForTest(other));
        long expected = FortificationRepository.findAll().stream().filter(f -> f.type() == other).count();
        assertEquals(expected, dialog.powerChart().rows().size());
        assertEquals(expected, dialog.buffChart().rows().size());

        assertFalse(dialog.currentLabelText().endsWith(" *"));
        SwingUtilities.invokeAndWait(() -> context.setLineupDirty(true));
        assertTrue(dialog.currentLabelText().endsWith(" *"));
    }
}
