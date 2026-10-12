package org.c2w.service;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.LineupBaseline;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

    /** The guild log lines of the open guild without the date/time prefix. */
    private List<String> guildLogTexts() {
        return GuildLog.read(context.guildFilePath().getParent()).stream().map(line -> line.substring(21)).toList();
    }

    @Test
    @DisplayName("creating a lineup writes exactly one 'lineup created' entry into the guild log")
    void createLineupIsLoggedOnce() throws Exception {
        int before = guildLogTexts().size();

        lineupService.createLineup("Plan A.lineup");

        List<String> texts = guildLogTexts();
        assertEquals(before + 1, texts.size());
        assertEquals(LanguageService.displayName("guildLog.lineupCreated", "Plan A"), texts.get(before));
    }

    @Test
    @DisplayName("a lineup that cannot be created writes no guild log entry")
    void failedCreateLineupIsNotLogged() throws Exception {
        Files.createDirectory(context.guildFilePath().getParent().resolve("Plan B.lineup"));
        int before = guildLogTexts().size();

        assertThrows(IOException.class, () -> lineupService.createLineup("Plan B.lineup"));

        assertEquals(before, guildLogTexts().size());
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

    // --- running an algorithm for one fortification type ---

    /** Makes the open guild four members, each with one hero and one titan team. */
    private void useGuildWithHeroAndTitanTeams() {
        Guild current = context.guild();
        List<GuildMember> members = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String id = "m" + i;
            int power = 1000 - i * 100;
            members.add(new GuildMember(id, id,
                    List.of(new HeroTeam(id, 0, List.of(new Hero(id + "-hero", List.of(Role.MAGE))), power)),
                    List.of(new TitanTeam(id, 0, List.of(new Titan(id + "-titan", TitanElement.WATER)), power))));
        }
        context.setGuild(new Guild(current.id(), current.name(), members));
    }

    private static Fortification firstFortificationOf(FortificationType type) {
        return FortificationRepository.findAll().stream().filter(f -> f.type() == type).findFirst().orElseThrow();
    }

    private static List<Lineup.Entry> entriesOf(Lineup lineup, Lineup.TeamType teamType) {
        return lineup.entries().stream().filter(entry -> entry.teamType() == teamType).toList();
    }

    /** One hero and one titan team assigned by hand. */
    private void assignOneHeroAndOneTitan() {
        lineupService.assignTeam("m4", Lineup.TeamType.HERO, 0, firstFortificationOf(FortificationType.HERO));
        lineupService.assignTeam("m4", Lineup.TeamType.TITAN, 0, firstFortificationOf(FortificationType.TITAN));
    }

    @Test
    @DisplayName("computeAlgorithm replaces the hero assignments and leaves the titan assignments as they are")
    void heroRunReplacesOnlyHeroes() {
        useGuildWithHeroAndTitanTeams();
        assignOneHeroAndOneTitan();
        Lineup before = context.lineup();

        LineupService.AlgorithmRun run = LineupService.computeAlgorithm(before, context.guild(), LineupAlgorithms.HERO.get(0));

        assertSame(before, run.before());
        assertEquals(entriesOf(before, Lineup.TeamType.TITAN), entriesOf(run.result(), Lineup.TeamType.TITAN));
        assertEquals(1, run.removed());
        assertEquals(entriesOf(run.result(), Lineup.TeamType.HERO).size(), run.assigned());
        assertEquals(4, run.assigned(), "every hero team is assigned anew");
        assertTrue(run.changedAnything());
    }

    @Test
    @DisplayName("computeAlgorithm for titans leaves the hero assignments as they are")
    void titanRunReplacesOnlyTitans() {
        useGuildWithHeroAndTitanTeams();
        assignOneHeroAndOneTitan();
        Lineup before = context.lineup();

        LineupService.AlgorithmRun run = LineupService.computeAlgorithm(before, context.guild(), LineupAlgorithms.TITAN.get(1));

        assertEquals(entriesOf(before, Lineup.TeamType.HERO), entriesOf(run.result(), Lineup.TeamType.HERO));
        assertEquals(1, run.removed());
        assertEquals(entriesOf(run.result(), Lineup.TeamType.TITAN).size(), run.assigned());
    }

    @Test
    @DisplayName("runAlgorithm applies the run, marks the lineup dirty and writes one guild log entry for the type")
    void runAlgorithmMarksDirtyAndLogs() throws Exception {
        useGuildWithHeroAndTitanTeams();
        assignOneHeroAndOneTitan();
        lineupService.saveLineup();
        int logSize = guildLogTexts().size();
        LineupAlgorithm algorithm = LineupAlgorithms.HERO.get(0);

        LineupService.AlgorithmRun run = lineupService.runAlgorithm(algorithm);

        assertSame(run.result(), context.lineup());
        assertTrue(context.isLineupDirty());
        List<String> texts = guildLogTexts();
        assertEquals(logSize + 1, texts.size());
        assertEquals(LanguageService.displayName("guildLog.algorithmRun", LanguageService.displayName("fortificationMap.showHeroes"),
                algorithm.localizedName(), 1, run.assigned()), texts.get(logSize));
    }

    @Test
    @DisplayName("a run that neither removes nor assigns anything leaves the lineup clean")
    void noOpAlgorithmRunStaysClean() {
        LineupService.AlgorithmRun run = lineupService.runAlgorithm(LineupAlgorithms.HERO.get(0));

        assertEquals(0, run.removed());
        assertEquals(0, run.assigned());
        assertFalse(context.isLineupDirty());
    }

    @Test
    @DisplayName("\"Manual\" cannot run an assignment")
    void manualAlgorithmIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LineupService.computeAlgorithm(context.lineup(), context.guild(),
                new ManualLineupAlgorithm(Lineup.TeamType.HERO)));
    }

    @Test
    @DisplayName("a background algorithm run is discarded if the lineup changed while it was computing")
    void staleAlgorithmRunIsDiscarded() {
        useGuildWithHeroAndTitanTeams();
        LineupService.AlgorithmRun run = LineupService.computeAlgorithm(context.lineup(), context.guild(),
                LineupAlgorithms.HERO.get(0));
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, firstFortification());
        Lineup edited = context.lineup();

        assertFalse(lineupService.applyAlgorithmRun(run));
        assertSame(edited, context.lineup(), "the edit made in the meantime must survive");
    }

    @Test
    @DisplayName("toLineupFileName appends the suffix only once")
    void toLineupFileName() {
        assertEquals("x.lineup", LineupService.toLineupFileName("x"));
        assertEquals("x.lineup", LineupService.toLineupFileName("x.lineup"));
    }

    // --- baseline of the "Changes" view after saving ---

    /** Makes the open guild one member "m1" with a single 1000-power hero team (one MAGE). */
    private void useGuildWithTeam() {
        Guild current = context.guild();
        HeroTeam team = new HeroTeam("m1", 0, List.of(new Hero("h1", List.of(Role.MAGE))), 1000);
        context.setGuild(new Guild(current.id(), current.name(),
                List.of(new GuildMember("m1", "m1", List.of(team), List.of()))));
    }

    private void assignToAlchemyTower() {
        Fortification alchemyTower = FortificationRepository.findById("alchemy-tower").orElseThrow();
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, alchemyTower);
        assertEquals(new LineupBaseline.Diff(1000, 1), context.fortificationDiffFromLoaded("alchemy-tower"));
    }

    @Test
    @DisplayName("saveLineup retakes the baseline - every fortification diffs to 0 right after saving")
    void saveLineupRetakesBaseline() throws Exception {
        useGuildWithTeam();
        assignToAlchemyTower();

        lineupService.saveLineup();

        assertFalse(context.isLineupDirty());
        for (Fortification fortification : FortificationRepository.findAll()) {
            assertEquals(new LineupBaseline.Diff(0, 0), context.fortificationDiffFromLoaded(fortification.id()));
        }
    }

    @Test
    @DisplayName("saveWithGuild retakes the baseline too")
    void saveWithGuildRetakesBaseline() throws Exception {
        useGuildWithTeam();
        assignToAlchemyTower();

        lineupService.saveWithGuild(context.guild(), context.lineup());

        assertFalse(context.hasUnsavedChanges());
        assertEquals(new LineupBaseline.Diff(0, 0), context.fortificationDiffFromLoaded("alchemy-tower"));
    }

    @Test
    @DisplayName("a failed saveLineup keeps the old baseline and the dirty state")
    void failedSaveKeepsBaselineAndDirtyState() throws Exception {
        useGuildWithTeam();
        assignToAlchemyTower();
        // A directory where the lineup file should be makes writing it fail.
        Files.delete(context.lineupFilePath());
        Files.createDirectory(context.lineupFilePath());

        assertThrows(IOException.class, () -> lineupService.saveLineup());

        assertTrue(context.isLineupDirty());
        assertEquals(new LineupBaseline.Diff(1000, 1), context.fortificationDiffFromLoaded("alchemy-tower"));
    }
}
