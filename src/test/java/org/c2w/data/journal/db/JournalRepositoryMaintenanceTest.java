package org.c2w.data.journal.db;

import org.c2w.data.journal.*;
import org.c2w.data.journal.parse.BattleLogParser;
import org.c2w.data.journal.parse.BattleLogTestFiles;
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

import static org.c2w.data.journal.db.JournalDatabaseTest.count;
import static org.junit.jupiter.api.Assertions.*;

/** The repository methods added for viewing and maintaining the journal (phase 5). */
class JournalRepositoryMaintenanceTest {

    private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

    @TempDir
    Path guildDir;

    private JournalDatabase db;
    private JournalRepository repository;

    @BeforeEach
    void open() throws Exception {
        db = JournalDatabase.open(guildDir, true).orElseThrow();
        repository = new JournalRepository(db);
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    @DisplayName("deleteNameMapping removes a mapping (normalized lookup), false if there is none")
    void deleteNameMapping() throws Exception {
        repository.putNameMapping(NameMappingKind.HERO, "Neuheld", "dante");
        repository.putNameMapping(NameMappingKind.PET, "Neuheld", "axel");

        assertTrue(repository.deleteNameMapping(NameMappingKind.HERO, " NEUHELD "));
        assertFalse(repository.deleteNameMapping(NameMappingKind.HERO, "Neuheld"));

        assertEquals(Optional.empty(), repository.findNameMapping(NameMappingKind.HERO, "Neuheld"));
        assertEquals(List.of(new NameMapping(NameMappingKind.PET, "Neuheld", "axel")), repository.listNameMappings());
    }

    @Test
    @DisplayName("reassignSeasonsByDate puts all 6 battles into the right season; previews count without writing")
    void reassignSeasonsByDate() throws Exception {
        saveAllSix();
        assertEquals(0, repository.countSeasonReassignments(), "no seasons, no battle has one");
        assertEquals(0, repository.reassignSeasonsByDate());

        Season first = repository.createSeason(1, SEP_14);
        assertEquals(6, repository.countSeasonReassignments());
        assertEquals(6, repository.countSeasonReassignments(repository.listSeasons()), "both previews agree");
        assertTrue(repository.listBattles(null).stream().allMatch(b -> b.seasonId() == null), "preview writes nothing");

        assertEquals(6, repository.reassignSeasonsByDate());
        assertTrue(repository.listBattles(null).stream().allMatch(b -> Objects.equals(b.seasonId(), first.id())));
        assertEquals(0, repository.reassignSeasonsByDate());

        // Planned: season 1 starts on 20.09., a new season 0 covers 31.08. - 19.09. -> 14.09. and 17.09. move.
        Season moved = new Season(first.id(), 1, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20).plusWeeks(12), null);
        Season earlier = new Season(0, 0, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 20), null);
        assertEquals(2, repository.countSeasonReassignments(List.of(moved)), "two battles lose their season");
        assertEquals(2, repository.countSeasonReassignments(List.of(moved, earlier)), "two battles get the new one");
        assertEquals(6, repository.countSeasonReassignments(List.of()), "all seasons gone");

        int changed = repository.inTransaction(r -> {
            r.updateSeason(moved);
            r.createSeason(0, earlier.start(), earlier.end());
            return r.reassignSeasonsByDate();
        });
        assertEquals(2, changed);
        Map<LocalDate, Integer> numbers = new HashMap<>();
        repository.listBattles(null).forEach(b -> numbers.put(b.date(), b.seasonNumber()));
        assertEquals(0, numbers.get(SEP_14));
        assertEquals(0, numbers.get(LocalDate.of(2026, 9, 17)));
        assertEquals(1, numbers.get(LocalDate.of(2026, 9, 21)));
        assertEquals(1, numbers.get(LocalDate.of(2026, 10, 1)));

        // A manual "no season" counts as a change and is undone by the reassignment.
        int auge = repository.findBattle(LocalDate.of(2026, 9, 24), 114834).orElseThrow();
        repository.assignSeason(auge, null);
        assertEquals(1, repository.countSeasonReassignments());
        assertEquals(1, repository.reassignSeasonsByDate());
    }

    @Test
    @DisplayName("Counting battles, logs and single fights for the delete confirmations")
    void counts() throws Exception {
        int auge = saveBoth("24-09-2026");
        int rakuen = save("de", "21-09-2026", LogDirection.ATTACK).battleId();
        int augeFights = fights("24-09-2026", LogDirection.ATTACK) + fights("24-09-2026", LogDirection.DEFENSE);
        int rakuenFights = fights("21-09-2026", LogDirection.ATTACK);

        assertEquals(new JournalCounts(1, 2, augeFights), repository.countBattles(List.of(auge)));
        assertEquals(new JournalCounts(2, 3, augeFights + rakuenFights), repository.countBattles(List.of(auge, rakuen, auge)));
        assertEquals(JournalCounts.NONE, repository.countBattles(List.of()));
        assertEquals(new JournalCounts(0, 0, 0), repository.countBattles(List.of(999)));
        assertEquals(new JournalCounts(2, 3, augeFights + rakuenFights), repository.countAll());

        Season season = repository.createSeason(1, SEP_14);
        assertEquals(new JournalCounts(0, 0, 0), repository.countSeason(season.id()));
        repository.assignSeason(rakuen, season.id());
        assertEquals(new JournalCounts(1, 1, rakuenFights), repository.countSeason(season.id()));
    }

    @Test
    @DisplayName("Log infos and the single battle summary")
    void logInfos() throws Exception {
        int auge = saveBoth("24-09-2026");

        List<LogInfo> logs = repository.listLogs(auge);

        assertEquals(List.of(LogDirection.DEFENSE, LogDirection.ATTACK), logs.stream().map(LogInfo::direction).toList());
        LogInfo defense = logs.get(0);
        assertEquals("deutsch", defense.language());
        assertTrue(defense.fileName().contains("Verteidigungs-Log"));
        assertEquals(BattleLogParser.PARSER_VERSION, defense.parserVersion());
        assertEquals(fights("24-09-2026", LogDirection.DEFENSE), defense.fightCount());
        assertEquals(1782, defense.pointsTotal());
        assertEquals(0, defense.problemCount());
        assertNotNull(defense.importedAt());
        assertEquals(2, repository.listLogs(null).size());
        assertEquals(List.of(), repository.listLogs(999));

        BattleSummary summary = repository.findBattleSummary(auge).orElseThrow();
        assertEquals("Das Schwarze Auge", summary.opponent().name());
        assertEquals(Optional.empty(), repository.findBattleSummary(999));
    }

    @Test
    @DisplayName("Player statistics from the defense logs only, in one query")
    void ownPlayerStats() throws Exception {
        saveBoth("24-09-2026");
        save("de", "21-09-2026", LogDirection.DEFENSE);
        JournalPlayer puschel = repository.findOwnPlayer("Puschel").orElseThrow();
        repository.setAssignment(puschel.id(), "m-puschel", AssignmentStatus.ASSIGNED);

        List<OwnPlayerStats> stats = repository.listOwnPlayerStats();

        // Expected values computed independently from the parsed defense logs.
        Map<String, Integer> defenses = new HashMap<>();
        Map<String, LocalDate> lastSeen = new HashMap<>();
        Map<String, List<Integer>> lastPowers = new HashMap<>();
        for (String day : List.of("21-09-2026", "24-09-2026")) {
            BattleLog log = BattleLogTestFiles.parse("de", day, LogDirection.DEFENSE).log();
            Map<String, List<Integer>> powersOfDay = new HashMap<>();
            for (Fight f : log.fights()) {
                defenses.merge(f.defender().playerName(), 1, Integer::sum);
                powersOfDay.computeIfAbsent(f.defender().playerName(), k -> new ArrayList<>()).add(f.defender().teamPower());
            }
            powersOfDay.forEach((name, powers) -> {
                lastSeen.put(name, log.header().date());
                lastPowers.put(name, powers);
            });
        }
        Set<String> ownNames = new HashSet<>(repository.listPlayers(true).stream().map(JournalPlayer::name).toList());
        assertEquals(ownNames, new HashSet<>(stats.stream().map(s -> s.player().name()).toList()), "all own players");
        assertTrue(stats.stream().allMatch(s -> s.player().ownGuild()));
        for (OwnPlayerStats s : stats) {
            String name = s.player().name();
            assertEquals(defenses.getOrDefault(name, 0), s.defenses(), name);
            assertEquals(lastSeen.get(name), s.lastSeen(), name);
            assertEquals(lastPowers.getOrDefault(name, List.of()), s.lastTeamPowers(), name);
        }
        OwnPlayerStats p = stats.stream().filter(s -> s.player().id() == puschel.id()).findFirst().orElseThrow();
        assertEquals(AssignmentStatus.ASSIGNED, p.status());
        assertEquals("m-puschel", p.memberId());
        assertTrue(p.defenses() > 0);
        OwnPlayerStats unassigned = stats.stream().filter(s -> s.assignment() == null).findFirst().orElseThrow();
        assertEquals(AssignmentStatus.OPEN, unassigned.status());
        assertNull(unassigned.memberId());
    }

    @Test
    @DisplayName("The defenders of the latest 3 defense logs")
    void recentDefenders() throws Exception {
        saveAllSix();

        JournalRepository.RecentDefenders recent = repository.recentDefenders(3);

        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 24)),
                recent.battleDays());
        Set<String> expected = new HashSet<>();
        for (String day : List.of("01-10-2026", "28-09-2026", "24-09-2026")) {
            BattleLogTestFiles.parse("de", day, LogDirection.DEFENSE).log().fights()
                    .forEach(f -> expected.add(f.defender().playerName()));
        }
        Set<String> actual = new HashSet<>();
        for (JournalPlayer p : repository.listPlayers(true)) {
            if (recent.playerIds().contains(p.id())) {
                actual.add(p.name());
            }
        }
        assertEquals(expected, actual);
        assertEquals(6, repository.recentDefenders(10).battleDays().size());
        assertEquals(List.of(), repository.recentDefenders(0).battleDays());
    }

    @Test
    @DisplayName("saveLog with replaceUnchanged replaces an identical file; season and assignments stay")
    void replaceUnchanged() throws Exception {
        int battleId = save("de", "24-09-2026", LogDirection.DEFENSE).battleId();
        Season season = repository.createSeason(1, SEP_14);
        repository.assignSeason(battleId, season.id());
        JournalPlayer puschel = repository.findOwnPlayer("Puschel").orElseThrow();
        repository.setAssignment(puschel.id(), "m1", AssignmentStatus.ASSIGNED);
        long fights = count(db, "SELECT COUNT(*) FROM fight");

        assertEquals(SaveResult.Outcome.UNCHANGED, save("de", "24-09-2026", LogDirection.DEFENSE).outcome());
        Path file = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.DEFENSE);
        SaveResult forced = repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null, true);

        assertEquals(SaveResult.Outcome.REPLACED, forced.outcome());
        assertEquals(battleId, forced.battleId());
        assertEquals(fights, count(db, "SELECT COUNT(*) FROM fight"));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM battle_log"));
        assertEquals(season.id(), repository.findBattleSummary(battleId).orElseThrow().seasonId());
        assertEquals(puschel, repository.findOwnPlayer("Puschel").orElseThrow(), "same player id");
        assertEquals("m1", repository.findAssignment(puschel.id()).orElseThrow().memberId());
    }

    @Test
    @DisplayName("reparseLog applies new name mappings to the stored original CSV")
    void reparseLogWithNameMappings() throws Exception {
        // An attack log with an unknown hero name: the first hero's name replaced by "Neuheld".
        Path original = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.ATTACK);
        BattleLog parsedOriginal = BattleLogTestFiles.parse(original).log();
        FightUnit hero = parsedOriginal.fights().stream().flatMap(f -> f.attacker().units().stream())
                .filter(u -> u.kind() == UnitKind.HERO && u.catalogId() != null).findFirst().orElseThrow();
        String content = Files.readString(original, StandardCharsets.UTF_8).replace(hero.name() + " |", "Neuheld |");
        byte[] raw = content.getBytes(StandardCharsets.UTF_8);
        BattleLogParser parser = BattleLogTestFiles.parser();
        BattleLogParseResult unknown = parser.parse(original.getFileName().toString(), content);
        int battleId = repository.saveLog(unknown, raw, null).battleId();
        int problemsBefore = unknown.problems().size();
        assertTrue(problemsBefore > 0, "the unknown name is a parse problem");
        assertTrue(neuheldIds(battleId).contains(null));

        repository.putNameMapping(NameMappingKind.HERO, "Neuheld", hero.catalogId());
        JournalRepository.Reparsed reparsed = repository.reparseLog(battleId, LogDirection.ATTACK,
                parser.withNameMappings(repository.nameMappingsByKind())).orElseThrow();

        assertEquals(problemsBefore, reparsed.problemsBefore());
        assertTrue(reparsed.problemsAfter() < problemsBefore, reparsed.toString());
        assertEquals(parsedOriginal.fights().size(), reparsed.fights());
        assertEquals(Set.of(hero.catalogId()), neuheldIds(battleId));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM battle_log"));
        assertEquals(Optional.of(JournalRepository.sha256(raw)), repository.findLogSha256(battleId, LogDirection.ATTACK));

        assertTrue(repository.deleteNameMapping(NameMappingKind.HERO, "Neuheld"));
        repository.reparseLog(battleId, LogDirection.ATTACK, parser);
        assertTrue(neuheldIds(battleId).contains(null), "without the mapping the name is unknown again");

        assertEquals(Optional.empty(), repository.reparseLog(battleId, LogDirection.DEFENSE, parser));
    }

    // --- helpers ---

    private Set<String> neuheldIds(int battleId) throws JournalException {
        Set<String> ids = new HashSet<>();
        repository.loadLog(battleId, LogDirection.ATTACK).orElseThrow().log().fights().stream()
                .flatMap(f -> f.attacker().units().stream()).filter(u -> u.name().equals("Neuheld"))
                .forEach(u -> ids.add(u.catalogId()));
        assertFalse(ids.isEmpty());
        return ids;
    }

    private void saveAllSix() throws Exception {
        for (String day : BattleLogTestFiles.BATTLE_DAYS) {
            saveBoth(day);
        }
    }

    private int saveBoth(String day) throws Exception {
        save("de", day, LogDirection.ATTACK);
        return save("de", day, LogDirection.DEFENSE).battleId();
    }

    private SaveResult save(String folder, String day, LogDirection direction) throws Exception {
        Path file = BattleLogTestFiles.file(folder, day, direction);
        return repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null);
    }

    private static int fights(String day, LogDirection direction) {
        return BattleLogTestFiles.parse("de", day, direction).log().fights().size();
    }
}
