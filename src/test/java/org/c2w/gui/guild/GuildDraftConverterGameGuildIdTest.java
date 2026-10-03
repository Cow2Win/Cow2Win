package org.c2w.gui.guild;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Editing a guild in the dialog (guild -> draft -> guild) must not lose its game guild id. */
class GuildDraftConverterGameGuildIdTest {

    @Test
    @DisplayName("gameGuildId survives fromGuild -> toGuild, also after editing the name")
    void roundTrip() {
        Guild guild = new Guild("g1", "Guild", List.of(new GuildMember("m1", "Member", List.of(), List.of())), 193861L);

        GuildDraft draft = GuildDraftConverter.fromGuild(guild);
        draft.name = "Renamed";
        Guild back = GuildDraftConverter.toGuild(draft);

        assertEquals(193861L, back.gameGuildId());
        assertEquals("Renamed", back.name());
    }

    @Test
    @DisplayName("a guild without gameGuildId stays without")
    void withoutGameGuildId() {
        Guild back = GuildDraftConverter.toGuild(GuildDraftConverter.fromGuild(new Guild("g1", "Guild", List.of())));

        assertNull(back.gameGuildId());
    }
}
