package org.c2w.service;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.PlayerAssignment;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.infra.Config;
import org.c2w.service.journal.*;
import org.c2w.service.journal.SyncPlan.EmptyReason;
import org.c2w.service.journal.SyncPlan.Source;
import org.c2w.service.journal.SyncRow.Certainty;
import org.c2w.service.journal.SyncRow.LineupHint;
import org.c2w.service.journal.SyncSelection.Choice;
import org.c2w.service.journal.TeamBuildPlan.SkipReason;
import org.c2w.service.journal.TeamBuildPlan.SkippedPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JournalSyncService}: with the real sample logs and the real guild
 * "Deutscher Bund" (fixture {@code guild/deutscher-bund.json}), and with
 * constructed in-memory logs for the matching rules.
 */
class JournalSyncServiceTest {

    static final Path FIXTURE = JournalTeamBuilderServiceTest.FIXTURE;

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private JournalSyncService service;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Leer");
        guildService.switchToGuild("Leer");
        service = new JournalSyncService(context, guildService);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    /** Opens a copy of the real guild "Deutscher Bund". */
    private void openFixture() throws Exception {
        Path folder = Files.createDirectories(workspace.resolve("Deutscher Bund"));
        Files.copy(FIXTURE, folder.resolve("guild.json"));
        guildService.switchToGuild("Deutscher Bund");
    }

    /** Imports {@code files} with the default answers (open questions stay open) and saves the guild. */
    private void importFiles(List<Path> files) throws Exception {
        JournalImportService importer = new JournalImportService(context, guildService, context.journal(),
                BattleLogTestFiles::parser);
        ImportPlan plan = importer.prepare(files);
        ImportResult result = importer.execute(plan, ImportAnswers.defaults());
        assertTrue(result.isSuccess(), result.errors().toString());
        guildService.saveGuild();
    }

    private static Path de(String day, LogDirection direction) {
        return BattleLogTestFiles.file("de", day, direction);
    }

    private static List<Path> both(String day) {
        return List.of(de(day, LogDirection.ATTACK), de(day, LogDirection.DEFENSE));
    }

    // =====================================================================
    // source
    // =====================================================================

    @Test
    @DisplayName("Source: the newest battle with a defense log; only an attack log in the newest -> the one before")
    void source() throws Exception {
        openFixture();
        assertEquals(EmptyReason.NO_JOURNAL, service.prepare().emptyReason());
        assertFalse(context.journal().repository(false).isPresent(), "prepare creates no journal");

        importFiles(List.of(de("01-10-2026", LogDirection.ATTACK), de("24-09-2026", LogDirection.ATTACK)));
        assertEquals(EmptyReason.NO_DEFENSE_LOG, service.prepare().emptyReason());

        importFiles(List.of(de("21-09-2026", LogDirection.DEFENSE), de("28-09-2026", LogDirection.DEFENSE),
                de("14-09-2026", LogDirection.DEFENSE)));
        SyncPlan plan = service.prepare();
        assertNull(plan.emptyReason());
        assertEquals(LocalDate.of(2026, 9, 28), plan.source().date(), "01.10. has only the attack log");
        assertEquals("Rebel Alliance", plan.source().opponent().name());
        assertEquals("deutsch", plan.source().language());
        assertNotNull(plan.source().importedAt());
        assertEquals(Optional.of(LocalDate.of(2026, 9, 28)), service.newestDefenseBattleDate());

        importFiles(List.of(de("01-10-2026", LogDirection.DEFENSE)));
        assertEquals(LocalDate.of(2026, 10, 1), service.prepare().source().date());
    }

    // =====================================================================
    // real data
    // =====================================================================

    @Test
    @DisplayName("Real data: 'Deutscher Bund' against the running battle of 01.10.")
    void realData() throws Exception {
        openFixture();
        importFiles(both("01-10-2026"));
        Guild before = context.guild();

        SyncPlan plan = service.prepare();

        assertEquals(LocalDate.of(2026, 10, 1), plan.source().date());
        Map<Certainty, Long> counts = new EnumMap<>(Certainty.class);
        for (Certainty c : Certainty.values()) {
            counts.put(c, plan.count(c));
        }
        long unchanged = plan.rows().stream().filter(SyncRow::unchanged).count();
        List<String> changes = plan.rows().stream().filter(r -> !r.unchanged())
                .map(r -> r.memberName() + " " + r.kind() + " " + r.suggestedIndex() + " " + r.suggested().storedPower()
                        + " -> " + r.logPower() + " " + r.certainty() + String.format(" %.2f%%", 100 * r.deviation(
                        r.suggestedIndex())) + (r.suggested().editedSinceBattle() ? " edited-since" : "")
                        + (r.preselectPower() ? " preselected" : ""))
                .toList();
        System.out.println("Sync real data 01.10.: rows=" + plan.rows().size() + " " + counts + ", unchanged="
                + unchanged + ", changed=" + changes.size() + ", preselected="
                + plan.rows().stream().filter(SyncRow::preselectPower).count() + ", unmatched=" + plan.unmatched()
                + ", skipped=" + plan.skippedPlayers() + ", not in log=" + plan.membersNotInLog().size()
                + ", stored teams not in log=" + plan.teamsNotInLog().size() + ", power conflicts="
                + plan.powerConflicts().size());
        changes.forEach(c -> System.out.println("  " + c));

        assertEquals(List.of(), plan.unmatched(), "every log team of an assigned player has its stored team");
        assertEquals(0, counts.get(Certainty.UNITS), "the defense log of 01.10. has no units");
        assertEquals(0, counts.get(Certainty.UNSURE));
        assertTrue(unchanged >= plan.rows().size() - 10, "almost every team is unchanged: " + unchanged);
        // numbers of the fixture (guild of 04.10.2026) against the running state of 01.10.
        assertEquals(42, plan.rows().size());
        assertEquals(42, counts.get(Certainty.SURE));
        assertEquals(36, unchanged);
        assertEquals(List.of(), plan.skippedPlayers());
        SyncRow puschel = plan.rows().stream()
                .filter(r -> r.memberName().equals("Puschel") && r.kind() == TeamKind.TITAN && r.logPower() == 1_400_474)
                .findFirst().orElseThrow();
        assertEquals(Certainty.SURE, puschel.certainty());
        assertEquals(1_376_972, puschel.suggested().storedPower());
        assertTrue(plan.rows().stream().filter(r -> !r.unchanged()).allMatch(r -> r.deviation(r.suggestedIndex())
                <= JournalSyncService.SURE_TOLERANCE), "every change within 3 %");

        // the preselection: only those rows change, with lastModified = battle day; the guild is unsaved
        SyncSelection preselected = SyncSelection.preselected(plan);
        SyncResult result = service.apply(plan, preselected);
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(preselected.selectedCount(), result.teamsChanged());
        assertEquals(0, result.unitsChanged());
        assertTrue(result.teamsChanged() == 0 || context.isGuildDirty());
        assertOnlyChanged(before, context.guild(), plan, preselected);
    }

    /** Every team of {@code after} equals {@code before}, except the selected ones (power, lastModified). */
    private static void assertOnlyChanged(Guild before, Guild after, SyncPlan plan, SyncSelection selection) {
        Map<String, Choice> byTeam = new HashMap<>();
        Map<String, SyncRow> rowByTeam = new HashMap<>();
        selection.choices().forEach((id, c) -> {
            if (c.any()) {
                SyncRow row = plan.row(id).orElseThrow();
                byTeam.put(row.memberId() + row.kind() + c.teamIndex(), c);
                rowByTeam.put(row.memberId() + row.kind() + c.teamIndex(), row);
            }
        });
        assertEquals(before.members().size(), after.members().size());
        for (int m = 0; m < before.members().size(); m++) {
            GuildMember b = before.members().get(m);
            GuildMember a = after.members().get(m);
            assertEquals(b.id(), a.id());
            assertEquals(b.name(), a.name());
            for (HeroTeam t : b.heroTeams()) {
                HeroTeam now = a.heroTeams().get(t.index());
                Choice c = byTeam.get(b.id() + TeamKind.HERO + t.index());
                if (c == null) {
                    assertEquals(t, now, b.name());
                } else {
                    assertEquals(c.power() ? rowByTeam.get(b.id() + TeamKind.HERO + t.index()).logPower()
                            : t.totalPower(), now.totalPower());
                    assertEquals(plan.source().date(), now.lastModified());
                    assertEquals(t.warFlag(), now.warFlag(), "war flag never changes");
                    if (!c.units()) {
                        assertEquals(t.heroes(), now.heroes());
                        assertEquals(t.pet(), now.pet());
                    }
                }
            }
            for (TitanTeam t : b.titanTeams()) {
                TitanTeam now = a.titanTeams().get(t.index());
                Choice c = byTeam.get(b.id() + TeamKind.TITAN + t.index());
                if (c == null) {
                    assertEquals(t, now, b.name());
                } else {
                    assertEquals(c.power() ? rowByTeam.get(b.id() + TeamKind.TITAN + t.index()).logPower()
                            : t.totalPower(), now.totalPower());
                    assertEquals(plan.source().date(), now.lastModified());
                    if (!c.units()) {
                        assertEquals(t.titans(), now.titans());
                        assertEquals(t.totems(), now.totems());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Units (17.09. as newest battle): same titans/heroes -> UNITS; other composition -> change, taken over when chosen")
    void unitsFromTheDefenseLog() throws Exception {
        openFixture();
        importFiles(both("17-09-2026"));

        SyncPlan plan = service.prepare();
        System.out.println("Sync real data 17.09.: rows=" + plan.rows().size() + " UNITS=" + plan.count(Certainty.UNITS)
                + " SURE=" + plan.count(Certainty.SURE) + " UNSURE=" + plan.count(Certainty.UNSURE) + " AMBIGUOUS="
                + plan.count(Certainty.AMBIGUOUS) + ", unmatched=" + plan.unmatched().size()
                + ", with unit changes=" + plan.rows().stream().filter(r -> r.suggested().unitsChange() != null).count());

        SyncRow puschel = row(plan, "Puschel", TeamKind.TITAN, 1_382_741);
        assertEquals(Certainty.UNITS, puschel.certainty());
        assertEquals(1_376_972, puschel.suggested().storedPower());
        assertNull(puschel.suggested().unitsChange(), "same titans and totems");
        assertTrue(puschel.suggested().editedSinceBattle(), "the fixture was edited on 01./02.10.");
        assertFalse(puschel.preselectPower(), "edited since the battle");

        SyncRow je = row(plan, "Team JE", TeamKind.HERO, 1_093_024);
        assertEquals(Certainty.UNITS, je.certainty());
        assertEquals(1_095_947, je.suggested().storedPower());
        assertNull(je.suggested().unitsChange(), "same heroes and pet Albus");

        // another composition: Puschel's titan team gets one titan replaced -> matched by power, units differ
        GuildMember member = member("Puschel");
        TitanTeam stored = member.titanTeams().get(puschel.suggestedIndex());
        Titan replaced = stored.titans().get(0);
        Titan other = context.catalog().titans().findAll().stream()
                .filter(t -> stored.titans().stream().noneMatch(s -> s.id().equals(t.id())))
                .filter(t -> t.element() == replaced.element()).findFirst().orElseThrow();
        List<Titan> titans = new ArrayList<>(stored.titans());
        titans.set(0, other);
        List<TitanTeam> titanTeams = new ArrayList<>(member.titanTeams());
        titanTeams.set(stored.index(), new TitanTeam(member.id(), stored.index(), titans, stored.totalPower(),
                LocalDate.of(2026, 9, 1), stored.totems()));
        replaceMember(new GuildMember(member.id(), member.name(), member.heroTeams(), titanTeams));
        Guild before = context.guild();

        plan = service.prepare();
        SyncRow changed = row(plan, "Puschel", TeamKind.TITAN, 1_382_741);
        assertEquals(Certainty.SURE, changed.certainty());
        SyncRow.UnitsChange change = changed.suggested().unitsChange();
        assertNotNull(change);
        assertEquals(List.of(replaced.id()), change.added());
        assertEquals(List.of(other.id()), change.removed());
        assertTrue(changed.suggested().unitsSelectable());
        assertTrue(changed.preselectPower());
        assertFalse(SyncSelection.preselected(plan).choices().get(changed.id()).units(), "units never preselected");

        SyncSelection selection = SyncSelection.none().with(changed.id(),
                new Choice(changed.suggestedIndex(), true, true));
        SyncResult result = service.apply(plan, selection);
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(1, result.unitsChanged());
        TitanTeam after = member("Puschel").titanTeams().get(changed.suggestedIndex());
        assertEquals(changed.logUnits().unitIds(), after.titans().stream().map(Titan::id).toList(), "log order");
        assertEquals(1_382_741, after.totalPower());
        assertEquals(LocalDate.of(2026, 9, 17), after.lastModified());
        assertOnlyChanged(before, context.guild(), plan, selection);
    }

    private static SyncRow row(SyncPlan plan, String member, TeamKind kind, int logPower) {
        return plan.rows().stream().filter(r -> r.memberName().equals(member) && r.kind() == kind
                && r.logPower() == logPower).findFirst().orElseThrow(() -> new AssertionError(member + " " + logPower
                + " not in " + plan.rows().stream().map(r -> r.memberName() + " " + r.logPower()).toList()));
    }

    @Test
    @DisplayName("Never from the attack log: the plan only depends on the defense log")
    void neverFromTheAttackLog() throws Exception {
        openFixture();
        importFiles(List.of(de("24-09-2026", LogDirection.DEFENSE)));
        SyncPlan defenseOnly = service.prepare();
        importFiles(List.of(de("24-09-2026", LogDirection.ATTACK), de("01-10-2026", LogDirection.ATTACK)));
        SyncPlan withAttack = service.prepare();

        assertEquals(LocalDate.of(2026, 9, 24), withAttack.source().date());
        assertEquals(defenseOnly.rows(), withAttack.rows());
        assertEquals(defenseOnly.unmatched(), withAttack.unmatched());
    }

    // =====================================================================
    // matching rules with in-memory logs
    // =====================================================================

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    private static final Source SOURCE = new Source(1, DAY, null, null, null, "deutsch", "test.csv",
            LocalDateTime.of(2026, 9, 25, 10, 0));
    private static final List<String> HEROES = List.of("dante", "aurora", "nebula", "sebastian", "martha");
    private static final List<String> HEROES_2 = List.of("galahad", "yasmine", "maya", "jhu", "astaroth");
    private static final List<String> HEROES_3 = List.of("keira", "morrigan", "isaac", "fafnir", "celeste");

    @Test
    @DisplayName("Power: exact -> unchanged; 1.7 % -> SURE preselected; 6 % -> UNSURE; 15 % -> no match; two close teams -> AMBIGUOUS")
    void powerMatching() {
        Guild guild = new Guild("g", "G", List.of(
                heroMember("a", 1_000_000),
                heroMember("b", 983_000),
                heroMember("c", 940_000),
                heroMember("d", 850_000),
                heroMember("e", 990_000, 992_000)));
        BattleLog log = log(defense("A", "citadel", 1, 1_000_000), defense("B", "citadel", 2, 1_000_000),
                defense("C", "citadel", 3, 1_000_000), defense("D", "citadel", 4, 1_000_000),
                defense("E", "citadel", 5, 1_000_000));

        SyncPlan plan = plan(log, guild, assignedByLowerCase("A", "B", "C", "D", "E"), null);

        SyncRow a = plan.rowsOf("a").get(0);
        assertEquals(Certainty.SURE, a.certainty());
        assertTrue(a.unchanged());
        assertFalse(a.preselectPower());
        SyncRow b = plan.rowsOf("b").get(0);
        assertEquals(Certainty.SURE, b.certainty());
        assertEquals(0.017, b.deviation(0), 1e-9);
        assertTrue(b.preselectPower());
        SyncRow c = plan.rowsOf("c").get(0);
        assertEquals(Certainty.UNSURE, c.certainty());
        assertFalse(c.preselectPower());
        assertEquals(List.of(), plan.rowsOf("d"));
        assertEquals(List.of(new SyncPlan.UnmatchedTeam("d", "D", TeamKind.HERO, 1_000_000, "citadel", "citadel", 4)),
                plan.unmatched());
        assertEquals(List.of(new SyncPlan.TeamRef("d", TeamKind.HERO, 0), new SyncPlan.TeamRef("e", TeamKind.HERO, 0)),
                plan.teamsNotInLog(), "d: too far away; e: the other of the two close teams");
        SyncRow e = plan.rowsOf("e").get(0);
        assertEquals(Certainty.AMBIGUOUS, e.certainty());
        assertEquals(1, e.suggestedIndex(), "the closer one");
        assertFalse(e.preselectPower());

        SyncSelection preselected = SyncSelection.preselected(plan);
        assertEquals(1, preselected.selectedCount());
        assertTrue(preselected.choices().get(b.id()).power());
    }

    @Test
    @DisplayName("One-to-one: crossed distances get the optimal pairing, not 'each takes the nearest'")
    void optimalPairing() {
        // log A 1.000.000, B 1.050.000; stored X 1.030.000 (index 0), Y 960.000 (index 1):
        // nearest for both is X; optimal: A-Y (4 %) + B-X (1.9 %) instead of A-X (3 %) + B-Y (8.6 %)
        Guild guild = new Guild("g", "G", List.of(heroMember("max", 1_030_000, 960_000)));
        BattleLog log = log(defense("Max", "citadel", 1, 1_000_000), defense("Max", "citadel", 2, 1_050_000));

        SyncPlan plan = plan(log, guild, Map.of("Max", assigned("max")), null);

        Map<Integer, Integer> targetByPower = plan.rows().stream()
                .collect(Collectors.toMap(SyncRow::logPower, SyncRow::suggestedIndex));
        assertEquals(Map.of(1_000_000, 1, 1_050_000, 0), targetByPower);
        assertEquals(Certainty.UNSURE, plan.rows().stream().filter(r -> r.logPower() == 1_000_000).findFirst()
                .orElseThrow().certainty());
    }

    @Test
    @DisplayName("Empty team: only the power can be taken over; edited since the battle: not preselected")
    void emptyTeamAndEditedSince() {
        GuildMember max = new GuildMember("max", "Max",
                List.of(new HeroTeam("max", 0, List.of(), null, null, 990_000, null)),
                List.of(new TitanTeam("max", 0, titans(TITANS), 1_190_000, DAY.plusDays(1), null)));
        Guild guild = new Guild("g", "G", List.of(max));
        BattleLog log = log(defense("Max", "citadel", 1, 1_000_000, heroUnits(HEROES, "axel")),
                defense("Max", "bridge", 1, 1_200_000));

        SyncPlan plan = plan(log, guild, Map.of("Max", assigned("max")), null);

        SyncRow hero = plan.rows().stream().filter(r -> r.kind() == TeamKind.HERO).findFirst().orElseThrow();
        assertEquals(Certainty.SURE, hero.certainty());
        assertTrue(hero.suggested().empty());
        assertNotNull(hero.logUnits());
        assertNotNull(hero.suggested().unitsChange());
        assertFalse(hero.suggested().unitsSelectable(), "empty teams are filled by 'build teams from logs'");
        assertTrue(hero.preselectPower());

        SyncRow titan = plan.rows().stream().filter(r -> r.kind() == TeamKind.TITAN).findFirst().orElseThrow();
        assertEquals(Certainty.SURE, titan.certainty());
        assertTrue(titan.suggested().editedSinceBattle());
        assertFalse(titan.preselectPower());
    }

    @Test
    @DisplayName("Skipped defenders: open, unknown, not in Cow2Win, former, member missing; members not in the log")
    void skipped() {
        Guild guild = new Guild("g", "G", List.of(heroMember("x", 1), heroMember("y", 1)));
        BattleLog log = log(defense("A", "citadel", 1, 1), defense("B", "citadel", 2, 1),
                defense("C", "citadel", 3, 1), defense("D", "citadel", 4, 1), defense("E", "citadel", 5, 1),
                defense("Y", "citadel", 6, 1));
        Map<String, PlayerAssignment> assignments = Map.of(
                "B", new PlayerAssignment(2, null, AssignmentStatus.NOT_IN_COW2WIN, null),
                "C", new PlayerAssignment(3, null, AssignmentStatus.FORMER, null),
                "D", assigned("gone"),
                "E", new PlayerAssignment(5, null, AssignmentStatus.OPEN, null),
                "Y", assigned("y"));

        SyncPlan plan = plan(log, guild, assignments, null);

        assertEquals(List.of(new SkippedPlayer("A", SkipReason.NOT_ASSIGNED, null),
                new SkippedPlayer("B", SkipReason.NOT_IN_COW2WIN, null),
                new SkippedPlayer("C", SkipReason.FORMER, null),
                new SkippedPlayer("D", SkipReason.MEMBER_MISSING, "gone"),
                new SkippedPlayer("E", SkipReason.NOT_ASSIGNED, null)), plan.skippedPlayers());
        assertEquals(List.of("x"), plan.membersNotInLog());
        assertEquals(1, plan.rows().size());
        assertEquals("y", plan.rows().get(0).memberId());
    }

    @Test
    @DisplayName("Renamed player: both log names of a member are merged")
    void renamedPlayer() {
        Guild guild = new Guild("g", "G", List.of(heroMember("max", 1_000_000, 900_000)));
        BattleLog log = log(defense("Max", "citadel", 1, 1_000_000), defense("Maxi", "citadel", 2, 905_000));

        SyncPlan plan = plan(log, guild, Map.of("Max", assigned("max"), "Maxi", assigned("max")), null);

        assertEquals(2, plan.rows().size());
        assertEquals(Set.of(0, 1), plan.rows().stream().map(SyncRow::suggestedIndex).collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("Different powers at one position: hint, the last power is used")
    void powerConflict() {
        Guild guild = new Guild("g", "G", List.of(heroMember("max", 1_000_000)));
        BattleLog log = log(defense("Max", "citadel", 1, 1_000_000), defense("Max", "citadel", 1, 1_010_000));

        SyncPlan plan = plan(log, guild, Map.of("Max", assigned("max")), null);

        assertEquals(1, plan.rows().size());
        assertEquals(1_010_000, plan.rows().get(0).logPower());
        assertEquals(List.of(1_000_000, 1_010_000), plan.powerConflicts().get(0).powers());
    }

    @Test
    @DisplayName("Lineup hint: team in another fortification; team not in the lineup; no hint where it fits")
    void lineupHint() {
        Guild guild = new Guild("g", "G", List.of(heroMember("max", 1_000_000, 900_000, 800_000)));
        BattleLog log = log(defense("Max", "citadel", 1, 1_000_000), defense("Max", "citadel", 2, 900_000),
                defense("Max", "barracks", 1, 800_000));
        Lineup lineup = new Lineup("g", "G", "", null, List.of(
                new Lineup.Entry("barracks", "max", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("barracks", "max", Lineup.TeamType.HERO, 2)));

        SyncPlan plan = plan(log, guild, Map.of("Max", assigned("max")), lineup);

        Map<Integer, LineupHint> hints = new HashMap<>();
        plan.rows().forEach(r -> hints.put(r.suggestedIndex(), r.suggested().lineupHint()));
        assertEquals(new LineupHint(LineupHint.Kind.OTHER_FORTIFICATION, "barracks"), hints.get(0));
        assertEquals(new LineupHint(LineupHint.Kind.NOT_IN_LINEUP, null), hints.get(1));
        assertNull(hints.get(2));
        assertNull(plan(log, guild, Map.of("Max", assigned("max")), null).rows().get(0).suggested().lineupHint());
    }

    // --- apply ---

    @Test
    @DisplayName("apply: only selected rows, lastModified = battle day, war flag kept; the same target twice -> nothing changes")
    void apply() throws Exception {
        WarFlag flag = context.catalog().warFlags().findAll().get(0);
        GuildMember max = new GuildMember("max", "Max", List.of(
                new HeroTeam("max", 0, heroes(HEROES), pet("axel"), flag, 980_000, LocalDate.of(2026, 9, 1)),
                new HeroTeam("max", 1, heroes(HEROES_2), null, null, 880_000, LocalDate.of(2026, 9, 1))),
                List.of());
        GuildMember moritz = new GuildMember("moritz", "Moritz", List.of(
                new HeroTeam("moritz", 0, heroes(HEROES), 700_000)), List.of());
        Guild before = setGuild(max, moritz);
        BattleLog log = log(defense("Max", "citadel", 1, 1_000_000, heroUnits(HEROES_3, "fenris")),
                defense("Max", "citadel", 2, 900_000), defense("Moritz", "citadel", 3, 710_000));
        SyncPlan plan = plan(log, before, Map.of("Max", assigned("max"), "Moritz", assigned("moritz")), null);
        SyncRow first = plan.rows().stream().filter(r -> r.logPower() == 1_000_000).findFirst().orElseThrow();
        SyncRow second = plan.rows().stream().filter(r -> r.logPower() == 900_000).findFirst().orElseThrow();
        assertEquals(0, first.suggestedIndex());
        assertEquals(Certainty.SURE, first.certainty());

        // the same team twice -> error, nothing changed
        SyncSelection twice = SyncSelection.none().with(first.id(), new Choice(0, true, false))
                .with(second.id(), new Choice(0, true, false));
        SyncResult failed = service.apply(plan, twice);
        assertFalse(failed.isSuccess());
        assertEquals(SyncResult.Error.Kind.TARGET_TWICE, failed.errors().get(0).kind());
        assertSame(before, context.guild());
        assertFalse(context.isGuildDirty());
        assertEquals(failed.errors(), JournalSyncService.check(before, plan, twice, context.catalog()));

        // units + power for the first team, nothing for Moritz
        SyncSelection selection = SyncSelection.none().with(first.id(), new Choice(0, true, true))
                .with(second.id(), new Choice(1, true, false));
        SyncResult result = service.apply(plan, selection);
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(2, result.teamsChanged());
        assertEquals(2, result.powerChanged());
        assertEquals(1, result.unitsChanged());
        assertTrue(context.isGuildDirty());
        HeroTeam t0 = member("max").heroTeams().get(0);
        assertEquals(HEROES_3, t0.heroes().stream().map(Hero::id).toList());
        assertEquals("fenris", t0.pet().id());
        assertEquals(flag, t0.warFlag(), "war flag kept");
        assertEquals(1_000_000, t0.totalPower());
        assertEquals(DAY, t0.lastModified());
        HeroTeam t1 = member("max").heroTeams().get(1);
        assertEquals(900_000, t1.totalPower());
        assertEquals(DAY, t1.lastModified());
        assertEquals(heroes(HEROES_2), t1.heroes());
        assertEquals(moritz, member("moritz"));
        assertOnlyChanged(before, context.guild(), plan, selection);
    }

    @Test
    @DisplayName("apply: a pet another hero team has is not taken over; an invalid totem is dropped; both reported")
    void petAndTotems() throws Exception {
        List<Titan> titans = titans(TITANS);
        TitanElement notAllowed = Arrays.stream(TitanElement.values())
                .filter(e -> !TitanTeam.eligibleTotems(titans).contains(e)).findFirst().orElseThrow();
        GuildMember max = new GuildMember("max", "Max", List.of(
                new HeroTeam("max", 0, heroes(HEROES), pet("axel"), null, 1_000_000, null),
                new HeroTeam("max", 1, heroes(HEROES_2), pet("fenris"), null, 900_000, null)),
                List.of(new TitanTeam("max", 0, titans, 1_200_000, null, null)));
        Guild before = setGuild(max);
        // the log: team 1 with pet axel (team 0 has it), the titan team with a totem the titans do not allow
        BattleLog log = log(defense("Max", "citadel", 1, 905_000, heroUnits(HEROES_2, "axel")),
                defense("Max", "bridge", 1, 1_210_000, titanUnits(TITANS, notAllowed)));
        SyncPlan plan = plan(log, before, Map.of("Max", assigned("max")), null);
        SyncRow hero = plan.rows().stream().filter(r -> r.kind() == TeamKind.HERO).findFirst().orElseThrow();
        SyncRow titan = plan.rows().stream().filter(r -> r.kind() == TeamKind.TITAN).findFirst().orElseThrow();
        assertEquals(Certainty.UNITS, hero.certainty());
        assertEquals(1, hero.suggestedIndex());
        assertEquals("fenris", hero.suggested().unitsChange().petBefore());
        assertEquals("axel", hero.suggested().unitsChange().petAfter());
        assertEquals(Certainty.UNITS, titan.certainty());
        assertEquals(Set.of(notAllowed), titan.suggested().unitsChange().totemsAfter());

        SyncResult result = service.apply(plan, SyncSelection.none()
                .with(hero.id(), new Choice(1, true, true)).with(titan.id(), new Choice(0, false, true)));

        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(List.of(new SyncResult.DroppedPet("max", TeamKind.HERO, 1, "axel")), result.droppedPets());
        assertEquals(List.of(new SyncResult.DroppedTotem("max", TeamKind.TITAN, 0, notAllowed)), result.droppedTotems());
        assertEquals("fenris", member("max").heroTeams().get(1).pet().id(), "keeps its pet");
        assertEquals("axel", member("max").heroTeams().get(0).pet().id());
        TitanTeam t = member("max").titanTeams().get(0);
        assertEquals(Set.of(), t.totems());
        assertEquals(1_200_000, t.totalPower(), "power not selected");
        assertEquals(DAY, t.lastModified());
    }

    @Test
    @DisplayName("apply: units of a row without selectable units are refused; after a guild switch everything is refused")
    void refused() throws Exception {
        Guild before = setGuild(heroMember("max", 1_000_000));
        BattleLog log = log(defense("Max", "citadel", 1, 1_010_000));
        SyncPlan plan = plan(log, before, Map.of("Max", assigned("max")), null);
        SyncRow row = plan.rows().get(0);

        SyncResult units = service.apply(plan, SyncSelection.none().with(row.id(), new Choice(0, true, true)));
        assertEquals(SyncResult.Error.Kind.UNITS_NOT_SELECTABLE, units.errors().get(0).kind());
        SyncResult unknown = service.apply(plan, SyncSelection.none().with(row.id(), new Choice(2, true, false)));
        assertEquals(SyncResult.Error.Kind.UNKNOWN_TEAM, unknown.errors().get(0).kind());
        assertSame(before, context.guild());

        guildService.createGuild("Andere");
        guildService.switchToGuild("Andere");
        SyncResult changed = service.apply(plan, SyncSelection.preselected(plan));
        assertEquals(SyncResult.Error.Kind.GUILD_CHANGED, changed.errors().get(0).kind());
    }

    // --- helpers ---

    private static final List<String> TITANS = List.of("sigurd", "nova", "mairi", "hyperion", "tenebris");

    private SyncPlan plan(BattleLog log, Guild guild, Map<String, PlayerAssignment> assignments, Lineup lineup) {
        return JournalSyncService.plan(SOURCE, log, assignments, guild, lineup, context.catalog(),
                context.guildFilePath());
    }

    private Guild setGuild(GuildMember... members) {
        Guild guild = new Guild(context.guild().id(), context.guild().name(), List.of(members));
        context.setGuild(guild);
        context.setGuildDirty(false);
        return guild;
    }

    private GuildMember member(String name) {
        return context.guild().members().stream().filter(m -> m.name().equals(name) || m.id().equals(name))
                .findFirst().orElseThrow();
    }

    private void replaceMember(GuildMember replacement) {
        List<GuildMember> members = new ArrayList<>(context.guild().members());
        members.replaceAll(m -> m.id().equals(replacement.id()) ? replacement : m);
        context.setGuild(context.guild().withMembers(members));
    }

    /** A member with power-only hero teams (no heroes) of these powers - here with heroes so they are not empty. */
    private GuildMember heroMember(String id, int... powers) {
        List<HeroTeam> teams = new ArrayList<>();
        List<List<String>> sets = List.of(HEROES, HEROES_2, HEROES_3);
        for (int i = 0; i < powers.length; i++) {
            teams.add(new HeroTeam(id, i, heroes(sets.get(i)), powers[i]));
        }
        return new GuildMember(id, id.substring(0, 1).toUpperCase() + id.substring(1), teams, List.of());
    }

    private List<Hero> heroes(List<String> ids) {
        return ids.stream().map(id -> context.catalog().heroes().findById(id).orElseThrow()).toList();
    }

    private List<Titan> titans(List<String> ids) {
        return ids.stream().map(id -> context.catalog().titans().findById(id).orElseThrow()).toList();
    }

    private Pet pet(String id) {
        return context.catalog().pets().findById(id).orElseThrow();
    }

    private static Map<String, PlayerAssignment> assignedByLowerCase(String... names) {
        Map<String, PlayerAssignment> result = new HashMap<>();
        for (String n : names) {
            result.put(n, assigned(n.toLowerCase(Locale.ROOT)));
        }
        return result;
    }

    private static PlayerAssignment assigned(String memberId) {
        return new PlayerAssignment(1, memberId, AssignmentStatus.ASSIGNED, null);
    }

    private static BattleLog log(Fight... fights) {
        BattleLogHeader header = new BattleLogHeader(DAY, null, null, null, null, LogDirection.DEFENSE, "deutsch",
                "test.csv");
        return new BattleLog(header, List.of(fights));
    }

    private static Fight defense(String defender, String fortId, int position, int power) {
        return defense(defender, fortId, position, power, List.of());
    }

    private static Fight defense(String defender, String fortId, int position, int power, List<FightUnit> units) {
        return new Fight(fortId, fortId, position, null, true, "Sieg", 35,
                new FightSide("Gegner", 130, 1, null, List.of()), new FightSide(defender, 130, power, null, units), 1);
    }

    private static List<FightUnit> heroUnits(List<String> ids, String pet) {
        List<FightUnit> units = new ArrayList<>();
        ids.forEach(id -> units.add(unit(UnitKind.HERO, id, null)));
        if (pet != null) {
            units.add(unit(UnitKind.PET, pet, null));
        }
        return units;
    }

    private static List<FightUnit> titanUnits(List<String> ids, TitanElement totem) {
        List<FightUnit> units = new ArrayList<>();
        ids.forEach(id -> units.add(unit(UnitKind.TITAN, id, null)));
        units.add(unit(UnitKind.TOTEM, null, totem));
        return units;
    }

    private static FightUnit unit(UnitKind kind, String id, TitanElement totem) {
        return new FightUnit(kind, id == null ? "Totem" : id, id, totem, null, "", 0, 6, 130,
                kind == UnitKind.TOTEM ? null : 100_000, 0, 0, 0, null);
    }
}
