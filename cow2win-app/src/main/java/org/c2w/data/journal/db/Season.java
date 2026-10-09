package org.c2w.data.journal.db;

import java.time.LocalDate;
import java.time.Period;

/**
 * A season of the journal. Covers {@code start <= day < end}: {@code end} is the
 * first day AFTER the season, so in a gapless raster it equals the next season's
 * start. Seasons live only in the journal, not in {@code guild.json}.
 *
 * @param id     database id
 * @param number season number, unique per journal
 * @param start  first day
 * @param end    first day after the season (exclusive)
 * @param note   free text, may be {@code null}
 */
public record Season(int id, int number, LocalDate start, LocalDate end, String note) {

    /** Standard length of a season. */
    public static final Period DEFAULT_LENGTH = Period.ofWeeks(12);

    public Season {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Season needs start < end: " + start + " / " + end);
        }
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
