package org.c2w.service;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.FightUnit;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanTeam;
import org.c2w.data.repository.GuildRepository;
import org.c2w.service.journal.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenarios of the journal import (Weltenschlacht journal, phase 3) with a temp
 * workspace, a real H2 journal and the sample logs. Cow2Win is about defense:
 * players are only asked for from the DEFENSE log, and the attack log never
 * changes the guild.
 */
class JournalImportServiceTest extends ServiceTestSupport {

    private static final long OWN_GAME_ID = 193861L;
    private static final String SEP_24 = "24-09-2026";

    // --- guild link, seasons, files ---

    @Test
    @DisplayName("First import: guild link and first season are asked, then the guild is linked (unsaved)")
    void firstImport() throws Exception {
        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE));

        assertTrue(plan.isImportable(), plan.errors().toString());
        assertEquals(OWN_GAME_ID, plan.guildLink().gameGuild().gameGuildId());
        assertEquals(1, plan.seasonQuestions().size());
        SeasonQuestion season = plan.seasonQuestions().get(0);
        assertEquals(SeasonQuestion.Kind.FIRST, season.kind());
        assertEquals(LocalDate.of(2026, 9, 24), season.suggestedStart());
        assertEquals(1, season.suggestedNumber());

        ImportResult result = service().execute(plan, ImportAnswers.defaults());

        assertTrue(result.isSuccess(), result.errors().toString());
        assertTrue(result.guildLinked());
        assertEquals(OWN_GAME_ID, context.guild().gameGuildId());
        assertTrue(context.isGuildDirty(), "guild changed but not saved");
        assertTrue(JournalDatabase.exists(alphaDir()));
        BattleSummary battle = repo().listBattles(null).get(0);
        assertEquals(BattleResult.WIN, battle.result());
        assertEquals(851, battle.rankingPoints());
        Season stored = repo().listSeasons().get(0);
        assertEquals(LocalDate.of(2026, 9, 24), stored.start());
        assertEquals(LocalDate.of(2026, 12, 17), stored.end(), "24.09. + 12 weeks (end exclusive)");
        assertEquals(stored.id(), battle.seasonId());
        assertEquals(1, result.battles().size());
        assertEquals(BattleLogCheck.Verdict.MATCHES, result.battles().get(0).verdict());
    }

    @Test
    @DisplayName("prepare writes nothing")
    void prepareWritesNothing() throws Exception {
        Guild before = context.guild();

        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE));

        assertTrue(plan.isImportable());
        assertFalse(JournalDatabase.exists(alphaDir()));
        assertEquals(before, context.guild());
        assertFalse(context.isGuildDirty());
    }

    @Test
    @DisplayName("Attack log only: no player questions, the guild keeps its members")
    void attackLogOnly() throws Exception {
        setGuild(sampleMembers());
        Guild before = context.guild();

        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK));
        assertEquals(List.of(), plan.playerQuestions());
        assertEquals(List.of(), plan.autoAssignments());

        ImportResult result = service().execute(plan, ImportAnswers.defaults());

        assertTrue(result.isSuccess());
        assertEquals(before.members(), context.guild().members());
        assertEquals(0, result.assignments());
        assertTrue(repo().loadLog(result.battles().get(0).battleId(), LogDirection.ATTACK).isPresent());
    }

    @Test
    @DisplayName("Second direction later (other language): one battle, check matches, players only now")
    void secondDirectionLater() throws Exception {
        ImportPlan first = service().prepare(files("de", SEP_24, LogDirection.ATTACK));
        assertEquals(List.of(), first.playerQuestions());
        assertEquals(BattleLogCheck.Verdict.NOT_CHECKABLE, first.battles().get(0).check().verdict());
        assertTrue(service().execute(first, ImportAnswers.defaults()).isSuccess());

        ImportPlan second = service().prepare(files("fr", SEP_24, LogDirection.DEFENSE));

        assertNull(second.guildLink(), "already linked");
        assertEquals(List.of(), second.seasonQuestions(), "the battle already has its season");
        PlannedBattle battle = second.battles().get(0);
        assertNotNull(battle.existingBattleId());
        assertEquals(BattleLogCheck.Verdict.MATCHES, battle.check().verdict());
        assertEquals(PlannedBattle.LogAction.NEW, battle.actions().get(LogDirection.DEFENSE));
        assertFalse(second.playerQuestions().isEmpty());

        assertTrue(service().execute(second, ImportAnswers.defaults()).isSuccess());
        assertEquals(1, repo().listBattles(null).size());
        assertEquals(Set.of(LogDirection.ATTACK, LogDirection.DEFENSE), repo().listBattles(null).get(0).directions());
    }

    @Test
    @DisplayName("Running battle: a later export replaces the partial one, the same file again is unchanged")
    void runningBattleReplaced() throws Exception {
        assertTrue(service().execute(service().prepare(List.of(
                BattleLogTestFiles.file("partial", "01-10-2026", LogDirection.ATTACK),
                BattleLogTestFiles.file("partial", "01-10-2026", LogDirection.DEFENSE))), ImportAnswers.defaults()).isSuccess());

        ImportPlan later = service().prepare(files("de", "01-10-2026", LogDirection.ATTACK));
        assertEquals(PlannedBattle.LogAction.REPLACE, later.battles().get(0).actions().get(LogDirection.ATTACK));
        assertEquals(BattleStatus.RUNNING, later.battles().get(0).status());
        ImportResult replaced = service().execute(later, ImportAnswers.defaults());
        assertEquals(SaveResult.Outcome.REPLACED, replaced.files().get(0).outcome());
        assertEquals(BattleStatus.RUNNING, replaced.battles().get(0).status());

        ImportPlan again = service().prepare(files("de", "01-10-2026", LogDirection.ATTACK));
        assertEquals(PlannedBattle.LogAction.UNCHANGED, again.battles().get(0).actions().get(LogDirection.ATTACK));
        assertEquals(SaveResult.Outcome.UNCHANGED, service().execute(again, ImportAnswers.defaults()).files().get(0).outcome());
    }

    @Test
    @DisplayName("All 6 German battles at once: one question for the first season from 14.09.")
    void severalBattlesAtOnce() throws Exception {
        ImportPlan plan = service().prepare(BattleLogTestFiles.files("de"));

        assertEquals(6, plan.battles().size());
        assertEquals(1, plan.seasonQuestions().size());
        SeasonQuestion q = plan.seasonQuestions().get(0);
        assertEquals(SeasonQuestion.Kind.FIRST, q.kind());
        assertEquals(LocalDate.of(2026, 9, 14), q.suggestedStart());
        assertEquals(6, q.battleDates().size());
        assertTrue(plan.battles().stream().allMatch(b -> q.id().equals(b.seasonQuestionId())));

        ImportResult result = service().execute(plan, ImportAnswers.defaults());

        assertTrue(result.isSuccess(), result.errors().toString());
        Season season = repo().listSeasons().get(0);
        assertEquals(LocalDate.of(2026, 12, 6), season.lastDay());
        assertTrue(repo().listBattles(null).stream().allMatch(b -> Integer.valueOf(season.id()).equals(b.seasonId())));
        assertEquals(6, result.battles().size());
    }

    @Test
    @DisplayName("Season raster: a battle after the last season gets a NEW question in the 12-week raster")
    void newSeasonInRaster() throws Exception {
        assertTrue(service().execute(service().prepare(files("de", "14-09-2026", LogDirection.ATTACK)),
                ImportAnswers.defaults()).isSuccess());
        // Shrink the first season so that 24.09. lies after it: 14.09. - 21.09.
        Season first = repo().listSeasons().get(0);
        repo().updateSeason(new Season(first.id(), 1, first.start(), LocalDate.of(2026, 9, 21), null));

        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK));

        SeasonQuestion q = plan.seasonQuestions().get(0);
        assertEquals(SeasonQuestion.Kind.NEW, q.kind());
        assertEquals(LocalDate.of(2026, 9, 21), q.suggestedStart());
        assertEquals(2, q.suggestedNumber());

        ImportResult refused = service().execute(plan, ImportAnswers.defaults().withSeason(q.id(), LocalDate.of(2026, 9, 15)));
        assertEquals(ImportResult.ImportError.Kind.SEASON_OVERLAP, refused.errors().get(0).kind());
        assertEquals(1, repo().listBattles(null).size(), "nothing written");

        ImportResult noSeason = service().execute(plan, ImportAnswers.defaults().withSeason(q.id(), null));
        assertTrue(noSeason.isSuccess());
        assertNull(noSeason.battles().get(0).seasonId());
    }

    @Test
    @DisplayName("Same battle and direction twice (DE + EN): the longer one is taken, the other skipped")
    void duplicateDirection() throws Exception {
        Path de = BattleLogTestFiles.file("de", "01-10-2026", LogDirection.ATTACK);
        Path en = BattleLogTestFiles.file("en", "01-10-2026", LogDirection.ATTACK);

        ImportPlan plan = service().prepare(List.of(de, en));

        assertEquals(PlannedFile.Status.SKIPPED, plan.files().get(0).status());
        assertEquals(PlannedFile.Error.DUPLICATE_DIRECTION, plan.files().get(0).error());
        assertEquals(PlannedFile.Status.READY, plan.files().get(1).status());
        assertEquals(1, plan.battles().size());
        ImportResult result = service().execute(plan, ImportAnswers.defaults());
        assertEquals(72, repo().loadLog(result.battles().get(0).battleId(), LogDirection.ATTACK).orElseThrow()
                .log().fights().size(), "the English export with one fight more");
    }

    @Test
    @DisplayName("Unreadable and foreign files are reported per file, the rest is imported")
    void badFiles() throws Exception {
        Path notALog = Files.writeString(workspace.resolve("notes.csv"), "hello,world\r\n");
        Path renamed = workspace.resolve("kampf.csv");
        Files.copy(BattleLogTestFiles.file("de", SEP_24, LogDirection.DEFENSE), renamed);

        ImportPlan plan = service().prepare(List.of(notALog, renamed, workspace.resolve("missing.csv"),
                BattleLogTestFiles.file("de", SEP_24, LogDirection.ATTACK)));

        assertEquals(PlannedFile.Error.NOT_A_BATTLE_LOG, plan.files().get(0).error());
        assertEquals(PlannedFile.Error.NO_HEAD_DATA, plan.files().get(1).error());
        assertEquals(PlannedFile.Error.UNREADABLE, plan.files().get(2).error());
        assertEquals(PlannedFile.Status.READY, plan.files().get(3).status());
        assertTrue(plan.isImportable());
    }

    @Test
    @DisplayName("Foreign guild: other game guild id, or another Cow2Win guild has the id - nothing importable")
    void foreignGuild() throws Exception {
        setGuild(context.guild().withGameGuildId(1L));
        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK));
        assertEquals(PlanError.Kind.NO_GUILD_WITH_THIS_ID, plan.errors().get(0).kind());
        assertFalse(plan.isImportable());
        assertEquals(ImportResult.ImportError.Kind.PLAN_NOT_IMPORTABLE,
                service().execute(plan, ImportAnswers.defaults()).errors().get(0).kind());
        assertFalse(JournalDatabase.exists(alphaDir()));

        guildService.createGuild("Beta");
        Path betaFile = guildService.guildDir("Beta").resolve(GuildService.GUILD_FILE_NAME);
        GuildRepository.save(GuildRepository.load(betaFile, context.catalog()).withGameGuildId(OWN_GAME_ID), betaFile);
        setGuild(context.guild().withGameGuildId(null));

        ImportPlan other = service().prepare(files("de", SEP_24, LogDirection.ATTACK));

        assertEquals(PlanError.Kind.LOGS_OF_OTHER_GUILD, other.errors().get(0).kind());
        assertEquals("Beta", other.errors().get(0).guildName());
    }

    @Test
    @DisplayName("The journal of the open guild belongs to another game guild")
    void journalOfOtherGuild() throws Exception {
        assertTrue(service().execute(service().prepare(files("de", SEP_24, LogDirection.ATTACK)),
                ImportAnswers.defaults().withGuildLink(false)).isSuccess());
        assertNull(context.guild().gameGuildId(), "link declined");
        Path foreign = workspace.resolve("24-09-2026 Andere Gilde Server 7 (4711) - Das Schwarze Auge Server 79 (114834) "
                + "Sieg +851 Rangpunkte Angriffs-Log.csv");
        Files.copy(BattleLogTestFiles.file("de", SEP_24, LogDirection.ATTACK), foreign);

        ImportPlan plan = service().prepare(List.of(foreign));

        assertEquals(PlanError.Kind.JOURNAL_OF_OTHER_GUILD, plan.errors().get(0).kind());
        assertEquals(OWN_GAME_ID, plan.errors().get(0).gameGuildId());
    }

    // --- players (defense log only) ---

    @Test
    @DisplayName("Players from the defense log: exact, normalized (marked), similar, unknown; answers are applied")
    void players() throws Exception {
        setGuild(sampleMembers());

        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE));

        assertTrue(plan.autoAssignments().contains(new PlayerAutoAssignment("Puschel", "Puschel", false)));
        assertTrue(plan.autoAssignments().contains(new PlayerAutoAssignment("Team Gandagom", "team gandagom", true)));
        PlayerQuestion ordensritter = question(plan, "Ordensritter");
        assertEquals(List.of(new PlayerQuestion.NameSuggestion("Ordensriter", "Ordensriter", 1)),
                ordensritter.nameSuggestions());
        PlayerQuestion faern = question(plan, "Faern");
        assertEquals(List.of(), faern.nameSuggestions());
        assertTrue(faern.allowedAnswers().contains(PlayerAnswer.Kind.CREATE));
        for (String attackerOnly : List.of("Arya", "Haselnuss", "Serg", "lucky")) {
            assertTrue(plan.playerQuestions().stream().noneMatch(q -> q.rawName().equals(attackerOnly)),
                    attackerOnly + " only attacks - no question");
        }

        ImportResult result = service().execute(plan, ImportAnswers.defaults()
                .withPlayer(ordensritter.id(), PlayerAnswer.assign("Ordensriter"))
                .withPlayer(faern.id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE))
                .withPlayer(question(plan, "Xavior").id(), PlayerAnswer.of(PlayerAnswer.Kind.NOT_IN_COW2WIN)));

        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(List.of("Faern"), result.createdMembers());
        GuildMember created = member("Faern");
        assertEquals("Faern", created.name());
        assertEquals(List.of(), created.heroTeams(), "new members have no teams");
        assertEquals(List.of(), created.titanTeams());
        assertEquals(AssignmentStatus.ASSIGNED, assignment("Ordensritter").status());
        assertEquals("Ordensriter", assignment("Ordensritter").memberId());
        assertEquals("Faern", assignment("Faern").memberId());
        assertEquals("team gandagom", assignment("Team Gandagom").memberId());
        assertEquals(AssignmentStatus.NOT_IN_COW2WIN, assignment("Xavior").status());
        assertEquals(AssignmentStatus.OPEN, assignment("Vivien").status(), "no answer = open");
        assertTrue(repo().findOwnPlayer("Arya").isPresent(), "attackers are stored as players");
        assertTrue(repo().findAssignment(repo().findOwnPlayer("Arya").get().id()).isEmpty(), "...but never assigned");

        ImportPlan next = service().prepare(files("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE));
        Set<String> asked = new java.util.HashSet<>(next.playerQuestions().stream().map(PlayerQuestion::rawName).toList());
        assertFalse(asked.contains("Xavior"), "NOT_IN_COW2WIN is not asked again");
        assertFalse(asked.contains("Faern"));
        assertFalse(asked.contains("Ordensritter"));
        assertTrue(asked.contains("Vivien"), "OPEN is asked again");
    }

    @Test
    @DisplayName("Rename via team power: id, teams and power unchanged, only the name")
    void renameViaPower() throws Exception {
        // Puschel defends with team power 1.400.474 on 24.09.; OldName's titan team has 1.390.000 (within 3 %).
        GuildMember oldName = new GuildMember("OldName", "OldName", List.of(),
                List.of(new TitanTeam("OldName", 0, titans("brustar", "iyari", "mort"), 1_390_000)));
        setGuild(new Guild(context.guild().id(), context.guild().name(), List.of(oldName)));

        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));

        PlayerQuestion puschel = question(plan, "Puschel");
        PlayerQuestion.RenameSuggestion suggestion = puschel.renameSuggestions().get(0);
        assertEquals("OldName", suggestion.memberId());
        assertEquals(new PlayerQuestion.RenameEvidence(PlayerQuestion.RenameEvidence.Kind.POWER,
                org.c2w.data.journal.TeamKind.TITAN, 0, 1_400_474, 1_390_000), suggestion.evidence().get(0));

        ImportResult result = service().execute(plan, ImportAnswers.defaults()
                .withPlayer(puschel.id(), PlayerAnswer.rename("OldName")));

        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(List.of(new ImportResult.RenamedMember("OldName", "OldName", "Puschel")), result.renamedMembers());
        GuildMember renamed = member("OldName");
        assertEquals("Puschel", renamed.name());
        assertEquals(oldName.titanTeams(), renamed.titanTeams(), "teams and power unchanged");
        assertEquals(1, context.guild().members().size(), "no new member");
        assertEquals("OldName", assignment("Puschel").memberId());
    }

    @Test
    @DisplayName("Rename via units (17.09. defense log has full teams): same titans = strong evidence")
    void renameViaUnits() throws Exception {
        // First fight of 17.09. defense: Puschel defends with Brustar, Iyari, Mort, Solaris, Tenebris.
        GuildMember alt = new GuildMember("Alt", "Alt", List.of(), List.of(
                new TitanTeam("Alt", 0, titans("brustar", "iyari", "mort", "solaris", "tenebris"), 500_000)));
        setGuild(new Guild(context.guild().id(), context.guild().name(), List.of(alt)));

        ImportPlan plan = service().prepare(files("de", "17-09-2026", LogDirection.DEFENSE));

        PlayerQuestion.RenameSuggestion suggestion = question(plan, "Puschel").renameSuggestions().get(0);
        assertEquals("Alt", suggestion.memberId());
        assertTrue(suggestion.hasUnitMatch());
        assertEquals(PlayerQuestion.RenameEvidence.Kind.SAME_UNITS, suggestion.evidence().get(0).kind());
    }

    @Test
    @DisplayName("No rename suggestion without evidence, and none for members that occur in the defense log")
    void noRenameWithoutEvidence() throws Exception {
        GuildMember weak = new GuildMember("Weak", "Weak", List.of(),
                List.of(new TitanTeam("Weak", 0, titans("brustar"), 100_000)));
        GuildMember puschel = new GuildMember("Puschel", "Puschel", List.of(),
                List.of(new TitanTeam("Puschel", 0, titans("brustar"), 1_390_000)));
        setGuild(new Guild(context.guild().id(), context.guild().name(), List.of(weak, puschel)));

        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));

        assertTrue(plan.playerQuestions().stream().flatMap(q -> q.renameSuggestions().stream())
                .noneMatch(s -> s.memberId().equals("Puschel")), "Puschel defends in this log");
        assertTrue(plan.playerQuestions().stream().flatMap(q -> q.renameSuggestions().stream())
                .noneMatch(s -> s.memberId().equals("Weak")));
    }

    @Test
    @DisplayName("An import with attack log changes no teams, power, pets or totems")
    void attackLogChangesNothing() throws Exception {
        setGuild(sampleMembers());
        Guild before = context.guild();

        ImportResult result = service().execute(
                service().prepare(files("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE)), ImportAnswers.defaults());

        assertTrue(result.isSuccess());
        assertEquals(before.members(), context.guild().members());
        assertEquals(before.withGameGuildId(OWN_GAME_ID), context.guild());
    }

    @Test
    @DisplayName("Member limit: no CREATE with 30 members, and at most 30 in total")
    void memberLimit() throws Exception {
        setGuild(new Guild(context.guild().id(), context.guild().name(), dummyMembers(Guild.MAX_MEMBERS)));
        ImportPlan full = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));
        PlayerQuestion q = question(full, "Faern");
        assertFalse(q.allowedAnswers().contains(PlayerAnswer.Kind.CREATE));
        assertEquals(ImportResult.ImportError.Kind.ANSWER_NOT_ALLOWED, service().execute(full,
                ImportAnswers.defaults().withPlayer(q.id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE))).errors().get(0).kind());

        setGuild(new Guild(context.guild().id(), context.guild().name(), dummyMembers(Guild.MAX_MEMBERS - 1)));
        ImportPlan almost = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));
        ImportResult refused = service().execute(almost, ImportAnswers.defaults()
                .withPlayer(question(almost, "Faern").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE))
                .withPlayer(question(almost, "Vivien").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE)));
        assertEquals(ImportResult.ImportError.Kind.MEMBER_LIMIT, refused.errors().get(0).kind());
        assertFalse(JournalDatabase.exists(alphaDir()), "nothing written");
        assertEquals(Guild.MAX_MEMBERS - 1, context.guild().members().size());
    }

    @Test
    @DisplayName("Invalid answers are refused and change nothing")
    void invalidAnswers() throws Exception {
        setGuild(sampleMembers());
        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));
        Guild before = context.guild();

        assertEquals(ImportResult.ImportError.Kind.UNKNOWN_MEMBER, service().execute(plan, ImportAnswers.defaults()
                .withPlayer(question(plan, "Faern").id(), PlayerAnswer.assign("nobody"))).errors().get(0).kind());
        assertEquals(ImportResult.ImportError.Kind.DUPLICATE_RENAME, service().execute(plan, ImportAnswers.defaults()
                .withPlayer(question(plan, "Faern").id(), PlayerAnswer.rename("Puschel"))
                .withPlayer(question(plan, "Vivien").id(), PlayerAnswer.rename("Puschel"))).errors().get(0).kind());
        assertEquals(before, context.guild());
        assertFalse(context.isGuildDirty());
        assertFalse(JournalDatabase.exists(alphaDir()));
    }

    // --- unknown names ---

    @Test
    @DisplayName("Unknown hero: question, mapping stored, log saved with the id, no question next time")
    void unknownName() throws Exception {
        Path original = BattleLogTestFiles.file("de", SEP_24, LogDirection.ATTACK);
        Path dir = Files.createDirectories(workspace.resolve("import"));
        Path file = dir.resolve(original.getFileName().toString());
        Files.writeString(file, Files.readString(original, StandardCharsets.UTF_8).replace("Lara Croft |", "Neuheld |"),
                StandardCharsets.UTF_8);

        ImportPlan plan = service().prepare(List.of(file));

        UnknownNameQuestion q = plan.unknownNames().stream().filter(n -> n.rawName().equals("Neuheld"))
                .findFirst().orElseThrow();
        assertEquals(NameMappingKind.HERO, q.kind());
        assertTrue(q.occurrences() > 0);
        assertEquals(ImportResult.ImportError.Kind.INVALID_CATALOG_ID,
                service().execute(plan, ImportAnswers.defaults().withName(q.id(), "no-such-hero")).errors().get(0).kind());

        ImportResult result = service().execute(plan, ImportAnswers.defaults().withName(q.id(), "lara"));

        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(1, result.nameMappings());
        assertEquals("lara", repo().findNameMapping(NameMappingKind.HERO, "Neuheld").orElseThrow().catalogId());
        List<FightUnit> units = new ArrayList<>();
        repo().loadLog(result.battles().get(0).battleId(), LogDirection.ATTACK).orElseThrow().log().fights()
                .forEach(f -> { units.addAll(f.attacker().units()); units.addAll(f.defender().units()); });
        assertTrue(units.stream().filter(u -> u.name().equals("Neuheld")).allMatch(u -> "lara".equals(u.catalogId())));
        assertTrue(service().prepare(List.of(file)).unknownNames().isEmpty(), "mapping applies next time");
    }

    // --- failure and robustness ---

    @Test
    @DisplayName("Writing fails: rollback, guild and dirty state as before")
    void writeFailureRollsBack() throws Exception {
        setGuild(sampleMembers());
        Guild before = context.guild();
        Path otherDir = Files.createDirectories(workspace.resolve("broken"));
        JournalDatabase closed = JournalDatabase.open(otherDir, true).orElseThrow();
        JournalRepository broken = new JournalRepository(closed);
        closed.close();
        JournalImportService failing = new JournalImportService(context, guildService,
                create -> create ? Optional.of(broken) : Optional.empty(), BattleLogTestFiles::parser);

        ImportPlan plan = failing.prepare(files("de", SEP_24, LogDirection.DEFENSE));
        ImportResult result = failing.execute(plan, ImportAnswers.defaults()
                .withPlayer(question(plan, "Faern").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE)));

        assertEquals(ImportResult.ImportError.Kind.WRITE_FAILED, result.errors().get(0).kind());
        assertEquals(before, context.guild(), "new member and guild link taken back");
        assertFalse(context.isGuildDirty(), "dirty state as before");
    }

    @Test
    @DisplayName("Discarded guild change: an assignment to a member that no longer exists is asked again")
    void discardedGuildChange() throws Exception {
        Guild original = context.guild();
        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));
        assertTrue(service().execute(plan, ImportAnswers.defaults()
                .withPlayer(question(plan, "Faern").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE))).isSuccess());
        assertEquals("Faern", assignment("Faern").memberId());

        setGuild(original.withGameGuildId(OWN_GAME_ID)); // user discards the new member

        ImportPlan next = service().prepare(files("de", SEP_24, LogDirection.DEFENSE));
        assertTrue(next.playerQuestions().stream().anyMatch(q -> q.rawName().equals("Faern")));
    }

    @Test
    @DisplayName("The plan belongs to the guild it was made for")
    void guildChangedBetweenPrepareAndExecute() throws Exception {
        ImportPlan plan = service().prepare(files("de", SEP_24, LogDirection.ATTACK));
        guildService.createGuild("Beta");
        guildService.switchToGuild("Beta");

        assertEquals(ImportResult.ImportError.Kind.GUILD_CHANGED,
                service().execute(plan, ImportAnswers.defaults()).errors().get(0).kind());
    }

    @Test
    void helpers() {
        assertEquals(1, JournalImportService.levenshtein("ordensritter", "ordensriter"));
        assertEquals(0, JournalImportService.levenshtein("", ""));
        assertEquals(3, JournalImportService.levenshtein("abc", ""));
        List<GuildMember> members = List.of(new GuildMember("Faern", "Faern", List.of(), List.of()),
                new GuildMember("Faern (2)", "x", List.of(), List.of()));
        assertEquals("Faern (3)", JournalImportService.uniqueMemberId("Faern", members));
        assertEquals("Vale", JournalImportService.uniqueMemberId("Vale", members));
    }

    // --- helpers ---

    private JournalImportService service() {
        return new JournalImportService(context, guildService, context.journal(), BattleLogTestFiles::parser);
    }

    private JournalRepository repo() throws JournalException {
        return context.journal().repository(false).orElseThrow();
    }

    private Path alphaDir() {
        return guildService.guildDir("Alpha");
    }

    private static List<Path> files(String folder, String day, LogDirection... directions) {
        List<Path> result = new ArrayList<>();
        for (LogDirection direction : directions) {
            result.add(BattleLogTestFiles.file(folder, day, direction));
        }
        return result;
    }

    private void setGuild(Guild guild) {
        context.setGuild(guild);
        context.setGuildDirty(false);
    }

    /** Members for the 24.09. defense log: Puschel (exact), team gandagom (normalized), Ordensriter (typo), with teams. */
    private Guild sampleMembers() {
        GuildMember puschel = new GuildMember("Puschel", "Puschel",
                List.of(new HeroTeam("Puschel", 0, List.of(context.catalog().heroes().findById("dante").orElseThrow()),
                        900_000)),
                List.of(new TitanTeam("Puschel", 0, titans("brustar"), 1_200_000)));
        GuildMember gandagom = new GuildMember("team gandagom", "team gandagom", List.of(), List.of());
        GuildMember ordensriter = new GuildMember("Ordensriter", "Ordensriter", List.of(), List.of());
        return new Guild(context.guild().id(), context.guild().name(), List.of(puschel, gandagom, ordensriter));
    }

    private static List<GuildMember> dummyMembers(int count) {
        List<GuildMember> members = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            members.add(new GuildMember("m" + i, "Dummy " + i, List.of(), List.of()));
        }
        return members;
    }

    private List<Titan> titans(String... ids) {
        List<Titan> result = new ArrayList<>();
        for (String id : ids) {
            result.add(context.catalog().titans().findById(id).orElseThrow());
        }
        return result;
    }

    private static PlayerQuestion question(ImportPlan plan, String rawName) {
        return plan.playerQuestions().stream().filter(q -> q.rawName().equals(rawName)).findFirst()
                .orElseThrow(() -> new AssertionError("no question for " + rawName));
    }

    private GuildMember member(String id) {
        return context.guild().members().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    private PlayerAssignment assignment(String rawName) throws JournalException {
        JournalPlayer player = repo().findOwnPlayer(rawName).orElseThrow();
        return repo().findAssignment(player.id()).orElseThrow(() -> new AssertionError("no assignment for " + rawName));
    }
}
