package org.c2w.gui.stage;

import org.c2w.data.model.Lineup;
import org.c2w.domain.ChangePlanChecks;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.ChangePlanOutline.FortificationGroup;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The checkbox of a fortification, GUI-free: {@link ChangePlanChecks#with(java.util.Collection, boolean)}
 * and the group state of {@link ChangePlanModel} ({@code checkedCount}, {@code allChecked}, {@code toggleTarget}).
 */
class ChangePlanFortificationCheckTest {

    /** The additions to the citadel: three entries. */
    private static final FortificationGroup GROUP = ChangePlanOutline.of(List.of(
                    new ChangeStep(ChangeType.PLACE, hero("m1"), null, "citadel"),
                    new ChangeStep(ChangeType.PLACE, hero("m2"), null, "citadel"),
                    new ChangeStep(ChangeType.PLACE, hero("m3"), null, "citadel")), null)
            .types().get(0).sections().get(0).fortifications().get(0);

    private static TeamKey hero(String memberId) {
        return new TeamKey(memberId, Lineup.TeamType.HERO, 0);
    }

    private static List<String> ids() {
        return GROUP.items().stream().map(ChangePlanOutline.Item::id).toList();
    }

    private static ChangePlanChecks checked(String... ids) {
        return new ChangePlanChecks("target", Set.of(ids));
    }

    @Test
    @DisplayName("with(ids, true/false) checks or unchecks exactly these ids, the others stay")
    void withSeveralIds() {
        ChangePlanChecks checks = new ChangePlanChecks("target", Set.of("a", "b"), Set.of());

        ChangePlanChecks added = checks.with(List.of("b", "c", "d"), true);
        assertEquals(Set.of("a", "b", "c", "d"), added.checkedIds());
        assertEquals("target", added.target());

        ChangePlanChecks removed = added.with(List.of("a", "c", "x"), false);
        assertEquals(Set.of("b", "d"), removed.checkedIds());
    }

    @Test
    @DisplayName("None checked: count 0, not all, a click checks all")
    void noneChecked() {
        ChangePlanChecks checks = checked();
        assertEquals(0, ChangePlanModel.checkedCount(GROUP, checks));
        assertFalse(ChangePlanModel.allChecked(GROUP, checks));
        assertTrue(ChangePlanModel.toggleTarget(GROUP, checks));
    }

    @Test
    @DisplayName("Some checked: the count, not all, a click checks all")
    void someChecked() {
        ChangePlanChecks checks = checked(ids().get(0), ids().get(2), "other");
        assertEquals(2, ChangePlanModel.checkedCount(GROUP, checks));
        assertFalse(ChangePlanModel.allChecked(GROUP, checks));
        assertTrue(ChangePlanModel.toggleTarget(GROUP, checks));
    }

    @Test
    @DisplayName("All checked: count n, all, a click unchecks all")
    void allChecked() {
        ChangePlanChecks checks = new ChangePlanChecks("target", Set.copyOf(ids()));
        assertEquals(3, ChangePlanModel.checkedCount(GROUP, checks));
        assertTrue(ChangePlanModel.allChecked(GROUP, checks));
        assertFalse(ChangePlanModel.toggleTarget(GROUP, checks));
    }
}
