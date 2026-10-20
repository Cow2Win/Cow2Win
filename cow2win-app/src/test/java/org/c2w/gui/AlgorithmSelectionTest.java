package org.c2w.gui;

import org.c2w.data.model.Lineup;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.infra.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link AlgorithmSelection}: preselected from the config, one choice per fortification type. */
class AlgorithmSelectionTest {

    private String previousHero;
    private String previousTitan;

    @BeforeEach
    void rememberConfig() {
        previousHero = Config.getDefaultHeroAlgorithm();
        previousTitan = Config.getDefaultTitanAlgorithm();
    }

    @AfterEach
    void restoreConfig() {
        Config.setDefaultHeroAlgorithm(previousHero);
        Config.setDefaultTitanAlgorithm(previousTitan);
    }

    @Test
    @DisplayName("Preselected from the config; \"Manual\" preselects the first algorithm of the type")
    void preselectedFromConfig() {
        LineupAlgorithm secondHero = LineupAlgorithms.forAssignment(Lineup.TeamType.HERO).get(1);
        Config.setDefaultHeroAlgorithm(secondHero.displayName());
        Config.setDefaultTitanAlgorithm(ManualLineupAlgorithm.DISPLAY_NAME);

        AlgorithmSelection selection = new AlgorithmSelection();

        assertSame(secondHero, selection.get(Lineup.TeamType.HERO));
        assertSame(LineupAlgorithms.forAssignment(Lineup.TeamType.TITAN).get(0), selection.get(Lineup.TeamType.TITAN));
        assertEquals(LineupAlgorithms.forAssignment(Lineup.TeamType.TITAN), selection.choices(Lineup.TeamType.TITAN));
    }

    @Test
    @DisplayName("set changes only the algorithm's own type, notifies once and never writes the config")
    void setPerType() {
        Config.setDefaultHeroAlgorithm("");
        Config.setDefaultTitanAlgorithm("");
        AlgorithmSelection selection = new AlgorithmSelection();
        LineupAlgorithm titanBefore = selection.get(Lineup.TeamType.TITAN);
        List<Lineup.TeamType> changed = new ArrayList<>();
        selection.addListener(changed::add);
        LineupAlgorithm cowScoreHero = LineupAlgorithms.forAssignment(Lineup.TeamType.HERO).get(1);

        selection.set(cowScoreHero);
        selection.set(cowScoreHero);

        assertSame(cowScoreHero, selection.get(Lineup.TeamType.HERO));
        assertSame(titanBefore, selection.get(Lineup.TeamType.TITAN));
        assertEquals(List.of(Lineup.TeamType.HERO), changed);
        assertEquals("", Config.getDefaultHeroAlgorithm());
    }

    @Test
    @DisplayName("\"Manual\" cannot be chosen")
    void manualIsRefused() {
        AlgorithmSelection selection = new AlgorithmSelection();
        assertThrows(IllegalArgumentException.class, () -> selection.set(new ManualLineupAlgorithm(Lineup.TeamType.HERO)));
    }
}
