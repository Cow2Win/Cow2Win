package org.c2w.service;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.service.DataStatus.Area;
import org.c2w.service.DataStatus.Level;
import org.c2w.service.DataStatus.MemberState;
import org.c2w.service.DataStatus.TextPart;
import org.c2w.service.DataStatusService.FileTimes;
import org.c2w.service.DataStatusService.Input;
import org.c2w.service.DataStatusService.StaleCheck;
import org.c2w.service.journal.SyncPlan;
import org.c2w.service.journal.SyncRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** {@link DataStatusService} - the status rules, with a fixed "today" and test data, no GUI. */
class DataStatusServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate LOG_DAY = LocalDate.of(2026, 10, 1);
    private static final Path LINEUP_FILE = Path.of("alpha", "target.lineup");
    private static final Path ORIGINAL_FILE = Path.of("alpha", "Original.lineup");
    private static final StaleCheck CHECK_30 = new StaleCheck(true, 30);

    private static HeroTeam team(String memberId, int index, LocalDate lastModified) {
        return new HeroTeam(memberId, index, List.of(), null, null, 1_000_000, lastModified);
    }

    private static GuildMember member(String id, HeroTeam... teams) {
        return new GuildMember(id, id, List.of(teams), List.of());
    }

    private static Guild guild(GuildMember... members) {
        return new Guild("alpha", "Alpha", List.of(members));
    }

    private static Lineup lineup(Lineup.Entry... entries) {
        return new Lineup("alpha", "target", "", LocalDateTime.now(), List.of(entries));
    }

    private static Input input(Guild guild, StaleCheck check, SyncPlan plan) {
        return new Input(guild, lineup(), LINEUP_FILE, false, FortificationType.HERO, check, TODAY, plan, lineup(),
                FileTimes.NONE);
    }

    private static SyncPlan planWith(List<SyncRow> rows, List<SyncPlan.UnmatchedTeam> unmatched) {
        return new SyncPlan(null, new SyncPlan.Source(1, LOG_DAY, null, null, null, null, null, null), rows, unmatched,
                List.of(), List.of(), List.of(), List.of(), Path.of("alpha", "guild.json"));
    }

    /** A matched log team of {@code memberId} with power {@code logPower} against a stored team of 1.000.000. */
    private static SyncRow row(String memberId, int logPower, boolean editedSinceBattle) {
        SyncRow.Target target = new SyncRow.Target(0, 1_000_000, null, false, editedSinceBattle, null, false, null);
        return new SyncRow("r-" + memberId, memberId, memberId, memberId, TeamKind.HERO, "bastion", "Bastion", 1,
                logPower, SyncRow.Certainty.SURE, 0, false, null, List.of(target));
    }

    @Nested
    @DisplayName("Input")
    class InputStage {

        @Test
        @DisplayName("No guild: NONE everywhere")
        void noGuild() {
            DataStatus status = DataStatusService.evaluate(input(null, StaleCheck.OFF, null));
            for (Area area : Area.values()) {
                assertEquals(Level.NONE, status.result(area).level(), area.name());
            }
        }

        @Test
        @DisplayName("No journal: OK \"teams recorded\" - however old the teams, and with members without teams")
        void noJournal() {
            Guild guild = guild(member("old", team("old", 0, LocalDate.of(2020, 1, 1))), member("empty"));
            DataStatus status = DataStatusService.evaluate(input(guild, StaleCheck.OFF, null));
            assertEquals(Level.OK, status.input().level());
            assertEquals(List.of(TextPart.of("status.input.complete")), status.input().texts());
            assertEquals(MemberState.NO_TEAMS, status.member("empty").orElseThrow().state());
        }

        @Test
        @DisplayName("A changed log team: ACTION_NEEDED, the member outdated per journal with the deviation")
        void changedRow() {
            Guild guild = guild(member("anna", team("anna", 0, LOG_DAY.minusDays(3))));
            DataStatus status = DataStatusService.evaluate(input(guild, StaleCheck.OFF,
                    planWith(List.of(row("anna", 1_100_000, false)), List.of())));
            assertEquals(Level.ACTION_NEEDED, status.input().level());
            assertEquals(List.of(TextPart.of("status.input.staleJournal", 1)), status.input().texts());
            DataStatus.MemberFinding finding = status.member("anna").orElseThrow();
            assertEquals(MemberState.STALE_JOURNAL, finding.state());
            assertEquals(LOG_DAY, finding.logDate());
            assertEquals(List.of(new DataStatus.TeamDeviation(TeamKind.HERO, 0, 1_000_000, 1_100_000)), finding.deviations());
            assertEquals(LOG_DAY, status.newestDefense());
        }

        @Test
        @DisplayName("The same row, but the team was edited since the battle: no evidence")
        void editedSinceBattle() {
            Guild guild = guild(member("anna", team("anna", 0, TODAY)));
            DataStatus status = DataStatusService.evaluate(input(guild, StaleCheck.OFF,
                    planWith(List.of(row("anna", 1_100_000, true)), List.of())));
            assertEquals(Level.OK, status.input().level());
            assertEquals(List.of(TextPart.of("status.input.ok")), status.input().texts(), "with a journal: data up to date");
        }

        @Test
        @DisplayName("An unchanged row is no evidence; an unmatched log team of a member is")
        void unchangedAndUnmatched() {
            Guild guild = guild(member("anna", team("anna", 0, TODAY)), member("bert", team("bert", 0, TODAY)));
            SyncPlan plan = planWith(List.of(row("anna", 1_000_000, false)),
                    List.of(new SyncPlan.UnmatchedTeam("bert", "bert", TeamKind.HERO, 900_000, "bastion", "Bastion", 2)));
            DataStatus status = DataStatusService.evaluate(input(guild, StaleCheck.OFF, plan));
            assertEquals(MemberState.OK, status.member("anna").orElseThrow().state());
            assertEquals(MemberState.STALE_JOURNAL, status.member("bert").orElseThrow().state());
            assertEquals(1, status.staleJournal());
        }

        @Test
        @DisplayName("Stale check 30 days: 31 days old is ATTENTION, 29 days is no finding")
        void staleCheck() {
            Guild old = guild(member("anna", team("anna", 0, TODAY.minusDays(31))));
            DataStatus status = DataStatusService.evaluate(input(old, CHECK_30, null));
            assertEquals(Level.ATTENTION, status.input().level());
            assertEquals(List.of(TextPart.of("status.input.staleAge", 1, 30)), status.input().texts());
            assertEquals(MemberState.TOO_OLD, status.member("anna").orElseThrow().state());
            assertEquals(31, status.member("anna").orElseThrow().ageDays());

            Guild fresh = guild(member("anna", team("anna", 0, TODAY.minusDays(29))));
            assertEquals(Level.OK, DataStatusService.evaluate(input(fresh, CHECK_30, null)).input().level());
        }

        @Test
        @DisplayName("Stale check off: age does not matter; members without teams are never too old; an unknown date is too old")
        void staleCheckEdgeCases() {
            Guild old = guild(member("anna", team("anna", 0, TODAY.minusDays(400))));
            assertEquals(Level.OK, DataStatusService.evaluate(input(old, new StaleCheck(false, 30), null)).input().level());

            Guild empty = guild(member("empty"));
            assertEquals(Level.OK, DataStatusService.evaluate(input(empty, CHECK_30, null)).input().level());

            Guild unknown = guild(member("anna", team("anna", 0, null)));
            assertEquals(MemberState.TOO_OLD, DataStatusService.evaluate(input(unknown, CHECK_30, null))
                    .member("anna").orElseThrow().state());
        }
    }

    @Nested
    @DisplayName("Strategic concept")
    class ConceptStage {

        private final Guild guild = guild(member("anna", team("anna", 0, TODAY)));
        private final Instant earlier = Instant.parse("2026-10-01T10:00:00Z");
        private final Instant later = Instant.parse("2026-10-02T10:00:00Z");

        private Input concept(Lineup lineup, Path file, boolean dirty, FileTimes times) {
            return new Input(guild, lineup, file, dirty, FortificationType.HERO, StaleCheck.OFF, TODAY, null, lineup, times);
        }

        @Test
        @DisplayName("Original open: NONE with \"Original open\"")
        void originalOpen() {
            DataStatus status = DataStatusService.evaluate(concept(lineup(), ORIGINAL_FILE, false, FileTimes.NONE));
            assertEquals(Level.NONE, status.concept().level());
            assertEquals(List.of(TextPart.of("status.concept.originalOpen")), status.concept().texts());
        }

        @Test
        @DisplayName("Free slots and an unassigned team: ACTION_NEEDED")
        void freeSlotsAndFreeTeams() {
            DataStatus status = DataStatusService.evaluate(concept(lineup(), LINEUP_FILE, false, FileTimes.NONE));
            assertEquals(Level.ACTION_NEEDED, status.concept().level());
            assertEquals("status.concept.freeSlots", status.concept().texts().get(0).key());
        }

        @Test
        @DisplayName("Free slots, but every team assigned: not red")
        void freeSlotsWithoutFreeTeams() {
            Lineup assigned = lineup(new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0));
            DataStatus status = DataStatusService.evaluate(concept(assigned, LINEUP_FILE, false, FileTimes.NONE));
            assertEquals(Level.OK, status.concept().level());
            assertTrue(DataStatusService.freeSlots(assigned, FortificationType.HERO) > 0);
        }

        @Test
        @DisplayName("Guild file or CowScore file newer than the lineup: ATTENTION \"recalculate\"; dirty: ATTENTION \"unsaved\"")
        void recalculateAndDirty() {
            Lineup assigned = lineup(new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0));

            DataStatus guildNewer = DataStatusService.evaluate(concept(assigned, LINEUP_FILE, false,
                    new FileTimes(later, earlier, List.of())));
            assertEquals(Level.ATTENTION, guildNewer.concept().level());
            assertTrue(guildNewer.concept().texts().contains(TextPart.of("status.concept.recalculate")));

            DataStatus cowScoreNewer = DataStatusService.evaluate(concept(assigned, LINEUP_FILE, false,
                    new FileTimes(earlier, earlier, List.of(earlier, later))));
            assertEquals(Level.ATTENTION, cowScoreNewer.concept().level());

            DataStatus older = DataStatusService.evaluate(concept(assigned, LINEUP_FILE, false,
                    new FileTimes(earlier, later, List.of(earlier))));
            assertEquals(Level.OK, older.concept().level());
            assertEquals(List.of(TextPart.of("status.concept.freeSlots",
                    DataStatusService.freeSlots(assigned, FortificationType.HERO))), older.concept().texts());

            DataStatus dirty = DataStatusService.evaluate(concept(assigned, LINEUP_FILE, true, FileTimes.NONE));
            assertEquals(Level.ATTENTION, dirty.concept().level());
            assertTrue(dirty.concept().texts().contains(TextPart.of("status.concept.unsaved")));
        }

        @Test
        @DisplayName("Every slot filled and nothing pending: OK \"lineup complete\"")
        void complete() {
            List<Lineup.Entry> entries = new ArrayList<>();
            List<GuildMember> members = new ArrayList<>();
            int memberNumber = 0;
            for (var fortification : FortificationRepository.findAll()) {
                if (fortification.type() != FortificationType.HERO) {
                    continue;
                }
                for (int slot = 0; slot < fortification.capacity(); slot++) {
                    String id = "m" + memberNumber++;
                    members.add(member(id, team(id, 0, TODAY)));
                    entries.add(new Lineup.Entry(fortification.id(), id, Lineup.TeamType.HERO, 0));
                }
            }
            Lineup full = new Lineup("alpha", "target", "", LocalDateTime.now(), entries);
            Guild many = new Guild("alpha", "Alpha", members.subList(0, Math.min(Guild.MAX_MEMBERS, members.size())));
            Input in = new Input(many, full, LINEUP_FILE, false, FortificationType.HERO, StaleCheck.OFF, TODAY, null, full,
                    FileTimes.NONE);
            DataStatus status = DataStatusService.evaluate(in);
            assertEquals(Level.OK, status.concept().level());
            assertEquals(List.of(TextPart.of("status.concept.ok")), status.concept().texts());
        }
    }

    @Nested
    @DisplayName("Output")
    class OutputStage {

        private final Guild guild = guild(member("anna", team("anna", 0, TODAY)));

        @Test
        @DisplayName("No Original: ACTION_NEEDED; plan with steps: ATTENTION; no steps: OK; Original open: NONE")
        void levels() {
            Lineup target = lineup(new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0));
            Input noOriginal = new Input(guild, target, LINEUP_FILE, false, FortificationType.HERO, StaleCheck.OFF, TODAY,
                    null, null, FileTimes.NONE);
            assertEquals(Level.ACTION_NEEDED, DataStatusService.evaluate(noOriginal).output().level());

            Input withSteps = new Input(guild, target, LINEUP_FILE, false, FortificationType.HERO, StaleCheck.OFF, TODAY,
                    null, lineup(), FileTimes.NONE);
            DataStatus steps = DataStatusService.evaluate(withSteps);
            assertEquals(Level.ATTENTION, steps.output().level());
            assertEquals(List.of(TextPart.of("status.output.steps", 1)), steps.output().texts());

            Input same = new Input(guild, target, LINEUP_FILE, false, FortificationType.HERO, StaleCheck.OFF, TODAY,
                    null, target, FileTimes.NONE);
            assertEquals(Level.OK, DataStatusService.evaluate(same).output().level());

            Input originalOpen = new Input(guild, target, ORIGINAL_FILE, false, FortificationType.HERO, StaleCheck.OFF,
                    TODAY, null, target, FileTimes.NONE);
            assertEquals(Level.NONE, DataStatusService.evaluate(originalOpen).output().level());
        }
    }

    @Test
    @DisplayName("nextStep: red before orange, in the order input, concept, output; empty if all OK/NONE")
    void nextStep() {
        DataStatus.StageResult red = new DataStatus.StageResult(Level.ACTION_NEEDED, List.of());
        DataStatus.StageResult orange = new DataStatus.StageResult(Level.ATTENTION, List.of());
        DataStatus.StageResult green = new DataStatus.StageResult(Level.OK, List.of());
        DataStatus.StageResult none = new DataStatus.StageResult(Level.NONE, List.of());

        assertEquals(Optional.of(Area.OUTPUT), status(orange, green, red).nextStep(), "red wins over an earlier orange");
        assertEquals(Optional.of(Area.INPUT), status(red, red, green).nextStep(), "the first red");
        assertEquals(Optional.of(Area.CONCEPT), status(green, orange, orange).nextStep(), "the first orange");
        assertEquals(Optional.empty(), status(green, none, green).nextStep());
    }

    private static DataStatus status(DataStatus.StageResult input, DataStatus.StageResult concept,
                                     DataStatus.StageResult output) {
        return new DataStatus(input, concept, output, null, 0, 0, 0, null, java.util.Map.of());
    }
}
