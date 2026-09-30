package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A hero team's optional {@link Pet}/{@link WarFlag} must survive the
 * guild -> draft -> guild round trip every editing dialog goes through,
 * and {@link TeamDraft#copyFrom} must carry them along.
 */
class GuildDraftConverterPetWarFlagTest {

    private static Guild guildWith(HeroTeam... heroTeams) {
        GuildMember member = new GuildMember("m1", "Member", List.of(heroTeams), List.of());
        return new Guild("g1", "Guild", List.of(member), 1, LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("pet and war flag survive fromGuild -> toGuild")
    void roundTrip() {
        Guild guild = guildWith(
                new HeroTeam("m1", 0, List.of(), new Pet("albus"), new WarFlag("flag-frost"), 1000, null),
                new HeroTeam("m1", 1, List.of(), null, null, 900, null));

        Guild roundTripped = GuildDraftConverter.toGuild(GuildDraftConverter.fromGuild(guild));

        List<HeroTeam> teams = roundTripped.members().get(0).heroTeams();
        assertEquals("albus", teams.get(0).pet().id());
        assertEquals("flag-frost", teams.get(0).warFlag().id());
        assertNull(teams.get(1).pet());
        assertNull(teams.get(1).warFlag());
    }

    @Test
    @DisplayName("copyFrom copies every field, including pet and war flag")
    void copyFromCopiesEverything() {
        TeamDraft<Hero> source = new TeamDraft<>();
        source.members.add(new Hero("h1", List.of(Role.TANK)));
        source.totalPower = 1234;
        source.lastModified = LocalDate.of(2026, 9, 29);
        source.pet = new Pet("albus");
        source.warFlag = new WarFlag("flag-frost");

        TeamDraft<Hero> target = new TeamDraft<>();
        target.members.add(new Hero("old", List.of(Role.TANK)));
        target.copyFrom(source);

        assertEquals(source.members, target.members);
        assertEquals(1234, target.totalPower);
        assertEquals(source.lastModified, target.lastModified);
        assertSame(source.pet, target.pet);
        assertSame(source.warFlag, target.warFlag);
    }

    @Test
    @DisplayName("a pet/war flag used twice by one member is dropped from the later team on toGuild instead of failing")
    void duplicatesAreDroppedOnSave() {
        GuildDraft draft = GuildDraftConverter.fromGuild(guildWith(
                new HeroTeam("m1", 0, List.of(), new Pet("albus"), new WarFlag("flag-frost"), 1000, null),
                new HeroTeam("m1", 1, List.of(), null, null, 900, null)));
        TeamDraft<Hero> second = draft.members.get(0).heroTeams.get(1);
        second.pet = new Pet("albus");
        second.warFlag = new WarFlag("flag-frost");

        List<HeroTeam> teams = GuildDraftConverter.toGuild(draft).members().get(0).heroTeams();
        assertEquals("albus", teams.get(0).pet().id());
        assertEquals("flag-frost", teams.get(0).warFlag().id());
        assertNull(teams.get(1).pet());
        assertNull(teams.get(1).warFlag());
    }

    @Test
    @DisplayName("findPetWarFlagConflict reports a pet/war flag used twice by one member, ignores empty teams")
    void findsConflicts() {
        GuildDraft draft = GuildDraftConverter.fromGuild(guildWith(
                new HeroTeam("m1", 0, List.of(), new Pet("albus"), new WarFlag("flag-frost"), 1000, null),
                new HeroTeam("m1", 1, List.of(), null, null, 900, null)));
        assertTrue(GuildDraftConverter.findPetWarFlagConflict(draft).isEmpty());

        TeamDraft<Hero> second = draft.members.get(0).heroTeams.get(1);
        second.warFlag = new WarFlag("flag-frost");
        GuildDraftConverter.PetWarFlagConflict conflict = GuildDraftConverter.findPetWarFlagConflict(draft).orElseThrow();
        assertEquals("m1", conflict.member().id);
        assertEquals("flag-frost", conflict.itemId());

        second.totalPower = 0; // an empty team is dropped on save, so it can't conflict
        assertTrue(GuildDraftConverter.findPetWarFlagConflict(draft).isEmpty());
    }
}
