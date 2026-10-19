package org.c2w.service;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.Catalog;
import org.c2w.infra.Config;
import org.c2w.service.journal.ImportAnswers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JournalMaintenanceService} on a real import of the sample logs into a
 * temp workspace. Every test also checks that the guild is never changed.
 */
class JournalMaintenanceServiceTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private JournalMaintenanceService service;
    private Guild guildBefore;
    private String guildFileBefore;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
        service = new JournalMaintenanceService(context.journal(), BattleLogTestFiles::parser);
    }

    @AfterEach
    void closeWorkspace() throws Exception {
        if (guildBefore != null) {
            assertEquals(guildBefore, context.guild(), "the guild in memory is unchanged");
            assertEquals(guildFileBefore, Files.readString(context.guildFilePath()), "guild.json is unchanged");
            assertFalse(context.isGuildDirty());
        }
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    /** Imports the German sample logs (all 6 battles, both directions) with the default answers. */
    private void importAll(Collection<String> days) throws Exception {
        JournalImportService importer = new JournalImportService(context, guildService, context.journal(),
                BattleLogTestFiles::parser);
        List<Path> files = new ArrayList<>();
        for (String day : days) {
            files.add(BattleLogTestFiles.file("de", day, LogDirection.ATTACK));
            files.add(BattleLogTestFiles.file("de", day, LogDirection.DEFENSE));
        }
        assertTrue(importer.execute(importer.prepare(files), ImportAnswers.defaults()).isSuccess());
        // The import links the guild (game guild id) - save it, then compare against this state.
        guildService.saveGuild();
        guildBefore = context.guild();
        guildFileBefore = Files.readString(context.guildFilePath());
    }

    private JournalRepository repo() throws JournalException {
        return service.repository().orElseThrow();
    }

    private int battle(String day) throws JournalException {
        LocalDate date = LocalDate.parse(day.substring(6) + "-" + day.substring(3, 5) + "-" + day.substring(0, 2));
        return repo().listBattles().stream().filter(b -> b.date().equals(date)).findFirst().orElseThrow().battleId();
    }

    @Test
    @DisplayName("Without a journal: reading works and creates no file, writing is refused")
    void withoutJournal() throws Exception {
        assertEquals(Optional.empty(), service.repository());
        assertEquals(JournalCounts.NONE, service.countBattles(List.of(1)));
        assertThrows(JournalException.class, () -> service.deleteBattles(List.of(1)));
        assertFalse(JournalDatabase.exists(guildService.guildDir("Alpha")));
    }

    @Test
    @DisplayName("Delete one and several battles; the counts for the confirmation are right")
    void deleteBattles() throws Exception {
        importAll(BattleLogTestFiles.SEASON_16_DAYS);
        int auge = battle("24-09-2026");
        int rakuen = battle("21-09-2026");
        int elysium = battle("14-09-2026");
        int augeFights = fights("24-09-2026");

        assertEquals(new JournalCounts(1, 2, augeFights), service.countBattles(List.of(auge)));
        assertEquals(1, service.deleteBattles(List.of(auge)));
        assertEquals(5, repo().listBattles().size());

        JournalCounts two = service.countBattles(List.of(rakuen, elysium));
        assertEquals(new JournalCounts(2, 4, fights("21-09-2026") + fights("14-09-2026")), two);
        assertEquals(2, service.deleteBattles(List.of(rakuen, elysium, auge)));
        assertEquals(3, repo().listBattles().size());
        assertEquals(Optional.empty(), repo().findBattleSummary(rakuen));
    }

    @Test
    @DisplayName("Player assignments: change status, assign to a member, unknown member refused, guild unchanged")
    void assignments() throws Exception {
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), List.of(member("Puschel"), member("Max"))));
        guildService.saveGuild();
        importAll(List.of("24-09-2026"));
        JournalPlayer puschel = repo().findOwnPlayer("Puschel").orElseThrow();
        assertEquals("Puschel", repo().findAssignment(puschel.id()).orElseThrow().memberId(), "assigned by the import");

        service.setAssignment(puschel.id(), AssignmentStatus.FORMER, "Puschel", context.guild());
        PlayerAssignment former = repo().findAssignment(puschel.id()).orElseThrow();
        assertEquals(AssignmentStatus.FORMER, former.status());
        assertNull(former.memberId());

        service.setAssignment(puschel.id(), AssignmentStatus.ASSIGNED, "Max", context.guild());
        assertEquals("Max", repo().findAssignment(puschel.id()).orElseThrow().memberId());
        assertThrows(IllegalArgumentException.class,
                () -> service.setAssignment(puschel.id(), AssignmentStatus.ASSIGNED, "Nobody", context.guild()));
        assertEquals(2, context.guild().members().size());
    }

    @Test
    @DisplayName("Name mappings: change and delete; invalid catalog ids are refused")
    void nameMappings() throws Exception {
        importAll(List.of("24-09-2026"));
        service.putNameMapping(NameMappingKind.HERO, "Neuheld", "dante");
        assertEquals("dante", repo().findNameMapping(NameMappingKind.HERO, "Neuheld").orElseThrow().catalogId());
        assertThrows(IllegalArgumentException.class, () -> service.putNameMapping(NameMappingKind.HERO, "Neuheld", "nope"));
        assertTrue(service.isValidMappingTarget(NameMappingKind.TOTEM, "FIRE"));
        assertTrue(service.deleteNameMapping(NameMappingKind.HERO, "Neuheld"));
        assertFalse(service.deleteNameMapping(NameMappingKind.HERO, "Neuheld"));
    }

    // --- helpers ---

    private static int fights(String day) {
        return BattleLogTestFiles.parse("de", day, LogDirection.ATTACK).log().fights().size()
                + BattleLogTestFiles.parse("de", day, LogDirection.DEFENSE).log().fights().size();
    }

    private static GuildMember member(String id) {
        return new GuildMember(id, id, List.of(), List.of());
    }
}
