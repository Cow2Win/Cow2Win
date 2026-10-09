package org.c2w.domain;

import org.c2w.data.model.Lineup;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LineupChangePlanService}: one step per changed team, removals first, placements last. */
class LineupChangePlanServiceTest {

    private final LineupFixture fixture = new LineupFixture();

    @Test
    @DisplayName("removed, moved and added teams become REMOVE, MOVE and PLACE steps in that order")
    void stepsInOrder() {
        List<ChangeStep> steps = LineupChangePlanService.from(
                LineupComparisonService.compare(fixture.before, fixture.after, fixture.guild));

        assertEquals(List.of(
                new ChangeStep(ChangeType.REMOVE, new TeamKey("m1", Lineup.TeamType.TITAN, 0), fixture.fortT, null),
                new ChangeStep(ChangeType.MOVE, new TeamKey("m1", Lineup.TeamType.HERO, 1), fixture.fortA, fixture.fortB),
                new ChangeStep(ChangeType.PLACE, new TeamKey("m2", Lineup.TeamType.HERO, 0), null, fixture.fortC)),
                steps);
    }

    @Test
    @DisplayName("an unchanged lineup needs no steps")
    void noChanges() {
        assertTrue(LineupChangePlanService.from(
                LineupComparisonService.compare(fixture.after, fixture.after, fixture.guild)).isEmpty());
    }
}
