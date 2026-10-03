package org.c2w.data.journal.db;

import org.c2w.data.journal.*;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.c2w.data.journal.db.JournalDatabaseTest.count;
import static org.junit.jupiter.api.Assertions.*;

class JournalRepositoryTest {

    private static final long OWN_GAME_ID = 193861;
    private static final long SCHWARZES_AUGE = 114834;
    private static final long SOJUS = 81447;

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
    void oneBattleTwoDirectionsTwoLanguages() throws Exception {
        SaveResult attack = save("de", "24-09-2026", LogDirection.ATTACK);
        SaveResult defense = save("fr", "24-09-2026", LogDirection.DEFENSE);

        assertTrue(attack.battleCreated());
        assertFalse(defense.battleCreated());
        assertEquals(attack.battleId(), defense.battleId());
        assertEquals(1, count(db, "SELECT COUNT(*) FROM battle"));
        assertEquals(2, count(db, "SELECT COUNT(*) FROM battle_log"));
        assertEquals("deutsch", repository.loadLog(attack.battleId(), LogDirection.ATTACK).orElseThrow()
                .log().header().language());
        assertEquals("francais", repository.loadLog(attack.battleId(), LogDirection.DEFENSE).orElseThrow()
                .log().header().language());
        assertEquals(Optional.of(attack.battleId()), repository.findBattle(LocalDate.of(2026, 9, 24), SCHWARZES_AUGE));
        assertEquals(Optional.empty(), repository.findBattle(LocalDate.of(2026, 9, 25), SCHWARZES_AUGE));
    }

    @Test
    void laterExportReplacesThePartialOne() throws Exception {
        SaveResult partial = save("partial", "01-10-2026", LogDirection.ATTACK);
        long playersAfterPartial = count(db, "SELECT COUNT(*) FROM player");
        assertEquals(66, fights(partial.battleId(), LogDirection.ATTACK));

        SaveResult later = save("de", "01-10-2026", LogDirection.ATTACK);

        assertEquals(SaveResult.Outcome.REPLACED, later.outcome());
        assertEquals(partial.battleId(), later.battleId());
        assertEquals(1, count(db, "SELECT COUNT(*) FROM battle"));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM battle_log"));
        assertEquals(71, fights(later.battleId(), LogDirection.ATTACK));
        assertEquals(71, count(db, "SELECT COUNT(*) FROM fight"), "old fights are gone");
        assertEquals(count(db, "SELECT COUNT(DISTINCT guild_id || '/' || name) FROM player"),
                count(db, "SELECT COUNT(*) FROM player"), "no duplicate players");
        assertTrue(count(db, "SELECT COUNT(*) FROM player") >= playersAfterPartial);
        assertEquals(BattleStatus.RUNNING, summary(later.battleId()).status());
        assertEquals(BattleResult.RUNNING, summary(later.battleId()).result());

        SaveResult again = save("de", "01-10-2026", LogDirection.ATTACK);
        assertEquals(SaveResult.Outcome.UNCHANGED, again.outcome());
        assertEquals(71, count(db, "SELECT COUNT(*) FROM fight"));
    }

    @Test
    void finishedBattleIsNotSetBackByARunningExport() throws Exception {
        SaveResult finished = save("de", "24-09-2026", LogDirection.ATTACK);
        BattleSummary summary = summary(finished.battleId());
        assertEquals(BattleStatus.FINISHED, summary.status());
        assertEquals(BattleResult.WIN, summary.result());
        assertEquals(851, summary.rankingPoints());

        // A running export of the same battle (artificial: same file content with 0 ranking points in the name).
        Path file = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.DEFENSE);
        String runningName = file.getFileName().toString().replace("Sieg +851", "Unentschieden 0");
        byte[] raw = Files.readAllBytes(file);
        BattleLogParseResult running = BattleLogTestFiles.parser().parse(runningName,
                new String(raw, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(BattleResult.RUNNING, running.log().header().result());

        SaveResult result = repository.saveLog(running, raw, null);

        assertEquals(SaveResult.Outcome.CREATED, result.outcome());
        assertEquals(1, result.warnings().size(), result.warnings().toString());
        summary = summary(finished.battleId());
        assertEquals(BattleStatus.FINISHED, summary.status());
        assertEquals(BattleResult.WIN, summary.result());
        assertEquals(851, summary.rankingPoints());
    }

    @Test
    void logOfAnotherOwnGuildIsRejected() throws Exception {
        save("de", "24-09-2026", LogDirection.ATTACK);
        BattleLogParseResult parsed = BattleLogTestFiles.parse("de", "21-09-2026", LogDirection.ATTACK);
        BattleLogHeader h = parsed.log().header();
        BattleLogHeader foreign = new BattleLogHeader(h.date(), new GuildRef("Andere Gilde", 7, 4711), h.opponent(),
                h.rankingPoints(), h.result(), h.direction(), h.language(), h.fileName());
        BattleLogParseResult foreignLog = new BattleLogParseResult(new BattleLog(foreign, parsed.log().entries()),
                parsed.problems());

        OwnGuildMismatchException e = assertThrows(OwnGuildMismatchException.class,
                () -> repository.saveLog(foreignLog, new byte[]{1, 2, 3}, null));

        assertEquals(OWN_GAME_ID, e.storedGameGuildId());
        assertEquals(4711, e.logGameGuildId());
        assertEquals(1, count(db, "SELECT COUNT(*) FROM battle"), "nothing written");
    }

    @Test
    void logWithoutHeadDataIsRejected() throws Exception {
        Path file = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.ATTACK);
        byte[] raw = Files.readAllBytes(file);
        BattleLogParseResult renamed = BattleLogTestFiles.parser().parse("kampf.csv",
                new String(raw, java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> repository.saveLog(renamed, raw, null));
    }

    @Test
    void seasons() throws Exception {
        Season first = repository.createSeason(1, LocalDate.of(2026, 9, 14));
        assertEquals(LocalDate.of(2026, 12, 7), first.end(), "12 weeks");
        assertEquals(LocalDate.of(2026, 12, 6), first.lastDay());
        Season second = repository.createSeason(2, first.end(), first.end().plusWeeks(12));

        assertEquals(List.of(first, second), repository.listSeasons());
        assertEquals(Optional.of(first), repository.findSeasonFor(LocalDate.of(2026, 9, 14)));
        assertEquals(Optional.of(first), repository.findSeasonFor(LocalDate.of(2026, 12, 6)));
        assertEquals(Optional.of(second), repository.findSeasonFor(LocalDate.of(2026, 12, 7)));
        assertEquals(Optional.empty(), repository.findSeasonFor(LocalDate.of(2026, 9, 13)));

        JournalException overlap = assertThrows(JournalException.class,
                () -> repository.createSeason(3, LocalDate.of(2026, 11, 1)));
        assertTrue(overlap.getMessage().contains("overlaps"), overlap.getMessage());
        assertThrows(JournalException.class, () -> repository.createSeason(1, LocalDate.of(2027, 6, 1)));
        assertThrows(JournalException.class, () -> repository.updateSeason(
                new Season(second.id(), 2, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 1), null)));
        Season moved = repository.updateSeason(new Season(second.id(), 2, first.end(), first.end().plusWeeks(13), "longer"));
        assertEquals(List.of(first, moved), repository.listSeasons());

        int battleId = save("de", "24-09-2026", LogDirection.ATTACK).battleId();
        assertNull(summary(battleId).seasonId());
        repository.assignSeason(battleId, first.id());
        assertEquals(first.id(), summary(battleId).seasonId());
        assertEquals(1, summary(battleId).seasonNumber());
        assertEquals(1, repository.listBattles(first.id()).size());
        assertEquals(0, repository.listBattles(second.id()).size());

        // saveLog without season keeps the assignment, with season sets it.
        save("de", "24-09-2026", LogDirection.DEFENSE);
        assertEquals(first.id(), summary(battleId).seasonId());
        Path file = BattleLogTestFiles.file("de", "21-09-2026", LogDirection.ATTACK);
        int other = repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), first.id()).battleId();
        assertEquals(first.id(), summary(other).seasonId());
    }

    @Test
    void deleteBattleCascadesAndCleansUp() throws Exception {
        int rakuen = save("de", "21-09-2026", LogDirection.ATTACK).battleId();
        save("de", "21-09-2026", LogDirection.DEFENSE);
        int auge = save("de", "24-09-2026", LogDirection.ATTACK).battleId();
        JournalPlayer ownPlayer = repository.listPlayers(true).stream()
                .filter(p -> p.name().equals("Puschel")).findFirst().orElseThrow();
        repository.setAssignment(ownPlayer.id(), "member-puschel", AssignmentStatus.ASSIGNED);
        // An own player with an OPEN assignment that only occurs in the deleted battle is removed.
        JournalPlayer openPlayer = playersOnlyIn(rakuen).stream().filter(JournalPlayer::ownGuild).findFirst().orElseThrow();
        repository.setAssignment(openPlayer.id(), null, AssignmentStatus.OPEN);

        assertTrue(repository.deleteBattle(auge));
        assertTrue(repository.deleteBattle(rakuen));
        assertFalse(repository.deleteBattle(rakuen));

        for (String table : List.of("battle", "battle_log", "fight", "fight_unit", "fort_event", "parse_problem")) {
            assertEquals(0, count(db, "SELECT COUNT(*) FROM " + table), table);
        }
        assertEquals(List.of(ownPlayer), repository.listPlayers(false), "only the assigned own player is kept");
        assertEquals(AssignmentStatus.ASSIGNED, repository.findAssignment(ownPlayer.id()).orElseThrow().status());
        assertEquals(Optional.empty(), repository.findAssignment(openPlayer.id()));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM guild"), "opponents removed, own guild stays");
        assertEquals(1, count(db, "SELECT COUNT(*) FROM guild WHERE is_own"));
    }

    @Test
    void deleteSeasonWithAndWithoutBattles() throws Exception {
        Season first = repository.createSeason(1, LocalDate.of(2026, 9, 14));
        int a = save("de", "24-09-2026", LogDirection.ATTACK).battleId();
        int b = save("de", "21-09-2026", LogDirection.ATTACK).battleId();
        repository.assignSeason(a, first.id());
        repository.assignSeason(b, first.id());

        assertTrue(repository.deleteSeason(first.id(), false));
        assertEquals(2, repository.listBattles(null).size());
        assertTrue(repository.listBattles(null).stream().allMatch(s -> s.seasonId() == null));

        Season again = repository.createSeason(1, LocalDate.of(2026, 9, 14));
        repository.assignSeason(a, again.id());
        assertTrue(repository.deleteSeason(again.id(), true));
        assertEquals(List.of(b), repository.listBattles(null).stream().map(BattleSummary::battleId).toList());
        assertEquals(List.of(), repository.listSeasons());
        assertEquals(2, count(db, "SELECT COUNT(*) FROM guild"), "own guild + Rakuen");
    }

    @Test
    void listBattlesForAllSixBattles() throws Exception {
        for (String day : BattleLogTestFiles.BATTLE_DAYS) {
            save("de", day, LogDirection.ATTACK);
            if (!day.equals("28-09-2026")) {
                save("en", day, LogDirection.DEFENSE);
            }
        }

        List<BattleSummary> battles = repository.listBattles(null);

        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 14)),
                battles.stream().map(BattleSummary::date).toList());
        assertSummary(battles.get(0), "Союз", BattleResult.RUNNING, BattleStatus.RUNNING, 0, 4990, 2879);
        assertSummary(battles.get(1), "Rebel Alliance", BattleResult.LOSS, BattleStatus.FINISHED, -171, 2643, null);
        assertSummary(battles.get(2), "Das Schwarze Auge", BattleResult.WIN, BattleStatus.FINISHED, 851, 2783, 1782);
        assertSummary(battles.get(3), "Rakuen", BattleResult.LOSS, BattleStatus.FINISHED, -286, 3725, 6576);
        assertSummary(battles.get(4), "RAZEM", BattleResult.WIN, BattleStatus.FINISHED, 975, 4606, 2357);
        assertSummary(battles.get(5), "Elysium", BattleResult.LOSS, BattleStatus.FINISHED, -42, 3793, 4207);
        assertEquals(Set.of(LogDirection.ATTACK), battles.get(1).directions());
        assertEquals(Set.of(LogDirection.ATTACK, LogDirection.DEFENSE), battles.get(0).directions());
        assertEquals(SOJUS, battles.get(0).opponent().gameGuildId());
    }

    @Test
    void assignmentsAndRenames() throws Exception {
        save("de", "24-09-2026", LogDirection.ATTACK);
        List<JournalPlayer> own = repository.listPlayers(true);
        assertTrue(own.stream().allMatch(JournalPlayer::ownGuild));
        assertTrue(repository.listPlayers(false).size() > own.size());
        JournalPlayer oldName = own.get(0);
        JournalPlayer newName = own.get(1);

        repository.setAssignment(oldName.id(), "m1", AssignmentStatus.ASSIGNED);
        repository.setAssignment(newName.id(), "m1", AssignmentStatus.ASSIGNED);
        PlayerAssignment former = repository.setAssignment(own.get(2).id(), null, AssignmentStatus.FORMER);

        assertEquals(List.of(oldName, newName).stream().map(JournalPlayer::name).sorted().toList(),
                repository.findPlayersByMember("m1").stream().map(JournalPlayer::name).toList());
        assertEquals(former, repository.findAssignment(own.get(2).id()).orElseThrow());
        repository.setAssignment(newName.id(), null, AssignmentStatus.NOT_IN_COW2WIN);
        assertEquals(1, repository.findPlayersByMember("m1").size(), "status change replaces the assignment");

        JournalPlayer opponent = repository.listPlayers(false).stream().filter(p -> !p.ownGuild()).findFirst().orElseThrow();
        assertThrows(JournalException.class, () -> repository.setAssignment(opponent.id(), "m2", AssignmentStatus.ASSIGNED));
        assertThrows(JournalException.class, () -> repository.setAssignment(999_999, "m2", AssignmentStatus.ASSIGNED));
        assertThrows(IllegalArgumentException.class, () -> repository.setAssignment(oldName.id(), null, AssignmentStatus.ASSIGNED));
    }

    @Test
    void nameMappings() throws Exception {
        repository.putNameMapping(NameMappingKind.HERO, "Neuheld", "new-hero");
        repository.putNameMapping(NameMappingKind.TOTEM, "Sturmgeisttotem", "DISTORTION");

        assertEquals(Optional.of(new NameMapping(NameMappingKind.HERO, "Neuheld", "new-hero")),
                repository.findNameMapping(NameMappingKind.HERO, " NEUHELD "));
        assertEquals(Optional.empty(), repository.findNameMapping(NameMappingKind.PET, "Neuheld"));

        repository.putNameMapping(NameMappingKind.HERO, "neuheld", "other-id");
        assertEquals("other-id", repository.findNameMapping(NameMappingKind.HERO, "Neuheld").orElseThrow().catalogId());
        assertEquals(2, repository.listNameMappings().size());
        assertThrows(IllegalArgumentException.class, () -> repository.putNameMapping(NameMappingKind.HERO, " ", "x"));
    }

    // --- helpers ---

    private SaveResult save(String folder, String day, LogDirection direction) throws Exception {
        Path file = BattleLogTestFiles.file(folder, day, direction);
        return repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null);
    }

    private int fights(int battleId, LogDirection direction) throws JournalException {
        return repository.loadLog(battleId, direction).orElseThrow().log().fights().size();
    }

    private BattleSummary summary(int battleId) throws JournalException {
        return repository.listBattles(null).stream().filter(s -> s.battleId() == battleId).findFirst().orElseThrow();
    }

    /** Players that occur only in fights of the given battle. */
    private List<JournalPlayer> playersOnlyIn(int battleId) throws JournalException {
        List<Integer> ids = db.read(c -> {
            List<Integer> result = new java.util.ArrayList<>();
            try (var ps = c.prepareStatement("SELECT p.id FROM player p WHERE NOT EXISTS (SELECT 1 FROM fight f"
                    + " JOIN battle_log l ON l.id = f.battle_log_id WHERE l.battle_id <> ?"
                    + " AND (f.attacker_player_id = p.id OR f.defender_player_id = p.id))")) {
                ps.setInt(1, battleId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getInt(1));
                    }
                }
            }
            return result;
        });
        return repository.listPlayers(false).stream().filter(p -> ids.contains(p.id())).toList();
    }

    private static void assertSummary(BattleSummary s, String opponent, BattleResult result, BattleStatus status,
                                      int rankingPoints, Integer own, Integer opp) {
        assertEquals(opponent, s.opponent().name());
        assertEquals(result, s.result());
        assertEquals(status, s.status());
        assertEquals(rankingPoints, s.rankingPoints());
        assertEquals(own, s.ownPoints());
        assertEquals(opp, s.opponentPoints());
    }
}
