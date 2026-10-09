package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.ChangePlanOutline.Kind;
import org.c2w.domain.LineupChangePlanService;
import org.c2w.domain.LineupComparisonService;
import org.c2w.gui.stage.ChangePlanModel;
import org.c2w.service.LiveApplyService.Outcome;
import org.c2w.service.LiveApplyService.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LiveApplyService}: which entries are applied, capacity, archive and safe saving. */
class LiveApplyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 14, 30);
    private static final Guild GUILD = new Guild("alpha", "Alpha", List.of());

    @TempDir
    Path guildDir;

    private static Lineup.Entry hero(String fortificationId, String memberId, int index) {
        return new Lineup.Entry(fortificationId, memberId, Lineup.TeamType.HERO, index);
    }

    private static Lineup lineup(Lineup.Entry... entries) {
        return new Lineup("alpha", "Alpha", "", LocalDateTime.of(2026, 9, 1, 12, 0), List.of(entries));
    }

    private static ChangePlanOutline outline(Lineup live, Lineup target) {
        return ChangePlanOutline.of(LineupChangePlanService.from(LineupComparisonService.compare(live, target, GUILD)), GUILD);
    }

    private static String id(ChangePlanOutline outline, Kind kind, String memberId) {
        return outline.items().stream()
                .filter(item -> item.kind() == kind && item.teamKey().teamMemberId().equals(memberId))
                .findFirst().orElseThrow().id();
    }

    private static boolean contains(Lineup lineup, String fortificationId, String memberId) {
        return lineup.entries().stream()
                .anyMatch(e -> e.fortificationId().equals(fortificationId) && e.teamMemberId().equals(memberId));
    }

    @Test
    @DisplayName("Only checked entries are applied; open ones stay as they were")
    void onlyChecked() {
        Lineup live = lineup(hero("bastion", "m1", 0), hero("bastion", "m2", 0));
        Lineup target = lineup(hero("bastion", "m2", 0), hero("citadel", "m3", 0), hero("citadel", "m4", 0));
        ChangePlanOutline outline = outline(live, target);

        Result result = LiveApplyService.apply(live, outline,
                Set.of(id(outline, Kind.REMOVE, "m1"), id(outline, Kind.ADD, "m3")), NOW);

        assertEquals(Outcome.APPLIED, result.outcome());
        assertFalse(contains(result.live(), "bastion", "m1"));
        assertTrue(contains(result.live(), "citadel", "m3"));
        assertFalse(contains(result.live(), "citadel", "m4"), "not checked");
        assertTrue(contains(result.live(), "bastion", "m2"));
        assertEquals(NOW, result.live().createdAt());
        assertEquals(2, result.applied().size());
        assertEquals(0, result.autoRemoved());
    }

    @Test
    @DisplayName("A checked addition of a fortification change takes its removal along; no team twice")
    void moveTakesRemovalAlong() {
        Lineup live = lineup(hero("bastion", "m1", 0));
        Lineup target = lineup(hero("citadel", "m1", 0));
        ChangePlanOutline outline = outline(live, target);

        Result result = LiveApplyService.apply(live, outline, Set.of(id(outline, Kind.ADD, "m1")), NOW);

        assertEquals(Outcome.APPLIED, result.outcome());
        assertEquals(List.of(hero("citadel", "m1", 0)), result.live().entries());
        assertEquals(2, result.applied().size());
        assertEquals(1, result.autoRemoved());
        assertEquals(Set.of(id(outline, Kind.ADD, "m1"), id(outline, Kind.REMOVE, "m1")), result.appliedIds());
    }

    @Test
    @DisplayName("Nothing checked: nothing to do")
    void nothingChecked() {
        Lineup live = lineup(hero("bastion", "m1", 0));
        Result result = LiveApplyService.apply(live, outline(live, lineup()), Set.of(), NOW);
        assertEquals(Outcome.NOTHING_CHECKED, result.outcome());
        assertNull(result.live());
    }

    @Test
    @DisplayName("Over capacity: nothing applied, the fortification is reported")
    void overCapacity() {
        // The barracks hold 3 teams.
        Lineup live = lineup(hero("barracks", "m1", 0), hero("barracks", "m2", 0), hero("barracks", "m3", 0));
        Lineup target = lineup(hero("barracks", "m1", 0), hero("barracks", "m2", 0), hero("barracks", "m4", 0));
        ChangePlanOutline outline = outline(live, target);

        Result result = LiveApplyService.apply(live, outline, Set.of(id(outline, Kind.ADD, "m4")), NOW);
        assertEquals(Outcome.OVER_CAPACITY, result.outcome());
        assertEquals(List.of("barracks"), result.overCapacityFortifications());
        assertNull(result.live());

        Result both = LiveApplyService.apply(live, outline,
                Set.of(id(outline, Kind.ADD, "m4"), id(outline, Kind.REMOVE, "m3")), NOW);
        assertEquals(Outcome.APPLIED, both.outcome());
    }

    @Test
    @DisplayName("Archive: a copy in live-history/, a second one in the same minute gets the suffix _2")
    void archive() throws Exception {
        LineupRepository.save(lineup(hero("bastion", "m1", 0)), LineupFiles.originalPathFor(guildDir));

        Path first = LiveApplyService.archive(guildDir, NOW);
        Path second = LiveApplyService.archive(guildDir, NOW);

        assertEquals(guildDir.resolve("live-history").resolve("Live_2026-10-05_1430.lineup"), first);
        assertEquals(guildDir.resolve("live-history").resolve("Live_2026-10-05_1430_2.lineup"), second);
        assertEquals(Files.readString(LineupFiles.originalPathFor(guildDir)), Files.readString(first));
    }

    @Test
    @DisplayName("Saving replaces the live file; an error leaves it unchanged")
    void saveLive() throws Exception {
        Path liveFile = LineupFiles.originalPathFor(guildDir);
        LineupRepository.save(lineup(hero("bastion", "m1", 0)), liveFile);
        String before = Files.readString(liveFile);

        // The temporary file cannot be written while a folder has its name.
        Path blocker = Files.createDirectories(liveFile.resolveSibling(liveFile.getFileName() + ".tmp"));
        Files.writeString(blocker.resolve("x"), "x");
        assertThrows(Exception.class, () -> LiveApplyService.saveLive(guildDir, lineup(hero("citadel", "m9", 0))));
        assertEquals(before, Files.readString(liveFile));

        Files.delete(blocker.resolve("x"));
        Files.delete(blocker);
        LiveApplyService.saveLive(guildDir, lineup(hero("citadel", "m9", 0)));
        assertEquals(List.of(hero("citadel", "m9", 0)), LineupRepository.load(liveFile).entries());
        assertFalse(Files.exists(blocker));
    }

    @Test
    @DisplayName("Lineup lists show no files from live-history/")
    void historyNotListed() throws Exception {
        LineupRepository.save(lineup(), LineupFiles.originalPathFor(guildDir));
        LineupRepository.save(lineup(), guildDir.resolve("Ziel.lineup"));
        LiveApplyService.archive(guildDir, NOW);

        assertEquals(List.of("Ziel.lineup"), ChangePlanModel.targetLineupFileNames(guildDir.resolve("guild.json")));
    }
}
