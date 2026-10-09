package org.c2w.gui.journal;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.TitanTeam;
import org.c2w.service.JournalTeamBuilderService;
import org.c2w.service.journal.*;
import org.c2w.service.journal.TeamBuildPlan.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link TeamBuilderModel} on an empty guild after importing all 6 battles with "create all". */
class TeamBuilderModelTest extends JournalGuiTestSupport {

    private JournalTeamBuilderService builder;

    @BeforeEach
    void importAll() throws Exception {
        ImportPlan plan = service().prepare(BattleLogTestFiles.files("de"));
        ImportWizardModel wizard = new ImportWizardModel(plan, context.guild());
        wizard.createAllWithoutSuggestion();
        assertTrue(service().execute(plan, wizard.toAnswers()).isSuccess());
        builder = new JournalTeamBuilderService(context, guildService);
    }

    private TeamBuilderModel model() throws Exception {
        return new TeamBuilderModel(builder.prepare(), context.guild(), context.catalog());
    }

    @Test
    @DisplayName("Preselection, clear, restore; counts of the header; filter")
    void selection() throws Exception {
        TeamBuilderModel model = model();
        long preselected = model.plan().proposals().stream().filter(Proposal::preselected).count();

        assertEquals(27, model.memberCount());
        assertEquals(model.plan().proposals().size(), model.proposalCount());
        assertEquals(model.plan().proposals().stream().filter(p -> p.composition() != null).count(),
                model.proposalsWithComposition());
        assertEquals(preselected, model.selectedCount());
        assertTrue(model.canApply());

        model.clearSelection();
        assertEquals(0, model.selectedCount());
        assertFalse(model.canApply(), "nothing selected");
        model.restorePreselection();
        assertEquals(preselected, model.selectedCount());

        model.setOnlyMembersWithoutTeams(true);
        assertEquals(27, model.members().size(), "nobody has teams yet");
    }

    @Test
    @DisplayName("An older proposal can be selected (new team); a known composition is only taken when chosen")
    void olderProposalAndKnownComposition() throws Exception {
        TeamBuilderModel model = model();
        Proposal older = model.plan().proposals().stream()
                .filter(p -> !p.fromSourceBattle() && p.selectable()).findFirst().orElseThrow();
        assertFalse(model.isSelected(older.id()));
        assertEquals(List.of(Target.NEW), model.targetChoices(older), "no empty teams, so only new ones");

        model.setSelected(older.id(), true);
        assertEquals(Target.NEW, model.target(older.id()));
        assertTrue(model.canApply(), model.problems().toString());

        Proposal withKnown = model.plan().proposals().stream()
                .filter(p -> p.composition() == null && !p.knownCompositions().isEmpty()).findFirst().orElseThrow();
        assertNull(model.composition(withKnown.id()), "never set automatically");
        Composition known = withKnown.knownCompositions().get(0);
        model.setKnownComposition(withKnown.id(), known);
        assertEquals(known, model.composition(withKnown.id()));
        model.setKnownComposition(withKnown.id(), null);
        assertNull(model.composition(withKnown.id()));

        Proposal own = model.plan().proposals().stream().filter(p -> p.composition() != null).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> model.setKnownComposition(own.id(), known));
    }

    @Test
    @DisplayName("Two proposals filling the same empty team: problem shown, apply blocked")
    void sameTargetTwice() throws Exception {
        TeamBuildPlan first = builder.prepare();
        MemberPlan member = first.members().stream().filter(m -> m.proposals().stream()
                        .filter(p -> p.kind() == TeamKind.HERO && p.selectable()).count() >= 2)
                .findFirst().orElseThrow();
        List<GuildMember> members = new ArrayList<>(context.guild().members());
        members.replaceAll(m -> m.id().equals(member.memberId())
                ? new GuildMember(m.id(), m.name(), List.of(new HeroTeam(m.id(), 0, List.of(), null, null, 0, null)),
                List.of(new TitanTeam(m.id(), 0, List.of(), 0, null, null))) : m);
        context.setGuild(context.guild().withMembers(members));

        TeamBuilderModel model = model();
        List<Proposal> heroes = model.plan().member(member.memberId()).orElseThrow().proposals().stream()
                .filter(p -> p.kind() == TeamKind.HERO && p.selectable()).toList();
        assertTrue(model.targetChoices(heroes.get(0)).contains(Target.fill(0)));
        model.setTarget(heroes.get(0).id(), Target.fill(0));
        model.setTarget(heroes.get(1).id(), Target.fill(0));

        assertFalse(model.canApply());
        assertTrue(model.problems().stream().anyMatch(e -> e.kind() == TeamBuildResult.Error.Kind.TARGET_TAKEN),
                model.problems().toString());
    }
}
