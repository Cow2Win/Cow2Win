package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ContextBar}: "Original" lineup status, "unsaved" tooltip and the "not live yet" hint - headless, never shown. */
class ContextBarTest {

    @TempDir
    Path guildDir;

    @Test
    @DisplayName("An existing Original lineup is shown with its last-modified date in the short format")
    void originalStatusWithDate() throws Exception {
        Path original = Files.writeString(guildDir.resolve("Original.lineup"), "{}");
        LocalDate date = LocalDate.of(2026, 9, 28);
        Files.setLastModifiedTime(original, FileTime.from(date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));

        String expectedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.GERMANY).format(date);
        assertEquals(LanguageService.displayName("context.original", expectedDate),
                ContextBar.originalStatusText(original, Locale.GERMANY));
        assertTrue(expectedDate.contains("28"), expectedDate);
    }

    @Test
    @DisplayName("Without an Original lineup the status says so")
    void originalStatusMissing() {
        String missing = LanguageService.displayName("context.originalMissing");
        assertEquals(missing, ContextBar.originalStatusText(guildDir.resolve("Original.lineup"), Locale.UK));
        assertEquals(missing, ContextBar.originalStatusText(null, Locale.UK));
    }

    @Test
    @DisplayName("The unsaved hint's tooltip names the guild, the lineup or both - none if nothing is unsaved")
    void unsavedTooltipKey() {
        assertNull(ContextBar.unsavedTooltipKey(false, false));
        assertEquals("context.unsavedTooltip.guild", ContextBar.unsavedTooltipKey(true, false));
        assertEquals("context.unsavedTooltip.lineup", ContextBar.unsavedTooltipKey(false, true));
        assertEquals("context.unsavedTooltip.both", ContextBar.unsavedTooltipKey(true, true));
    }

    @Test
    @DisplayName("\"Not live yet\": hidden without a missing type, otherwise text and tooltip name the missing type")
    void liveIncompleteHint() throws Exception {
        String previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(guildDir.toString());
        AppContext context = new AppContext(new Catalog(guildDir));
        try {
            GuildService guildService = new GuildService(context, RecentFiles.NONE);
            guildService.createGuild("Alpha", false);
            guildService.switchToGuild("Alpha");
            MainActions actions = new MainActions();
            for (ActionId id : ActionId.values()) {
                actions.register(new AppAction(id, () -> { }));
            }
            ContextBar bar = new ContextBar(context, actions);
            JLabel hint = bar.liveIncompleteLabel();
            assertFalse(hint.isVisible());

            bar.setLiveIncomplete(FortificationType.TITAN);
            assertTrue(hint.isVisible());
            assertEquals(LanguageService.displayName("context.liveIncomplete.TITAN"), hint.getText());
            assertEquals(LanguageService.displayName("context.liveIncompleteTooltip.TITAN"), hint.getToolTipText());

            bar.setLiveIncomplete(FortificationType.HERO);
            assertEquals(LanguageService.displayName("context.liveIncomplete.HERO"), hint.getText());
            assertEquals(LanguageService.displayName("context.liveIncompleteTooltip.HERO"), hint.getToolTipText());

            bar.setLiveIncomplete(null);
            assertFalse(hint.isVisible());
        } finally {
            context.journal().closeCurrent();
            Config.setWorkspacePath(previousWorkspace);
        }
    }
}
