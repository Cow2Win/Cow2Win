package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.HeroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lighthouse buff of season 17 (Skill Cooldown Decrease, 1 % per Support hero) in the defense
 * log of 12.10.2026: 13 Support heroes in the 5 defender teams = 13 %.
 */
class LighthouseBuffLogTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"de", "en", "fr"})
    void defenseLogHasTheLighthouseBuffAndNoProblems(String folder) {
        BattleLogParseResult result = BattleLogTestFiles.parse(folder, BattleLogTestFiles.SEASON_17_DAY, LogDirection.DEFENSE);

        assertEquals(List.of(), result.problems());
        List<Fight> lighthouse = lighthouseFights(result.log());
        assertEquals(7, lighthouse.size());
        for (Fight fight : lighthouse) {
            DefenseBuff buff = fight.defender().buff();
            assertNotNull(buff, "line " + fight.lineNumber());
            assertEquals(BuffEffect.SKILL_COOLDOWN_DECREASE, buff.effect(), "line " + fight.lineNumber());
            assertEquals(13, buff.percent(), "line " + fight.lineNumber());
        }
    }

    @Test
    void catalogCountsThirteenSupportHeroesInTheFiveDefenderTeams(@TempDir Path workspace) {
        Fortification fort = FortificationRepository.findById("lighthouse").orElseThrow();
        assertEquals(new RoleBuff(Role.SUPPORT, BuffEffect.SKILL_COOLDOWN_DECREASE, 1), fort.buff());
        HeroRepository heroes = new HeroRepository(workspace);

        // each position once (its latest fight shows the team as it stands)
        Map<Integer, Fight> byPosition = new TreeMap<>();
        for (Fight fight : lighthouseFights(BattleLogTestFiles.parse("de", BattleLogTestFiles.SEASON_17_DAY,
                LogDirection.DEFENSE).log())) {
            byPosition.put(fight.position(), fight);
        }
        assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(byPosition.keySet()));

        long supportHeroes = byPosition.values().stream()
                .flatMap(f -> f.defender().units().stream())
                .filter(u -> u.kind() == UnitKind.HERO)
                .map(u -> heroes.findById(u.catalogId()).orElseThrow())
                .filter(h -> h.matchesBuff(fort.buff()))
                .count();
        assertEquals(13, supportHeroes);
    }

    private static List<Fight> lighthouseFights(BattleLog log) {
        return log.fights().stream().filter(f -> "lighthouse".equals(f.fortificationId())).toList();
    }
}
