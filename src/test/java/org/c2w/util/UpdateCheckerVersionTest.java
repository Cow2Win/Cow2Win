package org.c2w.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link UpdateChecker}'s pure version parsing/comparison logic
 * (package-private {@code parseVersion}/{@code compare}) without touching
 * the network - the actual GitHub call in {@link UpdateChecker#checkAsync}
 * is deliberately left untested here (no HTTP mocking infrastructure in
 * this project yet), same trade-off as the rest of this project's tests
 * focusing on the deterministic logic around an I/O boundary rather than
 * the I/O itself.
 */
class UpdateCheckerVersionTest {

    @Test
    void parsesPlainAndVPrefixedVersions() {
        assertParsesTo(new int[]{1, 2, 3}, "1.2.3");
        assertParsesTo(new int[]{1, 2, 3}, "v1.2.3");
        assertParsesTo(new int[]{1, 2, 3}, "V1.2.3");
        assertParsesTo(new int[]{0, 0, 0}, "0.0.0");
        assertParsesTo(new int[]{10, 20, 30}, "  v10.20.30  ");
    }

    @Test
    void rejectsAnythingThatIsNotExactlyMajorMinorPatch() {
        assertTrue(UpdateChecker.parseVersion(null).isEmpty());
        assertTrue(UpdateChecker.parseVersion("").isEmpty());
        assertTrue(UpdateChecker.parseVersion("1.2").isEmpty());
        assertTrue(UpdateChecker.parseVersion("1.2.3.4").isEmpty());
        assertTrue(UpdateChecker.parseVersion("1.2.3-beta").isEmpty());
        assertTrue(UpdateChecker.parseVersion("1.2.3+build42").isEmpty());
        assertTrue(UpdateChecker.parseVersion("release-1.2.3").isEmpty());
        // AppVersion's own fallback must never accidentally parse as a real version.
        assertTrue(UpdateChecker.parseVersion("0.0.0-dev").isEmpty());
    }

    @Test
    void comparesMajorMinorPatchInOrder() {
        assertTrue(UpdateChecker.compare(new int[]{2, 0, 0}, new int[]{1, 9, 9}) > 0);
        assertTrue(UpdateChecker.compare(new int[]{1, 3, 0}, new int[]{1, 2, 9}) > 0);
        assertTrue(UpdateChecker.compare(new int[]{1, 2, 4}, new int[]{1, 2, 3}) > 0);
        assertEquals(0, UpdateChecker.compare(new int[]{1, 2, 3}, new int[]{1, 2, 3}));
        assertTrue(UpdateChecker.compare(new int[]{1, 2, 3}, new int[]{1, 2, 4}) < 0);
    }

    private static void assertParsesTo(int[] expected, String version) {
        Optional<int[]> actual = UpdateChecker.parseVersion(version);
        assertTrue(actual.isPresent(), "expected \"" + version + "\" to parse");
        assertArrayEquals(expected, actual.get());
    }
}
