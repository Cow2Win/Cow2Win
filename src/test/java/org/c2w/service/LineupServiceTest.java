package org.c2w.service;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.util.AppContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LineupService}: files on disk, {@link AppContext} and its dirty state stay consistent. */
class LineupServiceTest extends ServiceTestSupport {

    private static Fortification firstFortification() {
        return FortificationRepository.findAll().get(0);
    }

    @Test
    @DisplayName("assignTeam changes the open lineup in memory, marks it dirty and notifies listeners")
    void assignTeamMarksLineupDirty() {
        List<String> events = new ArrayList<>();
        context.addListener(new AppContext.Listener() {
            @Override
            public void lineupChanged() {
                events.add("lineup");
            }
        });
        Fortification fortification = firstFortification();

        LineupService.AssignResult result = lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fortification);

        assertTrue(result.assigned());
        assertEquals(List.of(new Lineup.Entry(fortification.id(), "m1", Lineup.TeamType.HERO, 0)),
                context.lineup().entries());
        assertTrue(context.isLineupDirty());
        assertEquals(List.of("lineup"), events);
    }

    @Test
    @DisplayName("assignTeam moves a team instead of duplicating it, and null removes its assignment")
    void assignTeamMovesAndRemoves() {
        List<Fortification> fortifications = FortificationRepository.findAll();
        Fortification first = fortifications.get(0);
        Fortification second = fortifications.get(1);

        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, first);
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, second);
        assertEquals(List.of(new Lineup.Entry(second.id(), "m1", Lineup.TeamType.HERO, 0)),
                context.lineup().entries());

        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, null);
        assertTrue(context.lineup().entries().isEmpty());
    }

    @Test
    @DisplayName("assignTeam refuses a full fortification and changes nothing")
    void assignTeamRefusesFullFortification() {
        Fortification fortification = firstFortification();
        for (int i = 0; i < fortification.capacity(); i++) {
            assertTrue(lineupService.assignTeam("m" + i, Lineup.TeamType.HERO, 0, fortification).assigned());
        }
        Lineup before = context.lineup();

        LineupService.AssignResult result = lineupService.assignTeam("extra", Lineup.TeamType.HERO, 0, fortification);

        assertFalse(result.assigned());
        assertEquals(fortification.capacity(), result.filledSlots());
        assertSame(before, context.lineup());
    }

    @Test
    @DisplayName("saveLineup writes the open lineup to its file and clears the dirty flag")
    void saveLineupPersistsAndClearsDirty() throws Exception {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, firstFortification());

        lineupService.saveLineup();

        assertFalse(context.isLineupDirty());
        assertEquals(context.lineup().entries(), LineupRepository.load(context.lineupFilePath()).entries());
    }

    @Test
    @DisplayName("createLineup writes an empty file, opens it clean and remembers it")
    void createLineupOpensNewFile() throws Exception {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, firstFortification());

        lineupService.createLineup("second.lineup");

        Path expected = lineupService.guildDir().resolve("second.lineup");
        assertTrue(Files.isRegularFile(expected));
        assertEquals(expected, context.lineupFilePath());
        assertTrue(context.lineup().entries().isEmpty());
        assertFalse(context.isLineupDirty());
        assertEquals(List.of(expected), recentFiles.lineups);
        assertEquals(List.of(LineupService.DEFAULT_LINEUP_FILE_NAME, "second.lineup"),
                lineupService.listLineupFileNames());
    }

    @Test
    @DisplayName("deleteLineup + lineupAfterRemoval + selectLineup open the neighbouring file")
    void deleteLineupAndOpenNeighbour() throws Exception {
        lineupService.createLineup("b.lineup");
        lineupService.createLineup("c.lineup");
        lineupService.selectLineup("b.lineup");
        int index = lineupService.listLineupFileNames().indexOf("b.lineup");

        lineupService.deleteLineup("b.lineup");
        String next = lineupService.lineupAfterRemoval(index);
        lineupService.selectLineup(next);

        assertEquals("c.lineup", next);
        assertEquals(lineupService.guildDir().resolve("c.lineup"), context.lineupFilePath());
        assertFalse(lineupService.lineupExists("b.lineup"));
    }

    @Test
    @DisplayName("clearLineup removes every assignment and marks the lineup dirty")
    void clearLineupMarksDirty() throws Exception {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, firstFortification());
        lineupService.saveLineup();

        lineupService.clearLineup();

        assertTrue(context.lineup().entries().isEmpty());
        assertTrue(context.isLineupDirty());
    }

    @Test
    @DisplayName("an algorithm run that assigns nothing leaves the lineup clean")
    void noOpAlgorithmRunStaysClean() {
        LineupService.AlgorithmRun run = lineupService.runAlgorithms(
                new ManualLineupAlgorithm(Lineup.TeamType.HERO), new ManualLineupAlgorithm(Lineup.TeamType.TITAN));

        assertEquals(0, run.heroesAssigned());
        assertEquals(0, run.titansAssigned());
        assertFalse(context.isLineupDirty());
    }

    @Test
    @DisplayName("toLineupFileName appends the suffix only once")
    void toLineupFileName() {
        assertEquals("x.lineup", LineupService.toLineupFileName("x"));
        assertEquals("x.lineup", LineupService.toLineupFileName("x.lineup"));
    }
}
