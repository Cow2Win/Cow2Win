package org.c2w.domain;

/**
 * Plausibility rule for a team's total power as typed in by the user - shared
 * by every input field for power (live lineup editor and the hero/titan value
 * overviews).
 */
public final class TeamPower {

    /**
     * Upper bound for a team's power, to catch typos (e.g. one digit too many).
     * Applies to hero and titan teams alike; realistic team power is far below.
     */
    public static final int MAX = 2_000_000;

    private TeamPower() {
    }

    /** Whether {@code power} exceeds {@link #MAX} (MAX itself is allowed). */
    public static boolean isTooHigh(int power) {
        return power > MAX;
    }
}
