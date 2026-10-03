package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.HeroColor;
import org.c2w.data.model.TitanElement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Spot checks with concrete values from the sample logs. */
class BattleLogParserSamplesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({"de, Brücke, Feuergeisttotem", "en, Bridge, Fire Spirit Totem", "fr, Pont, Totem de l'esprit du feu"})
    void firstFightOfTheRunningBattle(String folder, String fortName, String totemName) {
        Fight fight = BattleLogTestFiles.parse(folder, "01-10-2026", LogDirection.ATTACK).log().fights().get(0);

        assertEquals(fortName, fight.fortificationName());
        assertEquals("bridge", fight.fortificationId());
        assertEquals(6, fight.position());
        assertTrue(fight.attackerWins());
        assertEquals(35, fight.points());
        assertEquals(TeamKind.TITAN, fight.teamKind());
        assertEquals(4, fight.lineNumber());

        FightSide attacker = fight.attacker();
        assertEquals("Puschel", attacker.playerName());
        assertEquals(130, attacker.level());
        assertEquals(1_037_005, attacker.teamPower());
        assertNull(attacker.buff());
        assertEquals("Жека", fight.defender().playerName());
        assertEquals(892_178, fight.defender().teamPower());
        assertNull(fight.defender().buff());

        FightUnit titan = attacker.units().get(0);
        assertEquals(UnitKind.TITAN, titan.kind());
        assertEquals("Araji", titan.name());
        assertEquals("araji", titan.catalogId());
        assertEquals(6, titan.stars());
        assertEquals(130, titan.level());
        assertEquals(209_561, titan.power());
        assertEquals(9_531_359, titan.damageDealt());
        assertEquals(825_825, titan.damageTaken());
        assertEquals(0, titan.healing());

        assertEquals(6, attacker.units().size(), "5 titans + 1 totem");
        FightUnit totem = attacker.units().get(5);
        assertEquals(UnitKind.TOTEM, totem.kind());
        assertEquals(totemName, totem.name());
        assertEquals(TitanElement.FIRE, totem.totemElement());
        assertNull(totem.catalogId());
        assertEquals(4, totem.stars());
        assertNull(totem.power());
        assertEquals(32_060_697, totem.damageDealt());

        FightUnit defenderTotem = fight.defender().units().get(5);
        assertEquals(TitanElement.EARTH, defenderTotem.totemElement());
        assertEquals(1, defenderTotem.stars());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"de, Akademie der Magier, Kaserne", "en, Mage Academy, Barracks", "fr, Académie des mages, Baraquements"})
    void undefendedPositionsOfTheRunningBattle(String folder, String academy, String barracks) {
        List<FortEvent> events = BattleLogTestFiles.parse(folder, "01-10-2026", LogDirection.ATTACK).log().fortEvents();

        FortEvent first = events.get(0);
        assertEquals(FortEventKind.UNDEFENDED, first.kind());
        assertEquals(academy, first.fortificationName());
        assertEquals("mage-academy", first.fortificationId());
        assertEquals(2, first.freePositions());
        assertEquals(3, first.totalPositions());
        assertEquals(70, first.points());

        FortEvent second = events.get(1);
        assertEquals(barracks, second.fortificationName());
        assertEquals("barracks", second.fortificationId());
        assertEquals(1, second.freePositions());
        assertEquals(3, second.totalPositions());
        assertEquals(35, second.points());
    }

    /** {@code Heldenbrücke (Position: 2)} at line 123 of the German attack log of 01.10.2026. */
    @Test
    void heroFightWithColorPetAndPatronage() {
        Fight fight = fightAtLine("de", "01-10-2026", LogDirection.ATTACK, 123);

        assertEquals("heros-bridge", fight.fortificationId());
        assertEquals(TeamKind.HERO, fight.teamKind());
        assertEquals(6, fight.attacker().units().size());

        FightUnit dante = fight.attacker().units().get(0);
        assertEquals(UnitKind.HERO, dante.kind());
        assertEquals("dante", dante.catalogId());
        assertEquals(HeroColor.RED, dante.color());
        assertEquals("Rot +2", dante.colorText(), "raw text keeps the non-breaking space");
        assertEquals(2, dante.colorLevel());
        assertEquals(197_577, dante.power());
        assertEquals(new Patronage("fenris", "Fenris", 7958), dante.patronage());

        FightUnit pet = fight.attacker().units().get(5);
        assertEquals(UnitKind.PET, pet.kind());
        assertEquals("albus", pet.catalogId());
        assertEquals(HeroColor.VIOLET, pet.color());
        assertEquals(3, pet.colorLevel());
        assertNull(pet.patronage());

        FightUnit lyria = fight.defender().units().get(4);
        assertEquals("lyria", lyria.catalogId());
        assertEquals(HeroColor.RED, lyria.color());
        assertEquals(0, lyria.colorLevel(), "'Rot' without plus level");
        assertEquals(new Patronage("biscuit", "Biskuit", 6899), lyria.patronage());
        assertNull(fight.defender().units().get(0).patronage(), "empty patronage column");

        assertEquals(UnitKind.PET, fight.defender().units().get(5).kind());
        assertEquals("oliver", fight.defender().units().get(5).catalogId());
    }

    @Test
    void defenseLogWithUnits() {
        BattleLog log = BattleLogTestFiles.parse("de", "17-09-2026", LogDirection.DEFENSE).log();
        Fight fight = log.fights().get(0);

        assertEquals("Beloved Avenger", fight.attacker().playerName());
        assertEquals("Puschel", fight.defender().playerName());
        assertTrue(fight.attackerWins());
        assertEquals(TeamKind.TITAN, fight.teamKind());
        assertEquals("brustar", fight.defender().units().get(0).catalogId());
        assertEquals(7, fight.defender().units().size(), "5 titans + 2 totems");
        assertEquals(7, fight.attacker().units().size());
        assertTrue(log.fights().stream().allMatch(Fight::hasUnits), "all 86 fights of 17.09. have units");
    }

    @Test
    void defenseLogWithoutUnits() {
        BattleLog log = BattleLogTestFiles.parse("de", "24-09-2026", LogDirection.DEFENSE).log();
        Fight fight = log.fights().get(0);

        assertEquals("Sieg", fight.resultText());
        assertTrue(fight.attackerWins(), "result is always from the attacker's point of view");
        assertEquals("Verwirrt", fight.attacker().playerName());
        assertEquals("Puschel", fight.defender().playerName());
        assertEquals(List.of(), fight.attacker().units());
        assertEquals(List.of(), fight.defender().units());
        assertNull(fight.teamKind());
        assertTrue(log.fights().stream().anyMatch(f -> !f.attackerWins()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"de, Champi und Gnon", "en, Mushy and Shroom", "fr, Champi et Gnon"})
    void mushyAndShroomInAllLanguages(String folder, String name) {
        Fight fight = fightAtLine(folder, "17-09-2026", LogDirection.ATTACK, 289);
        FightUnit unit = fight.attacker().units().stream()
                .filter(u -> u.name().equals(name)).findFirst().orElseThrow();

        assertEquals("mushy-and-shroom", unit.catalogId());
        assertEquals(UnitKind.HERO, unit.kind());
    }

    @Test
    void playerNameWithDoubledSpaceStaysUnchanged() {
        Fight fight = fightAtLine("de", "01-10-2026", LogDirection.DEFENSE, 38);

        // The cell is "Vale  (130-485697)": the name ends with a space, the second space separates the level.
        assertEquals("Vale ", fight.defender().playerName());
        assertEquals(485_697, fight.defender().teamPower());
        DefenseBuff buff = fight.defender().buff();
        assertEquals(BuffEffect.DAMAGE_RESIST, buff.effect());
        assertEquals(14, buff.percent());
        assertEquals("Widerstand gegen Schaden (14%)", buff.rawText());
    }

    @Test
    void buffsInAllLanguages() {
        for (String folder : List.of("de", "en", "fr")) {
            Fight fight = fightAtLine(folder, "01-10-2026", LogDirection.DEFENSE, 38);
            assertEquals(BuffEffect.DAMAGE_RESIST, fight.defender().buff().effect(), folder);
        }
    }

    /** The fight whose row is at {@code line}. */
    private static Fight fightAtLine(String folder, String day, LogDirection direction, int line) {
        return BattleLogTestFiles.parse(folder, day, direction).log().fights().stream()
                .filter(f -> f.lineNumber() == line).findFirst()
                .orElseThrow(() -> new AssertionError("no fight at line " + line));
    }
}
