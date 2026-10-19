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
        ImportPlan plan = service().prepare(BattleLogTestFiles.season16Files("de"));
        ImportWizardModel wizard = new ImportWizardModel(plan, context.guild());
        wizard.createAllWithoutSuggestion();
        assertTrue(service().execute(plan, wizard.toAnswers()).isSuccess());
        builder = new JournalTeamBuilderService(context, guildService);
    }

    private TeamBuilderModel model() throws Exception {
        return new TeamBuilderModel(builder.prepare(), context.guild(), context.catalog());
    }

    @Test
    @DisplayName("Preselection; counts of the header and of '{0} of {1} selected'; select all / none")
    void selection() throws Exception {
        TeamBuilderModel model = model();
        List<Proposal> proposals = model.plan().proposals();
        long preselected = proposals.stream().filter(Proposal::preselected).count();
        long selectable = proposals.stream().filter(Proposal::selectable).count();

        assertTrue(preselected > 0);
        assertEquals(preselected, model.sourceCount(), "the header counts the source battles' proposals");
        assertEquals(preselected, model.selectedCount());
        assertEquals(selectable, model.selectableCount());
        assertTrue(model.canApply(), model.problems().toString());
        assertFalse(model.members().isEmpty());
        assertTrue(model.members().stream().allMatch(m -> m.proposals().stream().anyMatch(Proposal::selectable)),
                "only members with a selectable proposal are shown");
        assertEquals(27 - model.members().size(), model.membersWithoutNewTeams());
        assertFalse(model.noMemberAssigned());

        model.selectAll(false);
        assertEquals(0, model.selectedCount());
        assertFalse(model.allSelected());
        assertFalse(model.canApply(), "nothing selected");
        assertEquals(List.of(), model.problems());

        model.selectAll(true);
        assertEquals(selectable, model.selectedCount());
        assertTrue(model.allSelected());
        for (Proposal p : proposals.stream().filter(Proposal::selectable).toList()) {
            assertEquals(p.suggestedTarget(), model.target(p.id()), "each with its suggested target");
        }
        model.selectAll(!model.allSelected());
        assertEquals(0, model.selectedCount(), "all selected: the checkbox deselects all");
    }

    @Test
    @DisplayName("Select all / none of one member; the summary of its selection")
    void member() throws Exception {
        TeamBuilderModel model = model();
        MemberPlan member = model.members().get(0);
        int selectable = model.sourceProposals(member).size() + model.olderProposals(member).size();

        model.selectAll(member.memberId(), false);
        assertFalse(model.allSelected(member.memberId()));
        assertTrue(model.summary(member.memberId()).isEmpty());
        assertTrue(selectable > 0);

        model.selectAll(member.memberId(), true);
        assertTrue(model.allSelected(member.memberId()));
        TeamBuilderModel.Summary summary = model.summary(member.memberId());
        assertEquals(selectable, summary.newHeroTeams() + summary.newTitanTeams() + summary.fills().size());
        long heroes = member.proposals().stream().filter(p -> p.selectable() && p.kind() == TeamKind.HERO).count();
        assertEquals(heroes, summary.newHeroTeams(), "no empty teams: hero proposals are new hero teams");
    }

    @Test
    @DisplayName("An older proposal can be selected (new team); every proposal has a composition")
    void olderProposal() throws Exception {
        TeamBuilderModel model = model();
        assertTrue(model.plan().proposals().stream().allMatch(p -> p.composition() != null));
        Proposal older = model.plan().proposals().stream()
                .filter(p -> !p.fromSourceBattle() && p.selectable()).findFirst().orElseThrow();
        assertFalse(model.isSelected(older.id()));
        assertEquals(List.of(Target.NEW), model.targetChoices(older), "no empty teams, so only new ones");
        MemberPlan member = model.plan().member(older.memberId()).orElseThrow();
        assertTrue(model.olderProposals(member).contains(older));
        assertFalse(model.sourceProposals(member).contains(older));

        model.setSelected(older.id(), true);
        assertEquals(Target.NEW, model.target(older.id()));
        assertTrue(model.canApply(), model.problems().toString());
    }

    @Test
    @DisplayName("Two proposals filling the same empty team: problem at the member and the proposals, apply blocked")
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
        MemberPlan plan = model.plan().member(member.memberId()).orElseThrow();
        assertTrue(model.expandedAtStart(plan), "a target to choose: opened at the start");
        List<Proposal> heroes = plan.proposals().stream()
                .filter(p -> p.kind() == TeamKind.HERO && p.selectable()).toList();
        assertTrue(model.targetChoices(heroes.get(0)).contains(Target.fill(0)));
        model.setTarget(heroes.get(0).id(), Target.fill(0));
        model.setTarget(heroes.get(1).id(), Target.fill(0));

        assertFalse(model.canApply());
        assertTrue(model.problems().stream().anyMatch(e -> e.kind() == TeamBuildResult.Error.Kind.TARGET_TAKEN),
                model.problems().toString());
        assertFalse(model.problemsOf(member.memberId()).isEmpty());
        assertFalse(model.problemsOfProposal(heroes.get(1).id()).isEmpty());
        assertEquals(List.of(), model.generalProblems());
        assertEquals(List.of(new TeamBuilderModel.Fill(TeamKind.HERO, 0), new TeamBuilderModel.Fill(TeamKind.HERO, 0)),
                model.summary(member.memberId()).fills().stream().filter(f -> f.kind() == TeamKind.HERO).toList());
    }
}
