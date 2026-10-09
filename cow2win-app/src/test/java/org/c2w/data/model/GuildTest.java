package org.c2w.data.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Guild#displayName()} and the guild master flag. */
class GuildTest {

    @Test
    @DisplayName("displayName: the name if set, otherwise the id")
    void displayName() {
        assertEquals("Testgilde", new Guild("testgilde", "Testgilde", List.of()).displayName());
        assertEquals("testgilde", new Guild("testgilde", "", List.of()).displayName());
        assertEquals("testgilde", new Guild("testgilde", "  ", List.of()).displayName());
        assertEquals("testgilde", new Guild("testgilde", null, List.of()).displayName());
    }

    @Test
    @DisplayName("guildMaster: false by default, kept by withMembers and withGameGuildId")
    void guildMaster() {
        assertFalse(new Guild("g", "G", List.of()).guildMaster());
        assertFalse(new Guild("g", "G", List.of(), 42L).guildMaster());

        Guild master = new Guild("g", "G", List.of(), 42L, true);
        Guild withMembers = master.withMembers(List.of(new GuildMember("m1", "Member", List.of(), List.of())));
        assertTrue(withMembers.guildMaster());
        assertEquals(42L, withMembers.gameGuildId());
        assertTrue(master.withGameGuildId(7L).guildMaster());
    }

    @Test
    @DisplayName("the name length limit only applies when creating a guild - a longer name is still a valid guild")
    void longNameStillValid() {
        String name = "A guild name that is clearly longer than twenty characters";
        assertTrue(name.length() > Guild.MAX_NAME_LENGTH);
        assertEquals(name, new Guild("g", name, List.of()).name());
    }
}
