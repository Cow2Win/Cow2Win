package org.c2w.gui.journal;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.PlayerAssignment;
import org.c2w.data.model.*;
import org.c2w.service.JournalSyncService;
import org.c2w.service.journal.SyncPlan;
import org.c2w.service.journal.SyncResult;
import org.c2w.service.journal.SyncRow;
import org.c2w.service.journal.SyncRow.Certainty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link SyncModel} with a constructed guild and defense log. */
class SyncModelTest extends JournalGuiTestSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    private static final List<String> HEROES = List.of("dante", "aurora", "nebula", "sebastian", "martha");
    private static final List<String> HEROES_2 = List.of("galahad", "yasmine", "maya", "jhu", "astaroth");

    private SyncPlan plan;
    private SyncRow sure;
    private SyncRow same;
    private SyncRow unsure;

    @BeforeEach
    void plan() {
        // Max: team 0 1.000.000 -> 1.020.000 (sure), team 1 900.000 unchanged (in the lineup elsewhere);
        // Moritz: 940.000 -> 1.000.000 (unsure), edited after the battle
        setMembers(List.of(
                new GuildMember("max", "Max", List.of(
                        new HeroTeam("max", 0, heroes(HEROES), 1_000_000),
                        new HeroTeam("max", 1, heroes(HEROES_2), 900_000)), List.of()),
                new GuildMember("moritz", "Moritz", List.of(
                        new HeroTeam("moritz", 0, heroes(HEROES), null, null, 940_000, DAY.plusDays(2))), List.of())));
        BattleLog log = new BattleLog(new BattleLogHeader(DAY, null, null, null, null, LogDirection.DEFENSE, "deutsch",
                "test.csv"), List.of(defense("Max", 1, 1_020_000), defense("Max", 2, 900_000),
                defense("Moritz", 3, 1_000_000)));
        Lineup lineup = new Lineup(context.guild().id(), context.guild().name(), "", null,
                List.of(new Lineup.Entry("barracks", "max", Lineup.TeamType.HERO, 1)));
        plan = JournalSyncService.plan(new SyncPlan.Source(1, DAY, null, null, null, "deutsch", "test.csv", null), log,
                Map.of("Max", assigned("max"), "Moritz", assigned("moritz")), context.guild(), lineup,
                context.catalog(), context.guildFilePath());
        sure = plan.rows().stream().filter(r -> r.logPower() == 1_020_000).findFirst().orElseThrow();
        same = plan.rows().stream().filter(r -> r.logPower() == 900_000).findFirst().orElseThrow();
        unsure = plan.rows().stream().filter(r -> r.memberId().equals("moritz")).findFirst().orElseThrow();
    }

    private SyncModel model() {
        return new SyncModel(plan, context.guild(), context.catalog());
    }

    @Test
    @DisplayName("Preselection, filters 'only changes' (default) and 'only unsure', clear, select all sure")
    void selectionAndFilters() {
        SyncModel model = model();
        assertEquals(Certainty.SURE, sure.certainty());
        assertEquals(Certainty.UNSURE, unsure.certainty());

        assertTrue(model.onlyChanges());
        assertEquals(List.of(sure, unsure), model.rows(), "the unchanged row is hidden");
        assertEquals(1, model.selectedCount());
        assertTrue(model.power(sure.id()));
        assertFalse(model.power(unsure.id()));
        assertTrue(model.canApply(), model.problems().toString());
        assertEquals(2, model.changedCount());

        model.setOnlyChanges(false);
        assertEquals(3, model.rows().size());
        model.setOnlyUnsure(true);
        assertEquals(List.of(unsure), model.rows());
        model.setOnlyUnsure(false);

        model.clearSelection();
        assertEquals(0, model.selectedCount());
        assertFalse(model.canApply());
        model.selectAllSure();
        assertEquals(1, model.selectedCount(), "not the unsure one");
        assertTrue(model.power(sure.id()));

        model.setPower(unsure.id(), true);
        assertEquals(2, model.selectedCount(), "an unsure row may be chosen by hand");
        model.setPower(same.id(), true);
        assertFalse(model.power(same.id()), "nothing to take over");
        model.setUnits(sure.id(), true);
        assertFalse(model.units(sure.id()), "the log has no units");
        assertFalse(model.powerSelectable(same.id()));
        assertFalse(model.unitsSelectable(sure.id()));
    }

    @Test
    @DisplayName("Changing the team swaps with the other row of the member - never the same team twice")
    void targetSwap() {
        SyncModel model = model();
        assertEquals(List.of(0, 1), model.targetChoices(sure.id()));

        model.setTarget(sure.id(), 1);

        assertEquals(1, model.target(sure.id()));
        assertEquals(0, model.target(same.id()), "swapped");
        assertNull(model.certainty(sure.id()), "chosen by hand");
        assertTrue(model.power(sure.id()), "still differs from team 2");
        assertFalse(model.unchanged(same.id()), "900.000 differs from team 1");
        assertEquals(List.of(), model.problems());
        assertThrows(IllegalArgumentException.class, () -> model.setTarget(sure.id(), 2));

        model.setPower(same.id(), true);
        assertEquals(List.of(), model.problems());
        assertEquals(List.of(1, 0), List.of(model.selection().choices().get(sure.id()).teamIndex(),
                model.selection().choices().get(same.id()).teamIndex()));
    }

    @Test
    @DisplayName("Hints: edited since the battle, lineup elsewhere, not in the lineup")
    void hints() {
        SyncModel model = model();
        assertTrue(model.hints(unsure.id()).contains(SyncModel.Hint.EDITED_SINCE_BATTLE));
        assertTrue(model.hints(unsure.id()).contains(SyncModel.Hint.NOT_IN_LINEUP));
        assertEquals(List.of(SyncModel.Hint.NOT_IN_LINEUP), model.hints(sure.id()));
        assertEquals(List.of(SyncModel.Hint.LINEUP_OTHER_FORTIFICATION), model.hints(same.id()));
        assertEquals(List.of(), model.membersNotInLog());
        assertEquals("Max", model.memberName("max"));
    }

    @Test
    @DisplayName("The selection is applied by the service; problems block 'apply'")
    void apply() {
        SyncModel model = model();
        SyncResult result = new JournalSyncService(context, guildService).apply(plan, model.selection());
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(1, result.teamsChanged());
        assertEquals(1_020_000, context.guild().members().get(0).heroTeams().get(0).totalPower());
        assertTrue(context.isGuildDirty());

        List<GuildMember> members = new ArrayList<>(context.guild().members());
        members.remove(0);
        setMembers(members);
        SyncModel gone = new SyncModel(plan, context.guild(), context.catalog());
        assertFalse(gone.canApply(), "the member is gone");
        assertEquals(SyncResult.Error.Kind.UNKNOWN_MEMBER, gone.problems().get(0).kind());
    }

    private List<Hero> heroes(List<String> ids) {
        return ids.stream().map(id -> context.catalog().heroes().findById(id).orElseThrow()).toList();
    }

    private static PlayerAssignment assigned(String memberId) {
        return new PlayerAssignment(1, memberId, AssignmentStatus.ASSIGNED, null);
    }

    private static Fight defense(String defender, int position, int power) {
        return new Fight("citadel", "citadel", position, null, true, "Sieg", 35,
                new FightSide("Gegner", 130, 1, null, List.of()), new FightSide(defender, 130, power, null, List.of()), 1);
    }
}
