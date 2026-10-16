package org.c2w.data.repository;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.domain.ChangePlanChecks;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ChangePlanCheckRepository} and the rules of {@link ChangePlanChecks}. */
class ChangePlanCheckRepositoryTest {

    @TempDir
    Path guildDir;

    @Test
    @DisplayName("Saved checks are read back; without a file or with a broken one there are none")
    void saveAndLoad() throws Exception {
        assertEquals(ChangePlanChecks.EMPTY, ChangePlanCheckRepository.load(guildDir));

        ChangePlanChecks checks = new ChangePlanChecks("Ziel.lineup", Set.of("REMOVE|HERO|m1|0|citadel", "ADD|HERO|m2|1|bastion"));
        ChangePlanCheckRepository.save(guildDir, checks);
        assertTrue(Files.exists(guildDir.resolve("changeplan-checks.json")));
        assertEquals(checks, ChangePlanCheckRepository.load(guildDir));

        Files.writeString(guildDir.resolve("changeplan-checks.json"), "not json");
        assertEquals(ChangePlanChecks.EMPTY, ChangePlanCheckRepository.load(guildDir));
    }

    @Test
    @DisplayName("Rebuilding the plan keeps the checks of entries that still exist; another target starts empty")
    void forPlan() {
        ChangePlanChecks checks = new ChangePlanChecks("current:Ziel.lineup", Set.of("a", "b", "c"));

        ChangePlanChecks rebuilt = checks.forPlan("current:Ziel.lineup", List.of("a", "c", "d"));
        assertEquals(Set.of("a", "c"), rebuilt.checkedIds());
        assertEquals("current:Ziel.lineup", rebuilt.target());

        ChangePlanChecks other = checks.forPlan("algorithm", List.of("a", "b", "c"));
        assertEquals(Set.of(), other.checkedIds());
        assertEquals("algorithm", other.target());
    }

    @Test
    @DisplayName("with checks or unchecks one entry, without drops the applied ones")
    void withAndWithout() {
        ChangePlanChecks checks = ChangePlanChecks.EMPTY.forPlan("t", List.of()).with("a", true).with("b", true);
        assertTrue(checks.isChecked("a"));
        assertEquals(Set.of("b"), checks.with("a", false).checkedIds());
        assertEquals(Set.of("a"), checks.without(List.of("b", "x")).checkedIds());
    }

    @Test
    @DisplayName("The applied types are saved and read back; a file without them (older version) has none")
    void appliedTypes() throws Exception {
        ChangePlanChecks checks = new ChangePlanChecks("current:Ziel.lineup", Set.of("a"), Set.of(FortificationType.HERO));
        ChangePlanCheckRepository.save(guildDir, checks);
        assertTrue(Files.readString(guildDir.resolve("changeplan-checks.json")).contains("\"appliedTypes\""));
        assertEquals(checks, ChangePlanCheckRepository.load(guildDir));

        Files.writeString(guildDir.resolve("changeplan-checks.json"), "{\"target\": \"t\", \"checked\": [\"a\"]}");
        ChangePlanChecks old = ChangePlanCheckRepository.load(guildDir);
        assertEquals(Set.of("a"), old.checkedIds());
        assertEquals(Set.of(), old.appliedTypes());
    }

    @Test
    @DisplayName("The applied types survive a rebuild and checking; another target drops them like the checks")
    void appliedTypesForPlan() {
        ChangePlanChecks checks = new ChangePlanChecks("t", Set.of("a")).withApplied(FortificationType.TITAN);
        assertEquals(Set.of(FortificationType.TITAN), checks.forPlan("t", List.of("a")).appliedTypes());
        assertEquals(Set.of(FortificationType.TITAN), checks.with("b", true).without(List.of("a")).appliedTypes());
        assertEquals(Set.of(), checks.forPlan("other", List.of("a")).appliedTypes());
        assertEquals(Set.of(FortificationType.HERO, FortificationType.TITAN),
                checks.withApplied(FortificationType.HERO).appliedTypes());
    }

    @Test
    @DisplayName("Missing type: none applied → none; heroes applied, titans open → TITAN; titans done or both applied → none")
    void missingLiveType() {
        Guild guild = new Guild("g", "G", List.of());
        ChangePlanOutline both = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.PLACE, new TeamKey("m1", Lineup.TeamType.HERO, 0), null, "citadel"),
                new ChangeStep(ChangeType.PLACE, new TeamKey("m1", Lineup.TeamType.TITAN, 0), null, "bastion-of-fire")), guild);
        ChangePlanOutline heroesOnly = ChangePlanOutline.of(List.of(
                new ChangeStep(ChangeType.PLACE, new TeamKey("m1", Lineup.TeamType.HERO, 0), null, "citadel")), guild);

        assertNull(ChangePlanChecks.missingLiveType(both, Set.of()));
        assertEquals(FortificationType.TITAN, ChangePlanChecks.missingLiveType(both, Set.of(FortificationType.HERO)));
        assertEquals(FortificationType.HERO, ChangePlanChecks.missingLiveType(both, Set.of(FortificationType.TITAN)));
        assertNull(ChangePlanChecks.missingLiveType(heroesOnly, Set.of(FortificationType.HERO)), "no titan entries left");
        assertNull(ChangePlanChecks.missingLiveType(both, Set.of(FortificationType.HERO, FortificationType.TITAN)));
        assertNull(ChangePlanChecks.missingLiveType(null, Set.of(FortificationType.HERO)));
    }
}
