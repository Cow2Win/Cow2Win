package org.c2w.gui.journal;

import org.c2w.data.journal.BattleLog;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Fortification;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** {@link BattleReplay} and {@link BattleDetailModel#replay} with the sample logs of 01.10.2026. */
class BattleReplayTest extends JournalGuiTestSupport {

    private static final String OCT_01 = "01-10-2026";
    private static final Set<String> OPEN_AT_START = Set.of("bridge", "barracks", "lighthouse", "mage-academy");

    private static BattleReplay replay(LogDirection direction) {
        BattleLog log = BattleLogTestFiles.parse("de", OCT_01, direction).log();
        return new BattleReplay(direction, log.entries().stream().map(BattleDetailModel.ReplayStep::of).toList(),
                FortificationRepository.findAll());
    }

    private static long count(BattleReplay replay, BattleDetailModel.ReplayStep.Kind kind) {
        return replay.steps().stream().filter(s -> s.kind() == kind).count();
    }

    @Test
    @DisplayName("Model: one step per log row in file order, empty without a log")
    void modelSteps() throws Exception {
        importWithDefaults(prepare("de", OCT_01, LogDirection.ATTACK));
        JournalRepository repo = context.journal().repository(false).orElseThrow();
        int id = repo.listBattles().stream().filter(b -> b.date().equals(LocalDate.of(2026, 10, 1)))
                .findFirst().orElseThrow().battleId();
        BattleDetailModel model = BattleDetailModel.load(repo, id, context.guild()).orElseThrow();

        BattleLog log = BattleLogTestFiles.parse("de", OCT_01, LogDirection.ATTACK).log();
        List<BattleDetailModel.ReplayStep> steps = model.replay(LogDirection.ATTACK);
        assertEquals(log.entries().size(), steps.size());
        List<Integer> lines = steps.stream().map(BattleDetailModel.ReplayStep::lineNumber).toList();
        assertEquals(lines.stream().sorted().toList(), lines, "file order");
        assertTrue(model.replay(LogDirection.DEFENSE).isEmpty(), "no defense log");
    }

    @Test
    @DisplayName("Attack 01.10.: 92 steps, points sum up to the log total, first steps as in the log")
    void attackSteps() {
        BattleReplay replay = replay(LogDirection.ATTACK);
        BattleLog log = BattleLogTestFiles.parse("de", OCT_01, LogDirection.ATTACK).log();
        assertEquals(92, replay.size());
        assertEquals(71, count(replay, BattleDetailModel.ReplayStep.Kind.FIGHT));
        assertEquals(9, count(replay, BattleDetailModel.ReplayStep.Kind.UNDEFENDED));
        assertEquals(12, count(replay, BattleDetailModel.ReplayStep.Kind.CAPTURED));
        assertEquals(log.totalPoints(), replay.stateAt(replay.size()).points());
        assertEquals(4990, replay.stateAt(replay.size()).points());
        assertEquals(12, replay.stateAt(replay.size()).captured());

        BattleDetailModel.ReplayStep s1 = replay.steps().get(0);
        assertEquals(BattleDetailModel.ReplayStep.Kind.UNDEFENDED, s1.kind());
        assertEquals("mage-academy", s1.fortificationId());
        assertEquals(2, s1.freePositions());
        BattleDetailModel.ReplayStep s3 = replay.steps().get(2);
        assertEquals(BattleDetailModel.ReplayStep.Kind.FIGHT, s3.kind());
        assertEquals("bridge", s3.fortificationId());
        assertEquals(6, s3.position());
        assertEquals(35, s3.points());
        BattleDetailModel.ReplayStep s11 = replay.steps().get(10);
        assertEquals(BattleDetailModel.ReplayStep.Kind.CAPTURED, s11.kind());
        assertEquals("bridge", s11.fortificationId());
    }

    @Test
    @DisplayName("Defense 01.10.: 67 steps, no undefended positions")
    void defenseSteps() {
        BattleReplay replay = replay(LogDirection.DEFENSE);
        BattleLog log = BattleLogTestFiles.parse("de", OCT_01, LogDirection.DEFENSE).log();
        assertEquals(67, replay.size());
        assertEquals(61, count(replay, BattleDetailModel.ReplayStep.Kind.FIGHT));
        assertEquals(0, count(replay, BattleDetailModel.ReplayStep.Kind.UNDEFENDED));
        assertEquals(6, count(replay, BattleDetailModel.ReplayStep.Kind.CAPTURED));
        assertEquals(log.totalPoints(), replay.stateAt(replay.size()).points());
    }

    @Test
    @DisplayName("Start: nothing beaten or captured, only the four fortifications without prerequisites are open")
    void start() {
        for (LogDirection direction : LogDirection.values()) {
            BattleReplay.State state = replay(direction).stateAt(0);
            assertEquals(0, state.step());
            assertNull(state.current());
            assertEquals(0, state.points());
            assertEquals(FortificationRepository.count(), state.forts().size());
            for (BattleReplay.FortState f : state.forts().values()) {
                assertTrue(f.beaten().isEmpty(), f.fort().id());
                assertTrue(f.repelled().isEmpty(), f.fort().id());
                assertTrue(f.free().isEmpty(), f.fort().id());
                assertFalse(f.captured(), f.fort().id());
                assertFalse(f.current(), f.fort().id());
                assertEquals(!OPEN_AT_START.contains(f.fort().id()), f.locked(), f.fort().id());
            }
            assertTrue(state.currentFort().isEmpty());
        }
    }

    @Test
    @DisplayName("Attack step 11: bridge captured, its follow-ups unlocked, undefended positions of the mage academy")
    void attackAfterBridge() {
        BattleReplay.State state = replay(LogDirection.ATTACK).stateAt(11);
        BattleReplay.FortState bridge = state.forts().get("bridge");
        assertTrue(bridge.captured());
        assertFalse(bridge.locked());
        assertTrue(bridge.current());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), bridge.beaten());
        assertTrue(bridge.repelled().isEmpty(), "beaten in the end - no longer red");
        for (String id : List.of("bastion-of-fire", "bastion-of-ice", "gates-of-nature", "spring-of-elements",
                "foundry", "engineerium")) {
            assertFalse(state.forts().get(id).locked(), id);
        }
        assertTrue(state.forts().get("heros-bridge").locked());
        BattleReplay.FortState academy = state.forts().get("mage-academy");
        assertEquals(3, academy.capacity());
        assertEquals(Set.of(1, 3), academy.free(), "never fought at 3 and 1 - highest first");
        assertEquals(1, state.captured());
        int sum = replay(LogDirection.ATTACK).steps().subList(0, 11).stream()
                .mapToInt(BattleDetailModel.ReplayStep::points).sum();
        assertEquals(sum, state.points());
    }

    @Test
    @DisplayName("Good / bad always from our side")
    void goodAndBad() {
        BattleReplay attack = replay(LogDirection.ATTACK);
        BattleDetailModel.ReplayStep s6 = attack.steps().get(5);
        assertEquals("bridge", s6.fortificationId());
        assertEquals(3, s6.position());
        assertFalse(s6.attackerWins());
        assertEquals(attack.stateAt(5).bad() + 1, attack.stateAt(6).bad(), "a lost attack is bad");

        BattleReplay defense = replay(LogDirection.DEFENSE);
        BattleDetailModel.ReplayStep d1 = defense.steps().get(0);
        assertEquals("bridge", d1.fortificationId());
        assertEquals(6, d1.position());
        assertFalse(d1.attackerWins());
        BattleReplay.State state = defense.stateAt(1);
        assertEquals(1, state.good(), "a repelled attack is good");
        assertEquals(0, state.bad());
        assertTrue(state.forts().get("bridge").beaten().isEmpty(), "the attacker lost - nothing beaten");
        assertEquals(Set.of(6), state.forts().get("bridge").repelled(), "the attacker lost - red dot");
        assertTrue(attack.stateAt(6).forts().get("bridge").repelled().contains(3), "lost attack at position 3 - red");

        BattleReplay.State end = defense.stateAt(defense.size());
        long fell = defense.steps().stream().filter(s -> s.kind() == BattleDetailModel.ReplayStep.Kind.FIGHT
                && s.attackerWins()).count();
        assertEquals(fell, end.bad());
        assertEquals(61 - fell, end.good());
    }

    @Test
    @DisplayName("A step with an unknown fortification counts, but marks no fortification")
    void unknownFortification() {
        List<BattleDetailModel.ReplayStep> steps = new ArrayList<>(replay(LogDirection.ATTACK).steps().subList(0, 3));
        steps.add(new BattleDetailModel.ReplayStep(BattleDetailModel.ReplayStep.Kind.FIGHT, null, "Neues Fort", 1,
                true, "A", 1, "B", 2, 0, 40, 999));
        BattleReplay replay = new BattleReplay(LogDirection.ATTACK, steps, FortificationRepository.findAll());
        BattleReplay.State state = replay.stateAt(4);
        assertEquals(4, state.step());
        assertEquals(steps.stream().mapToInt(BattleDetailModel.ReplayStep::points).sum(), state.points());
        assertEquals(2, state.good());
        assertTrue(state.currentFort().isEmpty());
        assertTrue(state.forts().values().stream().noneMatch(BattleReplay.FortState::current));
    }

    @Test
    @DisplayName("Map: no two fortifications share a grid cell (with the collision overrides)")
    void mapCells() {
        List<Fortification> forts = FortificationRepository.findAll();
        Set<String> cells = forts.stream().map(f -> {
            int[] c = BattleReplayMapPanel.cell(f);
            assertTrue(c[0] >= 0 && c[0] < BattleReplayMapPanel.ROWS, f.id());
            assertTrue(c[1] >= 0 && c[1] < BattleReplayMapPanel.COLUMNS, f.id());
            return c[0] + "/" + c[1];
        }).collect(Collectors.toSet());
        assertEquals(forts.size(), cells.size());
    }
}
