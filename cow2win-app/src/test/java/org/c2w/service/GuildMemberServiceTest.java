package org.c2w.service;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.JournalPlayer;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanTeam;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.journal.ImportAnswers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GuildMemberService}: adding and deleting members in a temporary workspace. */
class GuildMemberServiceTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private GuildMemberService service;
    private Path guildDir;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
        guildDir = context.guildFilePath().getParent();
        service = new GuildMemberService(context, guildService);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    // --- add ---

    @Test
    @DisplayName("Add: id = trimmed name, saved right away, guild log entry")
    void addMember() throws IOException {
        GuildMember added = service.addMember("  Bob ");

        assertEquals("Bob", added.id());
        assertEquals("Bob", added.name());
        assertFalse(context.isGuildDirty());
        Guild saved = GuildRepository.load(context.guildFilePath(), context.catalog());
        assertTrue(saved.members().stream().anyMatch(m -> m.id().equals("Bob")));
        String logged = LanguageService.displayName("guildLog.memberAdded", "Bob");
        assertTrue(GuildLog.read(guildDir).stream().anyMatch(line -> line.contains(logged)), GuildLog.read(guildDir).toString());
    }

    @Test
    @DisplayName("Add: a taken id or display name (ignoring case), a blank name and a full guild are refused")
    void addRefused() throws IOException {
        setMembers(List.of(new GuildMember("anna-id", "Anna", List.of(), List.of())));

        assertTrue(service.isTaken("ANNA"));
        assertTrue(service.isTaken("Anna-Id"));
        assertThrows(IllegalArgumentException.class, () -> service.addMember("anna"));
        assertThrows(IllegalArgumentException.class, () -> service.addMember("  "));

        List<GuildMember> full = new ArrayList<>();
        for (int i = 1; i <= Guild.MAX_MEMBERS; i++) {
            full.add(new GuildMember("m" + i, "M" + i, List.of(), List.of()));
        }
        setMembers(full);
        assertFalse(service.canAddMember());
        assertThrows(IllegalArgumentException.class, () -> service.addMember("New"));
    }

    // --- impact ---

    @Test
    @DisplayName("Impact: teams per type and entries over every lineup, Live included")
    void impact() throws IOException {
        setUpMembersAndLineups();

        GuildMemberService.RemovalImpact impact = service.impactOf("anna");

        assertEquals("Anna", impact.displayName());
        assertEquals(2, impact.heroTeams());
        assertEquals(1, impact.titanTeams());
        assertEquals(3, impact.entries());
        assertEquals(2, impact.lineups());
    }

    // --- remove ---

    @Test
    @DisplayName("Delete: guild saved without the member, its entries gone from every lineup, other entries kept, unchanged files not written")
    void removeCleansLineups() throws IOException {
        setUpMembersAndLineups();
        Path untouched = guildDir.resolve("bert-only" + LineupFiles.SUFFIX);
        LineupRepository.save(lineup(new Lineup.Entry("bastion", "bert", Lineup.TeamType.HERO, 0)), untouched);
        FileTime old = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));
        Files.setLastModifiedTime(untouched, old);

        GuildMemberService.RemovalResult result = service.removeMember("anna");

        assertEquals(List.of(), result.failures());
        assertEquals(3, result.removedEntries());
        assertEquals(2, result.changedLineups());
        Guild saved = GuildRepository.load(context.guildFilePath(), context.catalog());
        assertEquals(List.of("bert"), saved.members().stream().map(GuildMember::id).toList());
        for (String name : List.of(LineupFiles.ORIGINAL_FILE_NAME, "test" + LineupFiles.SUFFIX)) {
            List<Lineup.Entry> entries = LineupRepository.load(guildDir.resolve(name)).entries();
            assertTrue(entries.stream().noneMatch(e -> e.teamMemberId().equals("anna")), name);
            assertTrue(entries.stream().anyMatch(e -> e.teamMemberId().equals("bert")), name);
        }
        assertEquals(old, Files.getLastModifiedTime(untouched), "a lineup without the member is not rewritten");
        String logged = LanguageService.displayName("guildLog.memberRemoved", "Anna");
        assertTrue(GuildLog.read(guildDir).stream().anyMatch(line -> line.contains(logged)));
    }

    @Test
    @DisplayName("Delete: the open lineup loses the entries too - saved stays saved, unsaved stays unsaved")
    void removeCleansOpenLineup() throws IOException {
        setUpMembersAndLineups();
        Path live = LineupFiles.originalPathFor(guildDir);
        context.set(LineupRepository.load(live), live);
        context.setLineupDirty(false);

        service.removeMember("anna");

        assertTrue(context.lineup().entries().stream().noneMatch(e -> e.teamMemberId().equals("anna")));
        assertFalse(context.isLineupDirty());
    }

    @Test
    @DisplayName("Delete with an unsaved open lineup: cleaned, and still unsaved")
    void removeKeepsUnsavedOpenLineupUnsaved() throws IOException {
        setUpMembersAndLineups();
        Path live = LineupFiles.originalPathFor(guildDir);
        context.set(LineupRepository.load(live), live);
        context.setLineupDirty(true);

        service.removeMember("anna");

        assertTrue(context.lineup().entries().stream().noneMatch(e -> e.teamMemberId().equals("anna")));
        assertTrue(context.isLineupDirty());
    }

    @Test
    @DisplayName("Delete: the member's journal players become \"former member\"; without a journal no error")
    void removeSetsJournalPlayersFormer() throws Exception {
        setMembers(List.of(new GuildMember("Puschel", "Puschel", List.of(), List.of()),
                new GuildMember("Other", "Other", List.of(), List.of())));
        assertEquals(0, service.removeMember("Other").formerPlayers(), "no journal yet");

        JournalImportService importService = new JournalImportService(context, guildService, context.journal(),
                BattleLogTestFiles::parser);
        var plan = importService.prepare(List.of(BattleLogTestFiles.file("de", "24-09-2026", LogDirection.DEFENSE)));
        assertTrue(importService.execute(plan, ImportAnswers.defaults()).isSuccess());
        JournalRepository journal = context.journal().repository(false).orElseThrow();
        List<JournalPlayer> players = journal.findPlayersByMember("Puschel");
        assertFalse(players.isEmpty());

        GuildMemberService.RemovalResult result = service.removeMember("Puschel");

        assertEquals(players.size(), result.formerPlayers());
        assertTrue(journal.findPlayersByMember("Puschel").isEmpty());
        assertEquals(AssignmentStatus.FORMER, journal.findAssignment(players.get(0).id()).orElseThrow().status());
    }

    @Test
    @DisplayName("Delete: a lineup that cannot be written does not stop the others")
    void unwritableLineupDoesNotStopTheRest() throws IOException {
        setUpMembersAndLineups();
        Path broken = guildDir.resolve("broken" + LineupFiles.SUFFIX);
        Files.writeString(broken, "this is no lineup");

        GuildMemberService.RemovalResult result = service.removeMember("anna");

        assertEquals(List.of("broken" + LineupFiles.SUFFIX), result.failures());
        assertTrue(LineupRepository.load(LineupFiles.originalPathFor(guildDir)).entries().stream()
                .noneMatch(e -> e.teamMemberId().equals("anna")));
    }

    // --- helpers ---

    /** Anna (2 hero teams, 1 titan team) and Bert; Live: 2 entries of Anna, 1 of Bert; test.lineup: 1 of each. */
    private void setUpMembersAndLineups() throws IOException {
        Hero hero = context.catalog().heroes().findAll().get(0);
        Titan titan = context.catalog().titans().findAll().get(0);
        GuildMember anna = new GuildMember("anna", "Anna",
                List.of(new HeroTeam("anna", 0, List.of(hero), 100_000), new HeroTeam("anna", 1, List.of(hero), 90_000)),
                List.of(new TitanTeam("anna", 0, List.of(titan), 80_000, null, null)));
        GuildMember bert = new GuildMember("bert", "Bert",
                List.of(new HeroTeam("bert", 0, List.of(hero), 70_000)), List.of());
        setMembers(List.of(anna, bert));
        guildService.saveGuild();

        LineupRepository.save(lineup(
                new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("foundry", "anna", Lineup.TeamType.HERO, 1),
                new Lineup.Entry("bastion", "bert", Lineup.TeamType.HERO, 0)), LineupFiles.originalPathFor(guildDir));
        LineupRepository.save(lineup(
                new Lineup.Entry("moon-temple", "anna", Lineup.TeamType.TITAN, 0),
                new Lineup.Entry("city-hall", "bert", Lineup.TeamType.HERO, 0)), guildDir.resolve("test" + LineupFiles.SUFFIX));
    }

    private Lineup lineup(Lineup.Entry... entries) {
        return new Lineup(context.guild().id(), context.guild().name(), "", LocalDateTime.now(), List.of(entries));
    }

    private void setMembers(List<GuildMember> members) {
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), members, context.guild().gameGuildId()));
        context.setGuildDirty(false);
    }
}
