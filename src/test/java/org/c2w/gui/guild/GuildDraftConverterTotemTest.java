package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.c2w.data.model.TitanElement.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A titan team's totems must survive the guild -> draft -> guild round trip
 * every editing dialog goes through, and {@link TeamDraft#copyFrom} must
 * carry them along.
 */
class GuildDraftConverterTotemTest {

    private static final List<Titan> TITANS = List.of(
            new Titan("f1", FIRE), new Titan("f2", FIRE), new Titan("w1", WATER), new Titan("w2", WATER));

    private static Guild guildWith(TitanTeam... titanTeams) {
        GuildMember member = new GuildMember("m1", "Member", List.of(), List.of(titanTeams));
        return new Guild("g1", "Guild", List.of(member), 1, LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("totems survive fromGuild -> toGuild")
    void roundTrip() {
        Guild guild = guildWith(
                new TitanTeam("m1", 0, TITANS, 1000, null, Set.of(FIRE, WATER)),
                new TitanTeam("m1", 1, TITANS, 900, null));

        GuildDraft draft = GuildDraftConverter.fromGuild(guild);
        assertEquals(Set.of(FIRE, WATER), draft.members.get(0).titanTeams.get(0).totems);
        assertEquals(Set.of(), draft.members.get(0).titanTeams.get(1).totems);

        List<TitanTeam> teams = GuildDraftConverter.toGuild(draft).members().get(0).titanTeams();
        assertEquals(Set.of(FIRE, WATER), teams.get(0).totems());
        assertEquals(Set.of(), teams.get(1).totems());
    }

    @Test
    @DisplayName("toGuild drops a totem the draft's titans no longer allow instead of failing")
    void toGuildDropsInvalidTotems() {
        GuildDraft draft = GuildDraftConverter.fromGuild(guildWith(new TitanTeam("m1", 0, TITANS, 1000, null,
                Set.of(FIRE, WATER))));
        TeamDraft<Titan> teamDraft = draft.members.get(0).titanTeams.get(0);
        teamDraft.members.remove(0); // only one fire titan left

        assertEquals(Set.of(WATER), GuildDraftConverter.toGuild(draft).members().get(0).titanTeams().get(0).totems());
    }

    @Test
    @DisplayName("copyFrom copies the totems")
    void copyFromCopiesTotems() {
        TeamDraft<Titan> source = new TeamDraft<>();
        source.members.addAll(TITANS);
        source.totems.add(FIRE);

        TeamDraft<Titan> target = new TeamDraft<>();
        target.totems.add(WATER);
        target.copyFrom(source);

        assertEquals(Set.of(FIRE), target.totems);
        source.totems.add(WATER);
        assertEquals(Set.of(FIRE), target.totems, "a copy, not the same set");
    }
}
