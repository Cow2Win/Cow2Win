package org.c2w.gui.journal;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.Catalog;
import org.c2w.service.JournalTeamBuilderService;
import org.c2w.service.journal.TeamBuildPlan;
import org.c2w.service.journal.TeamBuildPlan.*;
import org.c2w.service.journal.TeamBuildResult;
import org.c2w.service.journal.TeamBuildSelection;

import java.util.*;

/**
 * State of the "build teams from logs" dialog, without Swing: which proposals
 * are selected and where they go, the members shown (only those with a
 * selectable proposal), the counts of the header, the summary per member and
 * whether the selection can be applied (problems per member and proposal).
 * Starts with the plan's preselection.
 */
public final class TeamBuilderModel {

    /** What the selection of a member adds: new teams per kind and the empty teams it fills. */
    public record Summary(int newHeroTeams, int newTitanTeams, List<Fill> fills) {
        public Summary {
            fills = List.copyOf(fills);
        }

        public boolean isEmpty() {
            return newHeroTeams == 0 && newTitanTeams == 0 && fills.isEmpty();
        }
    }

    /** An empty team that the selection fills. */
    public record Fill(TeamKind kind, int teamIndex) {
    }

    private final TeamBuildPlan plan;
    private final Guild guild;
    private final Catalog catalog;
    private TeamBuildSelection selection;
    private TeamBuildSelection checkedSelection;
    private List<TeamBuildResult.Error> checkedProblems;

    public TeamBuilderModel(TeamBuildPlan plan, Guild guild, Catalog catalog) {
        this.plan = Objects.requireNonNull(plan);
        this.guild = guild;
        this.catalog = catalog;
        this.selection = TeamBuildSelection.preselected(plan);
    }

    public TeamBuildPlan plan() {
        return plan;
    }

    public TeamBuildSelection selection() {
        return selection;
    }

    // --- members ---

    /** The guild member of a member plan (current teams), empty if it is gone. */
    public Optional<GuildMember> guildMember(String memberId) {
        return guild == null ? Optional.empty()
                : guild.members().stream().filter(m -> m.id().equals(memberId)).findFirst();
    }

    /** The members shown: those with at least one selectable proposal. */
    public List<MemberPlan> members() {
        return plan.members().stream().filter(m -> m.proposals().stream().anyMatch(Proposal::selectable)).toList();
    }

    /** The selectable proposals of a member from its newest battle. */
    public List<Proposal> sourceProposals(MemberPlan member) {
        return member.proposals().stream().filter(p -> p.selectable() && p.fromSourceBattle()).toList();
    }

    /** The selectable proposals of a member from older battles. */
    public List<Proposal> olderProposals(MemberPlan member) {
        return member.proposals().stream().filter(p -> p.selectable() && !p.fromSourceBattle()).toList();
    }

    /** Teams of the member's newest battle it already has (with heroes/titans). */
    public int presentCount(MemberPlan member) {
        return (int) member.proposals().stream()
                .filter(p -> p.fromSourceBattle() && p.status() == ProposalStatus.ALREADY_PRESENT).count();
    }

    /** Guild members not shown: no log data, nothing new or only teams known by power. */
    public int membersWithoutNewTeams() {
        return guild == null ? 0 : Math.max(0, guild.members().size() - members().size());
    }

    /** True if no defender of the logs is assigned to a member of the guild. */
    public boolean noMemberAssigned() {
        return guild == null || plan.membersWithoutLogData().size() >= guild.members().size();
    }

    /** Defenders of the logs not assigned to an existing member (not "not in Cow2Win" or "former"). */
    public List<String> unassignedPlayers() {
        return plan.skippedPlayers().stream()
                .filter(s -> s.reason() == SkipReason.NOT_ASSIGNED || s.reason() == SkipReason.MEMBER_MISSING)
                .map(SkippedPlayer::rawName).toList();
    }

    /** Opened at the start: a target to choose or a problem. */
    public boolean expandedAtStart(MemberPlan member) {
        return member.proposals().stream().anyMatch(p -> p.selectable() && targetChoices(p).size() > 1)
                || !problemsOf(member.memberId()).isEmpty();
    }

    // --- counts ---

    /** Selectable proposals of the source battles (the preselection) - the header count. */
    public int sourceCount() {
        return (int) plan.proposals().stream().filter(p -> p.selectable() && p.fromSourceBattle()).count();
    }

    /** All selectable proposals. */
    public int selectableCount() {
        return selectable(plan.proposals()).size();
    }

    public int selectedCount() {
        return selection.targets().size();
    }

    /** What the selection of a member adds. */
    public Summary summary(String memberId) {
        MemberPlan member = plan.member(memberId).orElseThrow();
        int hero = 0;
        int titan = 0;
        List<Fill> fills = new ArrayList<>();
        for (Proposal p : member.proposals()) {
            Target target = selection.targets().get(p.id());
            if (target == null) {
                continue;
            }
            if (target.kind() == Target.Kind.FILL) {
                fills.add(new Fill(p.kind(), target.teamIndex()));
            } else if (p.kind() == TeamKind.HERO) {
                hero++;
            } else {
                titan++;
            }
        }
        fills.sort(Comparator.comparing(Fill::kind).thenComparingInt(Fill::teamIndex));
        return new Summary(hero, titan, fills);
    }

    // --- selection ---

    public boolean isSelected(String proposalId) {
        return selection.targets().containsKey(proposalId);
    }

    /** Selects (with its suggested target, else the first possible one) or deselects a selectable proposal. */
    public void setSelected(String proposalId, boolean selected) {
        Proposal p = proposal(proposalId);
        if (!selected) {
            selection = selection.without(proposalId);
        } else if (p.selectable()) {
            List<Target> targets = targetChoices(p);
            Target target = p.suggestedTarget() != null ? p.suggestedTarget() : targets.isEmpty() ? Target.NEW : targets.get(0);
            selection = selection.with(proposalId, target);
        }
    }

    /** True if every selectable proposal is selected (and there is one). */
    public boolean allSelected() {
        return allSelected(plan.proposals());
    }

    /** True if every selectable proposal of the member is selected (and there is one). */
    public boolean allSelected(String memberId) {
        return allSelected(plan.member(memberId).orElseThrow().proposals());
    }

    /** Selects every selectable proposal (each with its suggested target) or deselects everything. */
    public void selectAll(boolean selected) {
        if (selected) {
            selectable(plan.proposals()).forEach(p -> setSelected(p.id(), true));
        } else {
            selection = TeamBuildSelection.none();
        }
    }

    /** Selects or deselects every selectable proposal of the member. */
    public void selectAll(String memberId, boolean selected) {
        selectable(plan.member(memberId).orElseThrow().proposals()).forEach(p -> setSelected(p.id(), selected));
    }

    /** The target of a selected proposal, else its suggested one. */
    public Target target(String proposalId) {
        Target chosen = selection.targets().get(proposalId);
        return chosen != null ? chosen : proposal(proposalId).suggestedTarget();
    }

    /** Changes the target of a proposal (selects it). */
    public void setTarget(String proposalId, Target target) {
        selection = selection.with(proposalId, Objects.requireNonNull(target));
    }

    /** Where a proposal may go: a new team (if the member has a free slot), or one of its empty teams. */
    public List<Target> targetChoices(Proposal p) {
        MemberPlan member = plan.member(p.memberId()).orElseThrow();
        List<Target> result = new ArrayList<>();
        if (member.freeSlots(p.kind()) > 0) {
            result.add(Target.NEW);
        }
        member.emptyTeams(p.kind()).forEach(i -> result.add(Target.fill(i)));
        return result;
    }

    // --- checks ---

    /** Why the selection cannot be applied - empty if it can (or nothing is selected). */
    public List<TeamBuildResult.Error> problems() {
        if (selection.targets().isEmpty()) {
            return List.of();
        }
        if (selection != checkedSelection) {
            checkedProblems = JournalTeamBuilderService.check(guild, plan, selection, catalog);
            checkedSelection = selection;
        }
        return checkedProblems;
    }

    /** The problems of a member: its own and those of its proposals. */
    public List<TeamBuildResult.Error> problemsOf(String memberId) {
        return problems().stream().filter(e -> memberId.equals(memberOf(e))).toList();
    }

    /** The problems of a proposal. */
    public List<TeamBuildResult.Error> problemsOfProposal(String proposalId) {
        return problems().stream().filter(e -> proposalId.equals(e.proposalId())).toList();
    }

    /** Problems concerning neither a member nor a proposal (e.g. another guild opened). */
    public List<TeamBuildResult.Error> generalProblems() {
        return problems().stream().filter(e -> memberOf(e) == null).toList();
    }

    public boolean canApply() {
        return selectedCount() > 0 && problems().isEmpty();
    }

    private String memberOf(TeamBuildResult.Error e) {
        if (e.memberId() != null) {
            return e.memberId();
        }
        return e.proposalId() == null ? null : plan.proposal(e.proposalId()).map(Proposal::memberId).orElse(null);
    }

    private boolean allSelected(List<Proposal> proposals) {
        List<Proposal> selectable = selectable(proposals);
        return !selectable.isEmpty() && selectable.stream().allMatch(p -> isSelected(p.id()));
    }

    private static List<Proposal> selectable(List<Proposal> proposals) {
        return proposals.stream().filter(Proposal::selectable).toList();
    }

    private Proposal proposal(String id) {
        return plan.proposal(id).orElseThrow(() -> new IllegalArgumentException("Unknown proposal " + id));
    }
}
