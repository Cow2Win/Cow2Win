package org.c2w.gui.compare;

import org.c2w.data.repository.LineupFiles;
import org.c2w.gui.compare.ComparableLineups.Choice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ComparableLineups}: the lineups of every guild of the workspace, order and preselection. */
class ComparableLineupsTest {

    private static final List<String> FOLDERS = List.of("alpha", "beta", "gamma");

    @TempDir
    Path workspace;

    @BeforeEach
    void createWorkspace() throws IOException {
        guild("alpha", "Deutscher Bund", LineupFiles.ORIGINAL_FILE_NAME, "x.lineup");
        guild("beta", "for fun", "b.lineup", LineupFiles.ORIGINAL_FILE_NAME, "A.lineup");
        guild("gamma", "", "z.lineup");
    }

    @Test
    @DisplayName("Lineups of all guilds, current guild first, Live first, without the open file")
    void listsAllGuildsInOrder() {
        List<Choice> choices = ComparableLineups.list(workspace, FOLDERS, "beta", workspace.resolve("beta/b.lineup"));

        assertEquals(List.of("beta/Original.lineup", "beta/A.lineup",
                        "alpha/Original.lineup", "alpha/x.lineup", "gamma/z.lineup"),
                choices.stream().map(c -> c.guildFolder() + "/" + c.fileName()).toList());
        assertEquals("for fun", choices.get(0).guildName());
        assertEquals("Deutscher Bund", choices.get(2).guildName());
        assertEquals("gamma", choices.get(4).guildName(), "a guild without a name shows its folder name");
    }

    @Test
    @DisplayName("Live of the current guild is preselected")
    void preselectsLiveOfTheCurrentGuild() {
        List<Choice> choices = ComparableLineups.list(workspace, FOLDERS, "alpha", workspace.resolve("alpha/x.lineup"));

        Choice preselected = ComparableLineups.preselect(choices, "alpha");

        assertEquals("alpha", preselected.guildFolder());
        assertTrue(preselected.isOriginal());
    }

    @Test
    @DisplayName("If Live is open on the left, the first entry is preselected")
    void preselectsTheFirstEntryIfLiveIsOpen() {
        List<Choice> choices = ComparableLineups.list(workspace, FOLDERS, "beta",
                workspace.resolve("beta").resolve(LineupFiles.ORIGINAL_FILE_NAME));

        Choice preselected = ComparableLineups.preselect(choices, "beta");

        assertEquals("beta", preselected.guildFolder());
        assertEquals("A.lineup", preselected.fileName());
        assertTrue(choices.stream().noneMatch(c -> c.guildFolder().equals("beta") && c.isOriginal()));
    }

    @Test
    @DisplayName("Rebuilding keeps the selected lineup, unless it is now open on the left")
    void reselectKeepsTheSelection() {
        List<Choice> before = ComparableLineups.list(workspace, FOLDERS, "beta", workspace.resolve("beta/b.lineup"));
        Choice selected = before.get(3);

        List<Choice> after = ComparableLineups.list(workspace, FOLDERS, "beta", workspace.resolve("beta/A.lineup"));
        assertEquals(selected, ComparableLineups.reselect(after, selected, "beta"));

        List<Choice> nowOpen = ComparableLineups.list(workspace, FOLDERS, "alpha", selected.lineupPath());
        Choice reselected = ComparableLineups.reselect(nowOpen, selected, "alpha");
        assertTrue(reselected.isOriginal());
        assertEquals("alpha", reselected.guildFolder());
    }

    @Test
    @DisplayName("No other lineup: empty list, no preselection")
    void emptyWorkspace() {
        List<Choice> choices = ComparableLineups.list(workspace, List.of("gamma"), "gamma", workspace.resolve("gamma/z.lineup"));

        assertTrue(choices.isEmpty());
        assertNull(ComparableLineups.preselect(choices, "gamma"));
    }

    private void guild(String folder, String name, String... lineupFiles) throws IOException {
        Path dir = Files.createDirectories(workspace.resolve(folder));
        Files.writeString(dir.resolve("guild.json"), "{\"id\":\"" + folder + "\",\"name\":\"" + name + "\"}");
        for (String file : lineupFiles) {
            Files.writeString(dir.resolve(file), "{}");
        }
    }
}
