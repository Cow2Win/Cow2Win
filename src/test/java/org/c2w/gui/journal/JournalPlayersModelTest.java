package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link JournalPlayersModel}: rows, filters and member hints; the guild is never changed. */
class JournalPlayersModelTest extends JournalGuiTestSupport {

    @Test
    @DisplayName("Without a journal: empty, no hints, and no journal file is created")
    void withoutJournal() throws Exception {
        JournalPlayersModel.Data data = JournalPlayersModel.load(context.journal().repository(false));
        JournalPlayersModel model = new JournalPlayersModel(data, context.guild());

        assertFalse(data.hasJournal());
        assertEquals(List.of(), model.rows());
        assertEquals(List.of(), model.memberHints());
        assertFalse(JournalDatabase.exists(guildService.guildDir("Alpha")));
    }

    @Test
    @DisplayName("Real import: statistics, assignment problems, filters, hints - the guild stays unchanged")
    void realImport() throws Exception {
        setMembers(List.of(member("Puschel"), member("team gandagom"), member("Ordensriter"), member("Ghost")));
        importWithDefaults(prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE));
        guildService.saveGuild();
        Guild guildBefore = context.guild();
        String fileBefore = Files.readString(context.guildFilePath());
        JournalRepository repo = context.journal().repository(false).orElseThrow();
        // one player assigned to a member id the guild does not have (e.g. deleted in the guild editor)
        JournalPlayer orphan = repo.listOwnPlayerStats().stream()
                .filter(s -> s.status() == AssignmentStatus.OPEN && s.defenses() > 0).findFirst().orElseThrow().player();
        repo.setAssignment(orphan.id(), "deleted-member", AssignmentStatus.ASSIGNED);

        JournalPlayersModel model = new JournalPlayersModel(JournalPlayersModel.load(Optional.of(repo)), context.guild());

        JournalPlayersModel.Row puschel = row(model, "Puschel");
        assertEquals(AssignmentStatus.ASSIGNED, puschel.status());
        assertEquals("Puschel", puschel.memberName());
        assertFalse(puschel.hasProblem());
        assertEquals(LocalDate.of(2026, 9, 24), puschel.stats().lastSeen());
        long puschelDefenses = BattleLogTestFiles.parse("de", SEP_24, LogDirection.DEFENSE).log().fights().stream()
                .filter(f -> f.defender().playerName().equals("Puschel")).count();
        assertEquals(puschelDefenses, puschel.stats().defenses());
        assertFalse(puschel.stats().lastTeamPowers().isEmpty());

        JournalPlayersModel.Row orphanRow = row(model, orphan.name());
        assertTrue(orphanRow.memberMissing());
        assertNull(orphanRow.memberName());
        assertTrue(orphanRow.hasProblem());

        int all = model.rows().size();
        model.setOnlyOpen(true);
        assertTrue(model.rows().stream().allMatch(r -> r.status() == AssignmentStatus.OPEN));
        assertFalse(model.rows().contains(orphanRow));
        model.setOnlyOpen(false);
        model.setOnlyProblems(true);
        assertTrue(model.rows().contains(orphanRow));
        assertFalse(model.rows().contains(puschel));
        assertTrue(model.rows().size() < all);
        model.setOnlyProblems(false);
        assertEquals(all, model.rows().size());

        List<JournalPlayersModel.MemberHint> hints = model.memberHints();
        assertTrue(hints.contains(new JournalPlayersModel.MemberHint(member("Ghost"), JournalPlayersModel.HintKind.NO_PLAYER)),
                hints.toString());
        assertTrue(hints.stream().noneMatch(h -> h.member().id().equals("Puschel")));

        assertEquals(guildBefore, context.guild());
        assertEquals(fileBefore, Files.readString(context.guildFilePath()));
    }

    @Test
    @DisplayName("Hints: no player, not in the latest defense logs; several names of one member")
    void hintsAndFormerNames() {
        GuildMember anna = member("anna");
        GuildMember bert = member("bert");
        GuildMember carl = member("carl");
        Guild guild = new Guild("g", "G", List.of(anna, bert, carl));
        OwnPlayerStats annaOld = stats(1, "Anna_alt", "anna");
        OwnPlayerStats annaNew = stats(2, "Anna", "anna");
        OwnPlayerStats bertPlayer = stats(3, "Bert", "bert");
        OwnPlayerStats open = stats(4, "Unbekannt", null);

        JournalPlayersModel model = new JournalPlayersModel(new JournalPlayersModel.Data(true,
                List.of(annaOld, annaNew, bertPlayer, open), Set.of(2, 4), 3), guild);

        assertEquals(List.of(
                new JournalPlayersModel.MemberHint(carl, JournalPlayersModel.HintKind.NO_PLAYER),
                new JournalPlayersModel.MemberHint(bert, JournalPlayersModel.HintKind.NOT_IN_RECENT_DEFENSES)),
                model.memberHints(), "anna is in the latest logs under her new name");
        assertEquals(2, row(model, "Anna_alt").sameMember());
        assertEquals(1, row(model, "Bert").sameMember());
        assertEquals(0, row(model, "Unbekannt").sameMember());

        JournalPlayersModel noDefenseLogs = new JournalPlayersModel(new JournalPlayersModel.Data(true,
                List.of(bertPlayer), Set.of(), 0), guild);
        assertTrue(noDefenseLogs.memberHints().stream()
                        .noneMatch(h -> h.kind() == JournalPlayersModel.HintKind.NOT_IN_RECENT_DEFENSES),
                "without any defense log nobody is missing from it");
        assertEquals(List.of("anna", "bert", "carl"), model.members().stream().map(GuildMember::id).toList());
    }

    private static OwnPlayerStats stats(int id, String name, String memberId) {
        PlayerAssignment assignment = memberId == null ? null
                : new PlayerAssignment(id, memberId, AssignmentStatus.ASSIGNED, null);
        return new OwnPlayerStats(new JournalPlayer(id, name, 1, true), assignment, 1, LocalDate.of(2026, 9, 24),
                List.of(1_000_000));
    }

    private static JournalPlayersModel.Row row(JournalPlayersModel model, String name) {
        return model.allRows().stream().filter(r -> r.stats().player().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Members without a name are sorted by their id")
    void membersWithoutNameSortedById() throws Exception {
        setMembers(List.of(new GuildMember("zeta", "Zeta", List.of(), List.of()),
                new GuildMember("beta", "", List.of(), List.of()),
                new GuildMember("delta", null, List.of(), List.of()),
                new GuildMember("x", "Alpha", List.of(), List.of())));
        JournalPlayersModel.Data data = JournalPlayersModel.load(context.journal().repository(false));
        JournalPlayersModel model = new JournalPlayersModel(data, context.guild());

        assertEquals(List.of("Alpha", "beta", "delta", "Zeta"),
                model.members().stream().map(GuildMember::displayName).toList());
    }
}
