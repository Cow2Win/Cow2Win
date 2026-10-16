package org.c2w.data.journal;

import java.time.LocalDate;
import java.time.Period;

/**
 * A Clash of Worlds season of the game, as computed by {@link SeasonCalendar}.
 * Covers {@code start <= day < end}: {@code end} is the first day AFTER the
 * season (a Monday, the day after the last Sunday).
 *
 * @param number season number of the game
 * @param start  first day (a Monday)
 * @param end    first day after the season (exclusive)
 */
public record Season(int number, LocalDate start, LocalDate end) {

    /** Length of a season: 12 weeks. */
    public static final Period LENGTH = Period.ofWeeks(12);

    public Season {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Season needs start < end: " + start + " / " + end);
        }
    }

    /** The season {@code number} of {@link #LENGTH} starting on {@code start}. */
    public static Season of(int number, LocalDate start) {
        return new Season(number, start, start.plus(LENGTH));
    }

    /** True if {@code day} belongs to this season. */
    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && day.isBefore(end);
    }

    /** The last day of the season (inclusive), for display. */
    public LocalDate lastDay() {
        return end.minusDays(1);
    }
}
