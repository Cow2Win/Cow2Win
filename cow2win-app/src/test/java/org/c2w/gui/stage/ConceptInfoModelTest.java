package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.LineupComparisonService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ConceptInfoModel#compareWithOriginal} - GUI-free. */
class ConceptInfoModelTest {

    @TempDir
    Path guildDir;

    private final Guild guild = new Guild("alpha", "Alpha", List.of());

    private static Lineup lineup(String name) {
        return new Lineup("alpha", name, "", LocalDateTime.now(), List.of());
    }

    @Test
    @DisplayName("The open lineup is the Original itself: nothing to compare")
    void isOriginal() {
        Path original = LineupFiles.originalPathFor(guildDir);
        ConceptInfoModel.Comparison comparison = ConceptInfoModel.compareWithOriginal(
                guildDir.resolve("guild.json"), original, lineup("Original"), guild, FortificationType.HERO);
        assertEquals(ConceptInfoModel.ComparisonState.IS_ORIGINAL, comparison.state());
    }

    @Test
    @DisplayName("No Original file: no comparison, no exception")
    void noOriginal() {
        ConceptInfoModel.Comparison comparison = ConceptInfoModel.compareWithOriginal(
                guildDir.resolve("guild.json"), guildDir.resolve("target.lineup"), lineup("target"), guild,
                FortificationType.HERO);
        assertEquals(ConceptInfoModel.ComparisonState.NO_ORIGINAL, comparison.state());
    }

    @Test
    @DisplayName("Otherwise the values are those of LineupComparisonService.compare (total, type power, counts)")
    void compared() throws Exception {
        // m1 moved, m2 removed, m3 added.
        Lineup original = new Lineup("alpha", "Original", "", LocalDateTime.now(), List.of(
                new Lineup.Entry("bastion", "m1", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("bastion", "m2", Lineup.TeamType.HERO, 0)));
        LineupRepository.save(original, LineupFiles.originalPathFor(guildDir));
        Lineup current = new Lineup("alpha", "target", "", LocalDateTime.now(), List.of(
                new Lineup.Entry("shooting-range", "m1", Lineup.TeamType.HERO, 0),
                new Lineup.Entry("bastion", "m3", Lineup.TeamType.HERO, 0)));

        for (FortificationType type : FortificationType.values()) {
            ConceptInfoModel.Comparison comparison = ConceptInfoModel.compareWithOriginal(
                    guildDir.resolve("guild.json"), guildDir.resolve("target.lineup"), current, guild, type);
            LineupComparisonService.Summary expected = LineupComparisonService.compare(
                    LineupRepository.load(LineupFiles.originalPathFor(guildDir)), current, guild).summary();

            assertEquals(ConceptInfoModel.ComparisonState.COMPARED, comparison.state());
            assertEquals(expected.totalPowerBefore(), comparison.totalPowerBefore());
            assertEquals(expected.totalPowerAfter(), comparison.totalPowerAfter());
            assertEquals(type == FortificationType.HERO ? expected.heroPowerBefore() : expected.titanPowerBefore(),
                    comparison.typePowerBefore());
            assertEquals(type == FortificationType.HERO ? expected.heroPowerAfter() : expected.titanPowerAfter(),
                    comparison.typePowerAfter());
            assertEquals(expected.movedCount(), comparison.movedCount());
            assertEquals(expected.addedCount(), comparison.addedCount());
            assertEquals(expected.removedCount(), comparison.removedCount());
            assertEquals(1, comparison.movedCount());
            assertEquals(1, comparison.addedCount());
            assertEquals(1, comparison.removedCount());
        }
    }
}
