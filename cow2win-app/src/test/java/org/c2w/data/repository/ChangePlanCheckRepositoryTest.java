package org.c2w.data.repository;

import org.c2w.domain.ChangePlanChecks;
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
}
