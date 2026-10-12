package org.c2w.eval;

import org.c2w.data.model.Lineup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LineupAlgorithms#forAssignment}/{@link LineupAlgorithms#preselected}: the choices of the concept stage's "algorithm" combo box. */
class LineupAlgorithmsAssignmentChoicesTest {

    @ParameterizedTest
    @EnumSource(Lineup.TeamType.class)
    @DisplayName("forAssignment is forType without \"Manual\", in the same order")
    void forAssignmentWithoutManual(Lineup.TeamType teamType) {
        assertEquals(LineupAlgorithms.forType(teamType).stream()
                        .filter(algorithm -> !(algorithm instanceof ManualLineupAlgorithm)).toList(),
                LineupAlgorithms.forAssignment(teamType));
        assertEquals(LineupAlgorithms.forType(teamType).size() - 1, LineupAlgorithms.forAssignment(teamType).size());
        assertTrue(LineupAlgorithms.forAssignment(teamType).stream().allMatch(algorithm -> algorithm.teamType() == teamType));
    }

    @ParameterizedTest
    @EnumSource(Lineup.TeamType.class)
    @DisplayName("preselected finds a valid name, and falls back to the first choice for \"Manual\", unknown, empty and null")
    void preselected(Lineup.TeamType teamType) {
        LineupAlgorithm second = LineupAlgorithms.forAssignment(teamType).get(1);
        LineupAlgorithm first = LineupAlgorithms.forAssignment(teamType).get(0);

        assertSame(second, LineupAlgorithms.preselected(teamType, second.displayName()));
        assertSame(first, LineupAlgorithms.preselected(teamType, ManualLineupAlgorithm.DISPLAY_NAME));
        assertSame(first, LineupAlgorithms.preselected(teamType, "No such algorithm"));
        assertSame(first, LineupAlgorithms.preselected(teamType, ""));
        assertSame(first, LineupAlgorithms.preselected(teamType, null));
    }
}
