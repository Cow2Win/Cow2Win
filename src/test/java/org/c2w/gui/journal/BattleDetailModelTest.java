package org.c2w.gui.journal;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** {@link BattleDetailModel} with the real sample logs imported into a temp guild. */
class BattleDetailModelTest extends JournalGuiTestSupport {

    private JournalRepository repo() throws Exception {
        return context.journal().repository(false).orElseThrow();
    }

    private BattleDetailModel load(LocalDate date) throws Exception {
        int id = repo().listBattles(null).stream().filter(b -> b.date().equals(date)).findFirst().orElseThrow().battleId();
        return BattleDetailModel.load(repo(), id, context.guild()).orElseThrow();
    }

    @Test
    @DisplayName("24.09.: defense grouped per fortification, held/fallen from our side, points per fort match the log")
    void defenseOf24th() throws Exception {
        setSampleMembers();
        importWithDefaults(prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE));
        BattleLog log = BattleLogTestFiles.parse("de", SEP_24, LogDirection.DEFENSE).log();

        BattleDetailModel model = load(LocalDate.of(2026, 9, 24));
        BattleDetailModel.DirectionView defense = model.defense().orElseThrow();

        // every fight once, grouped by fortification, file order inside each group
        assertEquals(log.fights().size(), defense.fights().size());
        Map<String, Integer> pointsPerFort = new LinkedHashMap<>();
        for (BattleLogEntry e : log.entries()) {
            String fort = e instanceof Fight f ? f.fortificationId() : ((FortEvent) e).fortificationId();
            pointsPerFort.merge(fort, e.points(), Integer::sum);
        }
        assertEquals(new ArrayList<>(pointsPerFort.keySet()),
                defense.forts().stream().map(BattleDetailModel.FortGroup::fortificationId).toList(),
                "forts in order of their first row");
        for (BattleDetailModel.FortGroup fort : defense.forts()) {
            assertEquals(pointsPerFort.get(fort.fortificationId()), fort.points(), fort.fortificationName());
            List<Integer> lines = fort.fights().stream().map(BattleDetailModel.FightRow::lineNumber).toList();
            assertEquals(lines.stream().sorted().toList(), lines, "file order");
        }
        assertEquals(log.totalPoints(), defense.points());
        assertEquals(1782, defense.points(), "opponent points of the 24.09.");

        // held / fallen: the result column is the attacker's view - flipped for the defense log
        long attackerWins = log.fights().stream().filter(Fight::attackerWins).count();
        assertEquals(attackerWins, defense.badFights());
        assertEquals(log.fights().size() - attackerWins, defense.goodFights());
        for (int i = 0; i < log.fights().size(); i++) {
            Fight f = log.fights().get(i);
            BattleDetailModel.FightRow row = defense.fights().stream()
                    .filter(r -> r.lineNumber() == f.lineNumber()).findFirst().orElseThrow();
            assertEquals(f.attackerWins() ? BattleDetailModel.Outcome.FELL : BattleDetailModel.Outcome.HELD, row.outcome());
            assertEquals(f.defender().playerName(), row.ourPlayer().rawName(), "our player is the defender");
            assertEquals(f.defender().teamPower(), row.ourPower());
            assertEquals(f.attacker().playerName(), row.opponentName());
            assertEquals(f.points(), row.points());
        }
        assertEquals(defense.forts().stream().filter(BattleDetailModel.FortGroup::captured).toList(),
                defense.capturedForts());
        assertFalse(defense.hasUnits(), "the 24.09. defense log has no units");
        assertNotNull(defense.info());
        assertEquals("deutsch", defense.info().language());

        // attack: won / lost not flipped
        BattleDetailModel.DirectionView attack = model.attack().orElseThrow();
        BattleLog attackLog = BattleLogTestFiles.parse("de", SEP_24, LogDirection.ATTACK).log();
        assertEquals(attackLog.fights().stream().filter(Fight::attackerWins).count(), attack.goodFights());
        assertEquals(attackLog.totalPoints(), attack.points());
        assertTrue(attack.fights().stream().allMatch(r -> r.outcome() == BattleDetailModel.Outcome.WON
                || r.outcome() == BattleDetailModel.Outcome.LOST));
        assertTrue(attack.hasUnits(), "attack logs carry both teams");

        // head data, overview, problems
        assertEquals(BattleLogCheck.Verdict.MATCHES, model.check().verdict());
        assertEquals(193861, model.ownGuild().gameGuildId());
        List<BattleDetailModel.FortOverviewRow> overview = model.fortOverview();
        assertEquals(defense.forts().size() + attack.forts().size(), overview.size());
        assertEquals(LogDirection.DEFENSE, overview.get(0).direction(), "defense first");
        assertFalse(model.hasProblems());
        assertEquals(2, model.logs().size());
    }

    @Test
    @DisplayName("Player labels: assigned, open, and assigned to a member that no longer exists")
    void playerLabels() throws Exception {
        setSampleMembers();
        importWithDefaults(prepare("de", SEP_24, LogDirection.DEFENSE));

        BattleDetailModel model = load(LocalDate.of(2026, 9, 24));
        BattleDetailModel.OwnPlayer puschel = player(model, "Puschel");
        assertEquals(AssignmentStatus.ASSIGNED, puschel.status());
        assertEquals("Puschel", puschel.memberName());
        assertFalse(puschel.memberMissing());
        assertEquals(JournalTexts.text("journal.detail.player.assigned", "Puschel", "Puschel"), puschel.label());

        BattleDetailModel.OwnPlayer open = model.defense().orElseThrow().fights().stream()
                .map(BattleDetailModel.FightRow::ourPlayer).filter(p -> p.status() == AssignmentStatus.OPEN)
                .findFirst().orElseThrow();
        assertEquals(JournalTexts.text("journal.detail.player.status", JournalTexts.visibleSpaces(open.rawName()),
                JournalTexts.of("assignmentStatus", AssignmentStatus.OPEN)), open.label());

        // Puschel leaves the guild in Cow2Win - the journal still points at the member id
        setMembers(List.of(member("team gandagom"), member("Ordensriter")));
        BattleDetailModel after = load(LocalDate.of(2026, 9, 24));
        BattleDetailModel.OwnPlayer gone = player(after, "Puschel");
        assertTrue(gone.memberMissing());
        assertEquals(JournalTexts.text("journal.detail.player.memberMissing", "Puschel"), gone.label());
    }

    @Test
    @DisplayName("17.09.: the defense log carries units - our team and the attacker's")
    void defenseWithUnits() throws Exception {
        importWithDefaults(prepare("de", "17-09-2026", LogDirection.DEFENSE));

        BattleDetailModel.DirectionView defense = load(LocalDate.of(2026, 9, 17)).defense().orElseThrow();

        assertTrue(defense.hasUnits());
        BattleLog log = BattleLogTestFiles.parse("de", "17-09-2026", LogDirection.DEFENSE).log();
        Fight first = log.fights().get(0);
        BattleDetailModel.FightRow row = defense.fights().stream()
                .filter(r -> r.lineNumber() == first.lineNumber()).findFirst().orElseThrow();
        assertEquals(first.defender().units(), row.ourUnits(), "our units are the defender's");
        assertEquals(first.attacker().units(), row.opponentUnits());
        assertTrue(row.hasUnits());
    }

    @Test
    @DisplayName("A battle with only the attack log: no defense view (the dialog shows a hint and 'import')")
    void onlyAttackLog() throws Exception {
        importWithDefaults(prepare("de", "21-09-2026", LogDirection.ATTACK));

        BattleDetailModel model = load(LocalDate.of(2026, 9, 21));

        assertEquals(Optional.empty(), model.defense());
        assertTrue(model.attack().isPresent());
        assertEquals(1, model.logs().size());
        assertEquals(BattleLogCheck.Verdict.NOT_CHECKABLE, model.check().verdict());
        assertTrue(model.fortOverview().stream().allMatch(r -> r.direction() == LogDirection.ATTACK));
    }

    @Test
    @DisplayName("A deleted battle cannot be loaded")
    void deletedBattle() throws Exception {
        importWithDefaults(prepare("de", SEP_24, LogDirection.DEFENSE));
        int id = repo().listBattles(null).get(0).battleId();
        repo().deleteBattle(id);

        assertEquals(Optional.empty(), BattleDetailModel.load(repo(), id, context.guild()));
    }

    private static BattleDetailModel.OwnPlayer player(BattleDetailModel model, String rawName) {
        return model.defense().orElseThrow().fights().stream().map(BattleDetailModel.FightRow::ourPlayer)
                .filter(p -> p.rawName().equals(rawName)).findFirst().orElseThrow();
    }
}
