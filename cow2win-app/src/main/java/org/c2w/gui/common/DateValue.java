package org.c2w.gui.common;

import org.c2w.i18n.LanguageService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * A last-modified date as a table value: sorts chronologically, an unknown date (null) after
 * every known one; shown in the short format of the configured language (see {@link #shortDate}).
 * Used by the member overview and the live lineup dialog.
 */
public record DateValue(LocalDate date) implements Comparable<DateValue> {

    /** No date - shown empty, sorted last. */
    public static final DateValue NONE = new DateValue(null);

    @Override
    public int compareTo(DateValue other) {
        if (date == null) {
            return other.date == null ? 0 : 1;
        }
        return other.date == null ? -1 : date.compareTo(other.date);
    }

    @Override
    public String toString() {
        return shortDate(date);
    }

    /** {@code date} in the short format of the configured language, "" for null. */
    public static String shortDate(LocalDate date) {
        return date == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(LanguageService.locale()).format(date);
    }
}
