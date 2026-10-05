package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.domain.LineupChangePlanService;
import org.c2w.domain.LineupComparisonService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link OutputInfoModel#of} - GUI-free. */
class OutputInfoModelTest {

    private final Guild guild = new Guild("alpha", "Alpha", List.of(
            new GuildMember("m1", "Anna", List.of(), List.of()),
            new GuildMember("m2", "Bert", List.of(), List.of()),
            new GuildMember("m3", "Carl", List.of(), List.of())));

    private static Lineup.Entry hero(String fortificationId, String memberId, int index) {
        return new Lineup.Entry(fortificationId, memberId, Lineup.TeamType.HERO, index);
    }

    @Test
    @DisplayName("Steps per kind, affected players most steps first, power difference of the comparison")
    void values() {
        // Anna: one team moved, one removed (2 steps); Bert: one placed (1 step); Carl unchanged.
        Lineup original = new Lineup("alpha", "Original", "", LocalDateTime.now(), List.of(
                hero("bastion", "m1", 0), hero("bastion", "m1", 1), hero("bastion", "m3", 0)));
        Lineup target = new Lineup("alpha", "target", "", LocalDateTime.now(), List.of(
                hero("shooting-range", "m1", 0), hero("bastion", "m2", 0), hero("bastion", "m3", 0)));
        LineupComparisonService.LineupComparison comparison = LineupComparisonService.compare(original, target, guild);
        ChangePlanModel.Result result = new ChangePlanModel.Result(ChangePlanModel.State.OK, comparison,
                LineupChangePlanService.from(comparison), null, null);

        OutputInfoModel.OutputInfo info = OutputInfoModel.of(result, guild);

        assertTrue(info.hasPlan());
        assertEquals(3, info.totalSteps());
        assertEquals(1, info.removeSteps());
        assertEquals(1, info.moveSteps());
        assertEquals(1, info.placeSteps());
        assertEquals(List.of(new OutputInfoModel.PlayerSteps("Anna", 2), new OutputInfoModel.PlayerSteps("Bert", 1)),
                info.players());
        assertEquals(comparison.summary().totalPowerDiff(), info.totalPowerDiff());
    }

    @Test
    @DisplayName("Without a plan: nothing")
    void noPlan() {
        OutputInfoModel.OutputInfo info = OutputInfoModel.of(ChangePlanModel.Result.of(ChangePlanModel.State.NO_ORIGINAL), guild);
        assertFalse(info.hasPlan());
        assertEquals(List.of(), info.players());
        assertFalse(OutputInfoModel.of(null, guild).hasPlan());
    }
}
