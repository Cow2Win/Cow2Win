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
 * are selected and where they go, chosen known compositions, the filter "only
 * members without teams", the counts of the header and whether the selection
 * can be applied. Starts with the plan's preselection.
 */
public final class TeamBuilderModel {

    private final TeamBuildPlan plan;
    private final Guild guild;
    private final Catalog catalog;
    private TeamBuildSelection selection;
    private boolean onlyMembersWithoutTeams;

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

    /** True if the member has no team at all. */
    public boolean withoutTeams(String memberId) {
        return guildMember(memberId).map(m -> m.heroTeams().isEmpty() && m.titanTeams().isEmpty()).orElse(true);
    }

    public void setOnlyMembersWithoutTeams(boolean only) {
        this.onlyMembersWithoutTeams = only;
    }

    public boolean onlyMembersWithoutTeams() {
        return onlyMembersWithoutTeams;
    }

    /** The members shown (filter applied). */
    public List<MemberPlan> members() {
        return plan.members().stream().filter(m -> !onlyMembersWithoutTeams || withoutTeams(m.memberId())).toList();
    }

    /** Names of the guild members without any log data. */
    public List<String> membersWithoutLogData() {
        return plan.membersWithoutLogData().stream()
                .map(id -> guildMember(id).map(GuildMember::name).orElse(id)).toList();
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

    /** Deselects everything ("clear selection"). */
    public void clearSelection() {
        selection = TeamBuildSelection.none();
    }

    /** Back to the plan's preselection. */
    public void restorePreselection() {
        selection = TeamBuildSelection.preselected(plan);
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

    // --- compositions ---

    /** The composition that will be applied: the proposal's own, or a chosen known one, or none. */
    public Composition composition(String proposalId) {
        Proposal p = proposal(proposalId);
        return p.composition() != null ? p.composition() : selection.compositions().get(proposalId);
    }

    /** Takes over a known composition for a proposal without one ({@code null} = power only). */
    public void setKnownComposition(String proposalId, Composition known) {
        Proposal p = proposal(proposalId);
        if (p.composition() != null) {
            throw new IllegalStateException("The proposal has its own composition");
        }
        if (known != null && !p.knownCompositions().contains(known)) {
            throw new IllegalArgumentException("Not a known composition of the proposal");
        }
        selection = selection.withComposition(proposalId, known);
    }

    // --- header and checks ---

    /** Members with proposals. */
    public int memberCount() {
        return plan.members().size();
    }

    public int proposalCount() {
        return plan.proposals().size();
    }

    /** Proposals with their own composition (defense units or exact attack power). */
    public int proposalsWithComposition() {
        return (int) plan.proposals().stream().filter(p -> p.composition() != null).count();
    }

    public int selectedCount() {
        return selection.targets().size();
    }

    /** Why the selection cannot be applied - empty if it can. */
    public List<TeamBuildResult.Error> problems() {
        return JournalTeamBuilderService.check(guild, plan, selection, catalog);
    }

    public boolean canApply() {
        return selectedCount() > 0 && problems().isEmpty();
    }

    /** True if a proposal concerns a hero team. */
    public static boolean isHero(Proposal p) {
        return p.kind() == TeamKind.HERO;
    }

    private Proposal proposal(String id) {
        return plan.proposal(id).orElseThrow(() -> new IllegalArgumentException("Unknown proposal " + id));
    }
}
