package org.c2w.service;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.UnitKind;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogParser;
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

import java.nio.charset.StandardCharsets;
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
        return repo().listBattles(null).stream().filter(b -> b.date().equals(date)).findFirst().orElseThrow().battleId();
    }

    @Test
    @DisplayName("Without a journal: reading works and creates no file, writing is refused")
    void withoutJournal() throws Exception {
        assertEquals(Optional.empty(), service.repository());
        assertEquals(JournalCounts.NONE, service.countBattles(List.of(1)));
        assertEquals(List.of(), service.seasonsFor(LocalDate.now()));
        assertEquals(1, service.suggestNewSeason(LocalDate.of(2026, 10, 4)).number());
        assertEquals(0, service.reparse(null, null).logs());
        assertThrows(JournalException.class, () -> service.deleteBattles(List.of(1)));
        assertFalse(JournalDatabase.exists(guildService.guildDir("Alpha")));
    }

    @Test
    @DisplayName("Delete one and several battles; the counts for the confirmation are right")
    void deleteBattles() throws Exception {
        importAll(BattleLogTestFiles.BATTLE_DAYS);
        int auge = battle("24-09-2026");
        int rakuen = battle("21-09-2026");
        int elysium = battle("14-09-2026");
        int augeFights = fights("24-09-2026");

        assertEquals(new JournalCounts(1, 2, augeFights), service.countBattles(List.of(auge)));
        assertEquals(1, service.deleteBattles(List.of(auge)));
        assertEquals(5, repo().listBattles(null).size());

        JournalCounts two = service.countBattles(List.of(rakuen, elysium));
        assertEquals(new JournalCounts(2, 4, fights("21-09-2026") + fights("14-09-2026")), two);
        assertEquals(2, service.deleteBattles(List.of(rakuen, elysium, auge)));
        assertEquals(3, repo().listBattles(null).size());
        assertEquals(Optional.empty(), repo().findBattleSummary(rakuen));
    }

    @Test
    @DisplayName("Seasons: create, edit with preview and reassignment, overlap rejected, delete with/without battles")
    void seasons() throws Exception {
        importAll(BattleLogTestFiles.BATTLE_DAYS);
        Season first = repo().listSeasons().get(0);
        assertEquals(LocalDate.of(2026, 9, 14), first.start(), "the import's first season");
        assertTrue(repo().listBattles(null).stream().allMatch(b -> Objects.equals(b.seasonId(), first.id())));

        // suggestion: after the last season in the 12-week raster
        Season suggested = service.suggestNewSeason(LocalDate.of(2026, 10, 4));
        assertEquals(0, suggested.id());
        assertEquals(first.number() + 1, suggested.number());
        assertEquals(first.end(), suggested.start());
        assertEquals(first.end().plusWeeks(12), suggested.end());
        assertEquals(Optional.empty(), service.findConflict(suggested));
        assertEquals(0, service.previewSave(suggested));
        JournalMaintenanceService.SeasonChange created = service.saveSeason(
                new Season(0, suggested.number(), suggested.start(), suggested.end(), "Herbst"));
        assertEquals(0, created.reassigned());
        assertEquals("Herbst", created.season().note());

        // moving the first season's start behind 21.09.: 14.09., 17.09. and 21.09. lose it
        Season moved = new Season(first.id(), first.number(), LocalDate.of(2026, 9, 22), first.end(), null);
        assertEquals(3, service.previewSave(moved));
        assertEquals(3, service.saveSeason(moved).reassigned());
        assertEquals(3, repo().listBattles(null).stream().filter(b -> b.seasonId() == null).count());

        // a new season over 31.08. - 21.09. picks them up again
        Season earlier = new Season(0, 0, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 22), null);
        assertEquals(3, service.previewSave(earlier));
        Season earlierSaved = service.saveSeason(earlier).season();
        assertEquals(List.of(0, 0, 0, 1, 1, 1), repo().listBattles(null).stream()
                .map(BattleSummary::seasonNumber).sorted().toList());

        // overlap and duplicate numbers are rejected, naming the other season
        Season overlapping = new Season(0, 7, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), null);
        JournalMaintenanceService.SeasonConflict conflict = service.findConflict(overlapping).orElseThrow();
        assertEquals(JournalMaintenanceService.SeasonConflict.Kind.OVERLAP, conflict.kind());
        assertEquals(earlierSaved.id(), conflict.other().id());
        assertThrows(JournalException.class, () -> service.saveSeason(overlapping));
        Season sameNumber = new Season(0, 0, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 7, 1), null);
        assertEquals(JournalMaintenanceService.SeasonConflict.Kind.NUMBER_TAKEN,
                service.findConflict(sameNumber).orElseThrow().kind());
        assertEquals(3, repo().listSeasons().size(), "nothing saved");

        // delete without battles: they stay, without season
        assertEquals(3, service.countSeason(earlierSaved.id()).battles());
        assertEquals(3, service.previewDelete(earlierSaved.id()));
        JournalMaintenanceService.SeasonChange deleted = service.deleteSeason(earlierSaved.id(), false);
        assertEquals(0, deleted.deleted());
        assertEquals(6, repo().listBattles(null).size());
        assertEquals(3, repo().listBattles(null).stream().filter(b -> b.seasonId() == null).count());

        // delete with battles
        JournalCounts firstCounts = service.countSeason(first.id());
        assertEquals(3, firstCounts.battles());
        assertEquals(3, service.deleteSeason(first.id(), true).deleted());
        assertEquals(3, repo().listBattles(null).size());
        assertThrows(JournalException.class, () -> service.deleteSeason(first.id(), true));
    }

    @Test
    @DisplayName("Manual season: only a season containing the battle's day, or none")
    void manualSeason() throws Exception {
        importAll(List.of("24-09-2026"));
        int auge = battle("24-09-2026");
        Season first = repo().listSeasons().get(0);
        Season later = repo().createSeason(first.number() + 1, first.end());

        assertEquals(List.of(first), service.seasonsFor(LocalDate.of(2026, 9, 24)));
        assertThrows(IllegalArgumentException.class, () -> service.assignSeason(auge, later.id()));
        service.assignSeason(auge, null);
        assertNull(repo().findBattleSummary(auge).orElseThrow().seasonId());
        service.assignSeason(auge, first.id());
        assertEquals(first.id(), repo().findBattleSummary(auge).orElseThrow().seasonId());
        assertThrows(JournalException.class, () -> service.assignSeason(999, null));
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

    @Test
    @DisplayName("Parse again: one and all battles; seasons and assignments stay, parser version, problems before/after")
    void reparse() throws Exception {
        importAll(BattleLogTestFiles.BATTLE_DAYS);
        int auge = battle("24-09-2026");
        JournalPlayer puschel = repo().findOwnPlayer("Puschel").orElseThrow();
        repo().setAssignment(puschel.id(), null, AssignmentStatus.NOT_IN_COW2WIN);
        Map<Integer, Integer> seasons = new HashMap<>();
        repo().listBattles(null).forEach(b -> seasons.put(b.battleId(), b.seasonId()));
        long fightsBefore = repo().countAll().fights();
        List<int[]> progress = new ArrayList<>();

        JournalMaintenanceService.ReparseResult one = service.reparse(List.of(auge),
                (done, total) -> progress.add(new int[]{done, total}));

        assertEquals(2, one.logs());
        assertEquals(0, one.problemsBefore());
        assertEquals(0, one.problemsAfter());
        assertEquals(List.of(), one.failures());
        assertEquals(2, progress.get(progress.size() - 1)[0]);
        assertEquals(2, progress.get(progress.size() - 1)[1]);

        JournalMaintenanceService.ReparseResult all = service.reparse(null, null);
        assertEquals(12, all.logs());
        assertEquals(fightsBefore, repo().countAll().fights());
        repo().listBattles(null).forEach(b -> assertEquals(seasons.get(b.battleId()), b.seasonId()));
        assertEquals(AssignmentStatus.NOT_IN_COW2WIN, repo().findAssignment(puschel.id()).orElseThrow().status());
        assertTrue(repo().listLogs(null).stream().allMatch(l -> l.parserVersion() == BattleLogParser.PARSER_VERSION));
    }

    @Test
    @DisplayName("Parse again after a name mapping: the stored log carries the new id, problems drop")
    void reparseWithNewMapping() throws Exception {
        importAll(List.of("24-09-2026"));
        int auge = battle("24-09-2026");
        // Replace the attack log by one with an unknown hero name (as an older catalog would have had).
        Path original = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.ATTACK);
        var hero = BattleLogTestFiles.parse(original).log().fights().stream()
                .flatMap(f -> f.attacker().units().stream())
                .filter(u -> u.kind() == UnitKind.HERO && u.catalogId() != null).findFirst().orElseThrow();
        String content = Files.readString(original, StandardCharsets.UTF_8).replace(hero.name() + " |", "Neuheld |");
        repo().saveLog(BattleLogTestFiles.parser().parse(original.getFileName().toString(), content),
                content.getBytes(StandardCharsets.UTF_8), null);
        int problems = repo().listLogs(auge).stream().mapToInt(LogInfo::problemCount).sum();
        assertTrue(problems > 0);

        service.putNameMapping(NameMappingKind.HERO, "Neuheld", hero.catalogId());
        JournalMaintenanceService.ReparseResult result = service.reparse(null, null);

        assertEquals(problems, result.problemsBefore());
        assertTrue(result.problemsAfter() < problems);
        assertTrue(repo().loadLog(auge, LogDirection.ATTACK).orElseThrow().log().fights().stream()
                .flatMap(f -> f.attacker().units().stream()).filter(u -> u.name().equals("Neuheld"))
                .allMatch(u -> hero.catalogId().equals(u.catalogId())));
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
