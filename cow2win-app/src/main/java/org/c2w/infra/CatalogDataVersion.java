package org.c2w.infra;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code dataVersion} of a game catalog (see {@link CatalogManifest}): the date of the
 * catalog state, {@code YYYY-MM-DD}, optionally followed by {@code .n} for several states on
 * the same day ({@code 2026-11-02.2}). Ordered by date first, then by {@code n} (no
 * {@code .n} counts as 0) - never as a plain string, so {@code 2026-11-02.10} is newer than
 * {@code 2026-11-02.9}.
 *
 * @param date     the day of the catalog state
 * @param sequence the {@code .n} part, 0 if absent
 */
public record CatalogDataVersion(LocalDate date, int sequence) implements Comparable<CatalogDataVersion> {

    private static final Pattern FORMAT = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:\\.([1-9]\\d{0,8}))?");

    private static final Comparator<CatalogDataVersion> ORDER =
            Comparator.comparing(CatalogDataVersion::date).thenComparingInt(CatalogDataVersion::sequence);

    public CatalogDataVersion {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative: " + sequence);
        }
    }

    /**
     * Parses {@code 2026-10-17} or {@code 2026-10-17.2}.
     *
     * @throws IllegalArgumentException if {@code text} is not a valid data version
     */
    public static CatalogDataVersion parse(String text) {
        Matcher matcher = text == null ? null : FORMAT.matcher(text.strip());
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Invalid catalog data version: " + text);
        }
        try {
            LocalDate date = LocalDate.parse(matcher.group(1));
            return new CatalogDataVersion(date, matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid catalog data version: " + text, e);
        }
    }

    /**
     * The version of a new catalog state saved on {@code today}: {@code today} itself, or - if
     * this version is already from {@code today} (or, with a wrong clock, later) - the next
     * {@code .n} of this version's day.
     */
    public CatalogDataVersion next(LocalDate today) {
        if (date.isBefore(today)) {
            return new CatalogDataVersion(today, 0);
        }
        return new CatalogDataVersion(date, sequence + 1);
    }

    @Override
    public int compareTo(CatalogDataVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return sequence == 0 ? date.toString() : date + "." + sequence;
    }
}
