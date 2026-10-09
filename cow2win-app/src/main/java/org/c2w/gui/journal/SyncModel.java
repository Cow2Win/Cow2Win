package org.c2w.gui.journal;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.Catalog;
import org.c2w.service.JournalSyncService;
import org.c2w.service.journal.SyncPlan;
import org.c2w.service.journal.SyncResult;
import org.c2w.service.journal.SyncRow;
import org.c2w.service.journal.SyncRow.Certainty;
import org.c2w.service.journal.SyncRow.Target;
import org.c2w.service.journal.SyncSelection;
import org.c2w.service.journal.SyncSelection.Choice;

import java.util.*;

/**
 * State of the sync dialog, without Swing: per row the target team and the
 * checkboxes "power" and "units", the filters "only changes" (default on) and
 * "only unsure", the counts and whether the selection can be applied. Changing
 * the target of a row to a team another row of the member has swaps the two, so
 * a team is never the target of two rows. Starts with the plan's preselection.
 */
public final class SyncModel {

    /** Hints of a row (for the "hints" column). */
    public enum Hint {
        /** The team was changed after the battle day. */
        EDITED_SINCE_BATTLE,
        /** The team has no heroes/titans - only the power; filling it is "build teams from logs". */
        EMPTY_TEAM,
        /** The log has units for this team, but not every name is known. */
        UNITS_UNRESOLVED,
        /** The open lineup has the team in another fortification. */
        LINEUP_OTHER_FORTIFICATION,
        /** The open lineup does not have the team. */
        NOT_IN_LINEUP
    }

    private final SyncPlan plan;
    private final Guild guild;
    private final Catalog catalog;
    private final Map<String, Choice> choices = new LinkedHashMap<>();
    private boolean onlyChanges = true;
    private boolean onlyUnsure;

    public SyncModel(SyncPlan plan, Guild guild, Catalog catalog) {
        this.plan = Objects.requireNonNull(plan);
        this.guild = guild;
        this.catalog = catalog;
        restorePreselection();
    }

    public SyncPlan plan() {
        return plan;
    }

    /** The current choices as selection for the service. */
    public SyncSelection selection() {
        return new SyncSelection(choices);
    }

    // --- filters ---

    public boolean onlyChanges() {
        return onlyChanges;
    }

    public void setOnlyChanges(boolean only) {
        this.onlyChanges = only;
    }

    public boolean onlyUnsure() {
        return onlyUnsure;
    }

    public void setOnlyUnsure(boolean only) {
        this.onlyUnsure = only;
    }

    /** The rows shown (filters applied), grouped by member as in the plan. */
    public List<SyncRow> rows() {
        return plan.rows().stream()
                .filter(r -> !onlyChanges || !unchanged(r.id()) || isSelected(r.id()))
                .filter(r -> !onlyUnsure || certainty(r.id()) != Certainty.UNITS && certainty(r.id()) != Certainty.SURE)
                .toList();
    }

    // --- per row ---

    /** The team the row changes. */
    public int target(String rowId) {
        return choices.get(rowId).teamIndex();
    }

    /** The stored team the row changes. */
    public Target targetTeam(String rowId) {
        return row(rowId).target(target(rowId)).orElseThrow();
    }

    /** The teams the row may change: every team of the member of the row's kind. */
    public List<Integer> targetChoices(String rowId) {
        return row(rowId).targets().stream().map(Target::index).toList();
    }

    /**
     * Changes the team of a row; another row of the member with that team gets this row's
     * previous team. Checkboxes that no longer apply are cleared.
     */
    public void setTarget(String rowId, int index) {
        SyncRow row = row(rowId);
        if (!targetChoices(rowId).contains(index)) {
            throw new IllegalArgumentException("Not a team of the row: " + index);
        }
        int previous = target(rowId);
        if (previous == index) {
            return;
        }
        for (SyncRow other : plan.rowsOf(row.memberId())) {
            if (!other.id().equals(rowId) && other.kind() == row.kind() && target(other.id()) == index) {
                put(other.id(), previous, choices.get(other.id()).power(), choices.get(other.id()).units());
            }
        }
        put(rowId, index, choices.get(rowId).power(), choices.get(rowId).units());
    }

    /** The certainty of the row's match; {@code null} if the user picked another team ("manual"). */
    public Certainty certainty(String rowId) {
        SyncRow row = row(rowId);
        return target(rowId) == row.suggestedIndex() ? row.certainty() : null;
    }

    /** True if nothing would change for the row's current team. */
    public boolean unchanged(String rowId) {
        return row(rowId).unchanged(target(rowId));
    }

    public boolean powerSelectable(String rowId) {
        return row(rowId).powerChanged(target(rowId));
    }

    public boolean unitsSelectable(String rowId) {
        return targetTeam(rowId).unitsSelectable();
    }

    public boolean power(String rowId) {
        return choices.get(rowId).power();
    }

    public boolean units(String rowId) {
        return choices.get(rowId).units();
    }

    public void setPower(String rowId, boolean power) {
        Choice c = choices.get(rowId);
        put(rowId, c.teamIndex(), power, c.units());
    }

    public void setUnits(String rowId, boolean units) {
        Choice c = choices.get(rowId);
        put(rowId, c.teamIndex(), c.power(), units);
    }

    /** True if the row takes over anything. */
    public boolean isSelected(String rowId) {
        return choices.get(rowId).any();
    }

    /** The hints of the row for its current team. */
    public List<Hint> hints(String rowId) {
        SyncRow row = row(rowId);
        Target t = targetTeam(rowId);
        List<Hint> hints = new ArrayList<>();
        if (t.editedSinceBattle()) {
            hints.add(Hint.EDITED_SINCE_BATTLE);
        }
        if (t.empty()) {
            hints.add(Hint.EMPTY_TEAM);
        }
        if (row.logHasUnits() && row.logUnits() == null) {
            hints.add(Hint.UNITS_UNRESOLVED);
        }
        if (t.lineupHint() != null) {
            hints.add(t.lineupHint().kind() == SyncRow.LineupHint.Kind.NOT_IN_LINEUP ? Hint.NOT_IN_LINEUP
                    : Hint.LINEUP_OTHER_FORTIFICATION);
        }
        return hints;
    }

    // --- whole selection ---

    /** Selects the power of every sure row (units or power ≤ 3 %, matched team, power differs, not edited since). */
    public void selectAllSure() {
        for (SyncRow r : plan.rows()) {
            if (target(r.id()) == r.suggestedIndex() && r.preselectPower()) {
                setPower(r.id(), true);
            }
        }
    }

    /** Deselects everything (targets stay). */
    public void clearSelection() {
        for (String id : List.copyOf(choices.keySet())) {
            Choice c = choices.get(id);
            choices.put(id, new Choice(c.teamIndex(), false, false));
        }
    }

    /** Back to the plan's preselection (matched teams). */
    public void restorePreselection() {
        choices.clear();
        choices.putAll(SyncSelection.preselected(plan).choices());
    }

    public int selectedCount() {
        return (int) choices.values().stream().filter(Choice::any).count();
    }

    /** Rows whose team would change (any target). */
    public int changedCount() {
        return (int) plan.rows().stream().filter(r -> !unchanged(r.id())).count();
    }

    /** Why the selection cannot be applied - empty if it can. */
    public List<SyncResult.Error> problems() {
        return JournalSyncService.check(guild, plan, selection(), catalog);
    }

    public boolean canApply() {
        return selectedCount() > 0 && problems().isEmpty();
    }

    /** The name of a guild member (its id if it is gone). */
    public String memberName(String memberId) {
        return guild == null ? memberId : guild.members().stream().filter(m -> m.id().equals(memberId))
                .map(GuildMember::name).findFirst().orElse(memberId);
    }

    /** Names of the guild members not defending in the log. */
    public List<String> membersNotInLog() {
        return plan.membersNotInLog().stream().map(this::memberName).toList();
    }

    private void put(String rowId, int index, boolean power, boolean units) {
        SyncRow row = row(rowId);
        Target t = row.target(index).orElseThrow();
        choices.put(rowId, new Choice(index, power && row.powerChanged(index), units && t.unitsSelectable()));
    }

    private SyncRow row(String id) {
        return plan.row(id).orElseThrow(() -> new IllegalArgumentException("Unknown row " + id));
    }
}
