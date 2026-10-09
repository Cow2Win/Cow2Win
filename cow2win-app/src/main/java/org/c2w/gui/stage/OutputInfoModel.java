package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI-free values of the info panel of {@link OutputStageView}, computed from a
 * {@link ChangePlanModel.Result}: steps per kind, the affected players and the effect on the
 * total power.
 */
public final class OutputInfoModel {

    private OutputInfoModel() {
    }

    /** A member with at least one step. */
    public record PlayerSteps(String name, int steps) {
    }

    /**
     * The info panel's values; without a plan ({@code hasPlan} false) everything else is 0/empty.
     *
     * @param players the affected members, most steps first (then by name)
     */
    public record OutputInfo(boolean hasPlan, int totalSteps, int removeSteps, int moveSteps, int placeSteps,
                             List<PlayerSteps> players, int totalPowerBefore, int totalPowerAfter) {

        public int totalPowerDiff() {
            return totalPowerAfter - totalPowerBefore;
        }
    }

    /** The values for {@code result}; member names come from {@code guild}. */
    public static OutputInfo of(ChangePlanModel.Result result, Guild guild) {
        if (result == null || !result.isOk()) {
            return new OutputInfo(false, 0, 0, 0, 0, List.of(), 0, 0);
        }
        List<ChangeStep> steps = result.steps();
        Map<String, Integer> stepsByMember = new LinkedHashMap<>();
        for (ChangeStep step : steps) {
            stepsByMember.merge(step.teamKey().teamMemberId(), 1, Integer::sum);
        }
        List<PlayerSteps> players = new ArrayList<>();
        stepsByMember.forEach((memberId, count) ->
                players.add(new PlayerSteps(ChangePlanRenderer.memberName(memberId, guild), count)));
        players.sort(Comparator.comparingInt(PlayerSteps::steps).reversed()
                .thenComparing(PlayerSteps::name, String.CASE_INSENSITIVE_ORDER));
        return new OutputInfo(true, steps.size(), count(steps, ChangeType.REMOVE), count(steps, ChangeType.MOVE),
                count(steps, ChangeType.PLACE), List.copyOf(players),
                result.comparison().summary().totalPowerBefore(), result.comparison().summary().totalPowerAfter());
    }

    private static int count(List<ChangeStep> steps, ChangeType type) {
        return (int) steps.stream().filter(step -> step.type() == type).count();
    }
}
