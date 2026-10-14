package org.c2w.data.journal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The Weltenschlacht seasons of the game - the same for every guild. A season
 * lasts {@link Season#LENGTH 12 weeks} and starts on a Monday; normally a
 * 1-week break follows, so a new season starts every 13 weeks.
 *
 * <p>The known season starts are shipped in {@link #RESOURCE} (exceptions of
 * the game such as a longer break are added there with the next release).
 * After the last known start the calendar continues in the 13-week cycle;
 * before the first known start there is no season, and a day in a break
 * belongs to no season either.
 *
 * <p>The season of a journal battle is always computed from its date with
 * this calendar - it is neither stored nor editable.
 */
public final class SeasonCalendar {

    /** Classpath resource with the known season starts, one line {@code number=yyyy-MM-dd} per season. */
    public static final String RESOURCE = "/journal/season-calendar.properties";

    /** Season plus the normal break, in days: the distance between two starts after the last known one. */
    private static final long CYCLE_DAYS = 13 * 7;

    private static SeasonCalendar standard;

    /** Known starts by season number, gapless and ascending. */
    private final NavigableMap<Integer, LocalDate> starts;

    /**
     * A calendar with these known starts (tests pass their own).
     *
     * @throws IllegalStateException if the numbers are not gapless, a start is no
     *                               Monday or two starts are less than 12 weeks apart
     */
    public SeasonCalendar(Map<Integer, LocalDate> knownStarts) {
        if (knownStarts == null || knownStarts.isEmpty()) {
            throw new IllegalStateException("Season calendar needs at least one season start");
        }
        TreeMap<Integer, LocalDate> sorted = new TreeMap<>(knownStarts);
        Map.Entry<Integer, LocalDate> previous = null;
        for (Map.Entry<Integer, LocalDate> e : sorted.entrySet()) {
            if (e.getValue() == null) {
                throw new IllegalStateException("Season " + e.getKey() + " has no start");
            }
            if (e.getValue().getDayOfWeek() != DayOfWeek.MONDAY) {
                throw new IllegalStateException("Season " + e.getKey() + " starts on " + e.getValue()
                        + ", which is no Monday");
            }
            if (previous != null) {
                if (e.getKey() != previous.getKey() + 1) {
                    throw new IllegalStateException("Season numbers must be gapless: " + previous.getKey()
                            + " is followed by " + e.getKey());
                }
                if (e.getValue().isBefore(previous.getValue().plus(Season.LENGTH))) {
                    throw new IllegalStateException("Season " + e.getKey() + " starts on " + e.getValue()
                            + ", less than 12 weeks after season " + previous.getKey() + " ("
                            + previous.getValue() + ")");
                }
            }
            previous = e;
        }
        this.starts = Collections.unmodifiableNavigableMap(sorted);
    }

    /** The calendar of the shipped {@link #RESOURCE}, loaded once. */
    public static synchronized SeasonCalendar standard() {
        if (standard == null) {
            standard = new SeasonCalendar(load(RESOURCE));
        }
        return standard;
    }

    /** Reads {@code number=yyyy-MM-dd} lines from a classpath resource. */
    static Map<Integer, LocalDate> load(String resource) {
        Properties properties = new Properties();
        try (InputStream in = SeasonCalendar.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Season calendar not found on classpath: " + resource);
            }
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read season calendar " + resource, e);
        }
        Map<Integer, LocalDate> result = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            try {
                result.put(Integer.parseInt(key.strip()), LocalDate.parse(properties.getProperty(key).strip()));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid season calendar line in " + resource + ": "
                        + key + "=" + properties.getProperty(key), e);
            }
        }
        return result;
    }

    /** The season containing {@code day}; empty in a break or before the first known season. */
    public Optional<Season> seasonOf(LocalDate day) {
        if (day == null || day.isBefore(starts.firstEntry().getValue())) {
            return Optional.empty();
        }
        Map.Entry<Integer, LocalDate> last = starts.lastEntry();
        Season candidate;
        if (!day.isBefore(last.getValue())) {
            long cycles = ChronoUnit.DAYS.between(last.getValue(), day) / CYCLE_DAYS;
            candidate = season(last.getKey() + (int) cycles);
        } else {
            // the last known season starting on or before the day (starts ascend with the numbers)
            Map.Entry<Integer, LocalDate> known = starts.firstEntry();
            for (Map.Entry<Integer, LocalDate> e : starts.entrySet()) {
                if (e.getValue().isAfter(day)) {
                    break;
                }
                known = e;
            }
            candidate = Season.of(known.getKey(), known.getValue());
        }
        return candidate.contains(day) ? Optional.of(candidate) : Optional.empty();
    }

    /** The number of the season containing {@code day}, see {@link #seasonOf}. */
    public Optional<Integer> numberOf(LocalDate day) {
        return seasonOf(day).map(Season::number);
    }

    /**
     * Season {@code number}: a known one, or one computed after the last known
     * start in the 13-week cycle.
     *
     * @throws IllegalArgumentException for a number before the first known season
     */
    public Season season(int number) {
        LocalDate start = starts.get(number);
        if (start != null) {
            return Season.of(number, start);
        }
        if (number < starts.firstKey()) {
            throw new IllegalArgumentException("Season " + number + " is before the first known season "
                    + starts.firstKey());
        }
        Map.Entry<Integer, LocalDate> last = starts.lastEntry();
        return Season.of(number, last.getValue().plusDays(CYCLE_DAYS * (number - last.getKey())));
    }
}
