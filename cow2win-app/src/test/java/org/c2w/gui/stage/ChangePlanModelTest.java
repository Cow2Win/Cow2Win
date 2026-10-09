package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.LineupChangePlanService;
import org.c2w.domain.LineupComparisonService;
import org.c2w.eval.LineupAlgorithms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ChangePlanModel#plan} - GUI-free, with lineup files in a temporary guild folder. */
class ChangePlanModelTest {

    @TempDir
    Path guildDir;

    private final Guild guild = new Guild("alpha", "Alpha", List.of(
            new GuildMember("m1", "Anna", List.of(), List.of()),
            new GuildMember("m2", "Bert", List.of(), List.of())));

    private Path guildFile() {
        return guildDir.resolve("guild.json");
    }

    private static Lineup lineup(String guildId, String name, Lineup.Entry... entries) {
        return new Lineup(guildId, name, "", LocalDateTime.now(), List.of(entries));
    }

    private static Lineup.Entry hero(String fortificationId, String memberId) {
        return new Lineup.Entry(fortificationId, memberId, Lineup.TeamType.HERO, 0);
    }

    private Lineup saveOriginal() throws Exception {
        Lineup original = lineup("alpha", "Original", hero("bastion", "m1"), hero("bastion", "m2"));
        LineupRepository.save(original, LineupFiles.originalPathFor(guildDir));
        return original;
    }

    @Test
    @DisplayName("No Original lineup: NO_ORIGINAL")
    void noOriginal() {
        ChangePlanModel.Result result = ChangePlanModel.plan(guildFile(), guild,
                new ChangePlanModel.CurrentLineup(lineup("alpha", "target"), guildDir.resolve("target.lineup")));
        assertEquals(ChangePlanModel.State.NO_ORIGINAL, result.state());
    }

    @Test
    @DisplayName("The open lineup is the Original itself: ORIGINAL_IS_OPEN")
    void originalIsOpen() throws Exception {
        Lineup original = saveOriginal();
        ChangePlanModel.Result result = ChangePlanModel.plan(guildFile(), guild,
                new ChangePlanModel.CurrentLineup(original, LineupFiles.originalPathFor(guildDir)));
        assertEquals(ChangePlanModel.State.ORIGINAL_IS_OPEN, result.state());
    }

    @Test
    @DisplayName("A target of another guild: DIFFERENT_GUILD")
    void differentGuild() throws Exception {
        saveOriginal();
        LineupRepository.save(lineup("other-guild", "foreign"), guildDir.resolve("foreign.lineup"));
        ChangePlanModel.Result result = ChangePlanModel.plan(guildFile(), guild,
                new ChangePlanModel.SavedLineup("foreign.lineup"));
        assertEquals(ChangePlanModel.State.DIFFERENT_GUILD, result.state());
    }

    @Test
    @DisplayName("No target chosen: NO_TARGET")
    void noTarget() throws Exception {
        saveOriginal();
        assertEquals(ChangePlanModel.State.NO_TARGET,
                ChangePlanModel.plan(guildFile(), guild, new ChangePlanModel.SavedLineup(null)).state());
        assertEquals(ChangePlanModel.State.NO_TARGET,
                ChangePlanModel.plan(guildFile(), guild, new ChangePlanModel.Algorithms(null, null)).state());
    }

    @Test
    @DisplayName("A valid saved target: OK with the same steps as LineupChangePlanService.from(compare(...))")
    void savedTarget() throws Exception {
        Lineup original = saveOriginal();
        Lineup target = lineup("alpha", "target", hero("shooting-range", "m1"));
        LineupRepository.save(target, guildDir.resolve("target.lineup"));

        ChangePlanModel.Result result = ChangePlanModel.plan(guildFile(), guild, new ChangePlanModel.SavedLineup("target.lineup"));

        assertEquals(ChangePlanModel.State.OK, result.state());
        assertEquals(LineupChangePlanService.from(LineupComparisonService.compare(original,
                LineupRepository.load(guildDir.resolve("target.lineup")), guild)), result.steps());
        assertEquals(2, result.steps().size(), "m1 moved, m2 removed");
    }

    @Test
    @DisplayName("The current lineup is used as it stands in memory - an unsaved change counts")
    void currentLineupInMemory() throws Exception {
        saveOriginal();
        Lineup saved = lineup("alpha", "target", hero("bastion", "m1"), hero("bastion", "m2"));
        LineupRepository.save(saved, guildDir.resolve("target.lineup"));
        Lineup inMemory = lineup("alpha", "target", hero("bastion", "m1"), hero("shooting-range", "m2"));

        ChangePlanModel.Result fromFile = ChangePlanModel.plan(guildFile(), guild, new ChangePlanModel.SavedLineup("target.lineup"));
        ChangePlanModel.Result fromMemory = ChangePlanModel.plan(guildFile(), guild,
                new ChangePlanModel.CurrentLineup(inMemory, guildDir.resolve("target.lineup")));

        assertEquals(List.of(), fromFile.steps(), "the file equals the Original");
        assertEquals(1, fromMemory.steps().size(), "the unsaved move of m2");
        assertEquals(LineupChangePlanService.ChangeType.MOVE, fromMemory.steps().get(0).type());
    }

    @Test
    @DisplayName("An algorithm target is computed from the Original")
    void algorithmTarget() throws Exception {
        saveOriginal();
        ChangePlanModel.Result result = ChangePlanModel.plan(guildFile(), guild, new ChangePlanModel.Algorithms(
                LineupAlgorithms.forType(Lineup.TeamType.HERO).get(0), LineupAlgorithms.forType(Lineup.TeamType.TITAN).get(0)));
        assertEquals(ChangePlanModel.State.OK, result.state());
        assertNotNull(result.comparison());
    }

    @Test
    @DisplayName("The target list holds the guild's lineups except the Original")
    void targetFileNames() throws Exception {
        saveOriginal();
        LineupRepository.save(lineup("alpha", "b"), guildDir.resolve("b.lineup"));
        LineupRepository.save(lineup("alpha", "a"), guildDir.resolve("a.lineup"));
        assertEquals(List.of("a.lineup", "b.lineup"), ChangePlanModel.targetLineupFileNames(guildFile()));
    }
}
