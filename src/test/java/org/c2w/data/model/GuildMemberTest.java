package org.c2w.data.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation rules of {@link GuildMember} around a hero team's optional
 * {@link Pet} and {@link WarFlag}: each can be fielded in at most one hero
 * team per member.
 */
class GuildMemberTest {

    private static HeroTeam team(int index, Pet pet, WarFlag warFlag) {
        return new HeroTeam("m1", index, List.of(), pet, warFlag, 1000, null);
    }

    @Test
    void acceptsTeamsWithoutPetOrWarFlag() {
        GuildMember member = new GuildMember("m1", "Member", List.of(team(0, null, null), team(1, null, null)), null);
        assertNull(member.heroTeams().get(0).pet());
        assertNull(member.heroTeams().get(0).warFlag());
    }

    @Test
    void acceptsDifferentPetsAndWarFlagsAcrossTeams() {
        assertDoesNotThrow(() -> new GuildMember("m1", "Member", List.of(
                team(0, new Pet("albus"), new WarFlag("flag-bastion")),
                team(1, new Pet("axel"), new WarFlag("flag-frost")),
                team(2, null, null)), null));
    }

    @Test
    void rejectsSamePetInTwoTeams() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new GuildMember("m1", "Member", List.of(
                        team(0, new Pet("albus"), null),
                        team(1, new Pet("albus"), null)), null));
        assertTrue(e.getMessage().contains("albus"));
    }

    @Test
    void rejectsSameWarFlagInTwoTeams() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new GuildMember("m1", "Member", List.of(
                        team(0, null, new WarFlag("flag-frost")),
                        team(1, null, new WarFlag("flag-frost"))), null));
        assertTrue(e.getMessage().contains("flag-frost"));
    }

    @Test
    void convenienceConstructorsLeavePetAndWarFlagEmpty() {
        HeroTeam team = new HeroTeam("m1", 0, List.of(), 1000);
        assertNull(team.pet());
        assertNull(team.warFlag());
    }

    @Test
    void displayNameIsNameOrId() {
        assertEquals("Puschel", new GuildMember("m1", "Puschel", List.of(), List.of()).displayName());
        assertEquals("m1", new GuildMember("m1", "", List.of(), List.of()).displayName());
        assertEquals("m1", new GuildMember("m1", "  ", List.of(), List.of()).displayName());
        assertEquals("m1", new GuildMember("m1", null, List.of(), List.of()).displayName());
    }
}
