package org.c2w.gui;

import org.c2w.data.model.Guild;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildRepository;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The content of the "New guild" dialog ({@link NewGuildForm}) and its name check - headless, never shown. */
class NewGuildFormTest {

    private static final String TWENTY = "abcdefghijklmnopqrst";

    private final NewGuildForm form = new NewGuildForm();

    @Test
    @DisplayName("the name field takes at most 20 characters - typed or pasted, longer text is cut off")
    void maxLength() {
        form.nameField().setText(TWENTY + "uvw");
        assertEquals(TWENTY, form.nameField().getText());
        assertEquals(LanguageService.displayName("mainFrame.newGuild.nameCounter", 20, 20), form.counterLabel().getText());

        form.nameField().setText("Deutscher ");
        form.nameField().setCaretPosition(form.nameField().getText().length());
        form.nameField().replaceSelection("Bund und noch viel mehr");
        assertEquals("Deutscher Bund und n", form.nameField().getText(), "pasting fills up to 20 characters");

        form.nameField().selectAll();
        form.nameField().replaceSelection(TWENTY + TWENTY);
        assertEquals(TWENTY, form.nameField().getText(), "replacing a selection may use its room");
    }

    @Test
    @DisplayName("OK only with a non-blank name; the input is trimmed")
    void canConfirm() {
        int[] changes = {0};
        form.addChangeListener(() -> changes[0]++);
        assertFalse(form.canConfirm());
        assertEquals(LanguageService.displayName("mainFrame.newGuild.nameCounter", 0, 20), form.counterLabel().getText());

        form.nameField().setText("   ");
        assertFalse(form.canConfirm());

        form.nameField().setText("  Alpha ");
        assertTrue(form.canConfirm());
        assertEquals("Alpha", form.input().name());
        assertTrue(changes[0] > 0, "listeners hear about name changes");
    }

    @Test
    @DisplayName("the guild master check box is off by default and ends up in the input")
    void guildMasterCheckBox() {
        assertFalse(form.guildMasterCheckBox().isSelected());
        assertEquals(LanguageService.displayName("mainFrame.newGuild.guildMaster"), form.guildMasterCheckBox().getText());
        form.nameField().setText("Alpha");

        form.guildMasterCheckBox().setSelected(true);

        assertEquals(new NewGuildForm.Input("Alpha", true), form.input());
    }

    @Test
    @DisplayName("name check: empty, too long, illegal characters, existing folder - otherwise fine")
    void problem() {
        Set<String> existing = Set.of("Alpha");

        assertEquals(LanguageService.displayName("common.enterName"), NewGuildDialog.problem("", existing::contains));
        assertEquals(LanguageService.displayName("mainFrame.newGuild.nameTooLong", Guild.MAX_NAME_LENGTH),
                NewGuildDialog.problem(TWENTY + "x", existing::contains));
        assertEquals(LanguageService.displayName("common.illegalFilenameChars", ActionBar.ILLEGAL_FILENAME_CHARS),
                NewGuildDialog.problem("a/b", existing::contains));
        assertEquals(LanguageService.displayName("mainFrame.newGuild.alreadyExists", "Alpha"),
                NewGuildDialog.problem("Alpha", existing::contains));
        assertNull(NewGuildDialog.problem(TWENTY, existing::contains));
    }

    @Test
    @DisplayName("the check box value lands in the created guild")
    void createdGuildGetsTheFlag(@TempDir Path workspace) throws Exception {
        String previousWorkspace = Config.getWorkspacePath();
        try {
            Config.setWorkspacePath(workspace.toString());
            GuildService guildService = new GuildService(new AppContext(new Catalog(workspace)), RecentFiles.NONE);
            form.nameField().setText("Deutscher Bund");
            form.guildMasterCheckBox().setSelected(true);

            NewGuildForm.Input input = form.input();
            assertNull(NewGuildDialog.problem(input.name(), guildService::guildExists));
            guildService.createGuild(input.name(), input.guildMaster());

            Guild created = GuildRepository.load(workspace.resolve("Deutscher Bund").resolve(GuildService.GUILD_FILE_NAME),
                    new Catalog(workspace));
            assertTrue(created.guildMaster());
            assertEquals("Deutscher Bund", created.name());
        } finally {
            Config.setWorkspacePath(previousWorkspace);
        }
    }
}
