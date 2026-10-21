package org.c2w.gui;

import org.c2w.data.model.Lineup;
import org.c2w.domain.LineupKeyFigures;
import org.c2w.domain.LineupKeyFigures.Direction;
import org.c2w.gui.LineupComparisonDialog.Figure;
import org.c2w.gui.fort.LineupSummaryPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The GUI-free texts and directions of the key figures table of the {@link LineupComparisonDialog}. */
class LineupComparisonFiguresTest {

    private static final LineupKeyFigures CURRENT = new LineupKeyFigures(56_200_000, 600.0, 70, 55, 55, 6);
    private static final LineupKeyFigures COMPARED = new LineupKeyFigures(56_500_000, 612.4, 66, 54, 55, 4);

    @Test
    @DisplayName("Values are shown like the summary; the compared ones with their difference in brackets")
    void texts() {
        assertEquals(LineupSummaryPanel.formatPower(56_200_000), LineupComparisonDialog.figureText(Figure.POWER, CURRENT, "HODOR"));
        assertEquals("55/55", LineupComparisonDialog.figureText(Figure.SLOTS, CURRENT, "HODOR"));
        assertEquals("HODOR", LineupComparisonDialog.figureText(Figure.ALGORITHM, CURRENT, "HODOR"));

        assertEquals(LineupSummaryPanel.formatPower(56_500_000) + " (+0,3)",
                LineupComparisonDialog.comparedFigureText(Figure.POWER, CURRENT, COMPARED, "MAX_BUFF"));
        assertEquals("612,4 (+12,4)", LineupComparisonDialog.comparedFigureText(Figure.COW_SCORE, CURRENT, COMPARED, "MAX_BUFF"));
        assertEquals("66 (-4)", LineupComparisonDialog.comparedFigureText(Figure.BUFF_HITS, CURRENT, COMPARED, "MAX_BUFF"));
        assertEquals("4 (-2)", LineupComparisonDialog.comparedFigureText(Figure.TEAMS_OUTSIDE, CURRENT, COMPARED, "MAX_BUFF"));
        assertEquals("54/55", LineupComparisonDialog.comparedFigureText(Figure.SLOTS, CURRENT, COMPARED, "MAX_BUFF"),
                "no difference for the slots");
        assertEquals("MAX_BUFF", LineupComparisonDialog.comparedFigureText(Figure.ALGORITHM, CURRENT, COMPARED, "MAX_BUFF"),
                "no difference for the algorithm");
    }

    @Test
    @DisplayName("More power, CowScore and buff matches are better; fewer teams left out are better")
    void directions() {
        assertEquals(Direction.BETTER, LineupComparisonDialog.figureDirection(Figure.POWER, CURRENT, COMPARED));
        assertEquals(Direction.BETTER, LineupComparisonDialog.figureDirection(Figure.COW_SCORE, CURRENT, COMPARED));
        assertEquals(Direction.WORSE, LineupComparisonDialog.figureDirection(Figure.BUFF_HITS, CURRENT, COMPARED));
        assertEquals(Direction.BETTER, LineupComparisonDialog.figureDirection(Figure.TEAMS_OUTSIDE, CURRENT, COMPARED));
        assertEquals(Direction.WORSE, LineupComparisonDialog.figureDirection(Figure.TEAMS_OUTSIDE, COMPARED, CURRENT));
        assertEquals(Direction.EQUAL, LineupComparisonDialog.figureDirection(Figure.SLOTS, CURRENT, COMPARED));
        assertEquals(Direction.EQUAL, LineupComparisonDialog.figureDirection(Figure.ALGORITHM, CURRENT, COMPARED));
    }

    @Test
    @DisplayName("The algorithm: 'Live' for the live lineup, its name otherwise, a dash if it has none")
    void algorithm() {
        Lineup hodor = new Lineup("g1", "Guild", "Heroes: HODOR", null, List.of());
        Lineup none = new Lineup("g1", "Guild", "", null, List.of());
        assertEquals("Live", LineupComparisonDialog.algorithmText(hodor, true, "Live"));
        assertEquals("Heroes: HODOR", LineupComparisonDialog.algorithmText(hodor, false, "Live"));
        assertEquals(LineupComparisonDialog.DASH, LineupComparisonDialog.algorithmText(none, false, "Live"));
    }

    @Test
    @DisplayName("Six rows in a fixed order")
    void rows() {
        assertArrayEquals(new Figure[]{Figure.ALGORITHM, Figure.POWER, Figure.COW_SCORE, Figure.BUFF_HITS,
                Figure.SLOTS, Figure.TEAMS_OUTSIDE}, Figure.values());
    }
}
