package org.c2w.service.journal;

import java.time.LocalDate;
import java.util.List;

/**
 * Asks for the season of battles outside every known season. Battles that fall
 * into the same suggested season share one question. Answer: a start date
 * (end = start + 12 weeks) or "no season"; no answer = the suggestion.
 *
 * @param id              stable question id
 * @param kind            first season, a new one after the last, or an earlier one
 * @param suggestedStart  suggested first day (12-week raster)
 * @param suggestedNumber suggested season number
 * @param battleDates     the battle days this question decides
 */
public record SeasonQuestion(String id, Kind kind, LocalDate suggestedStart, int suggestedNumber,
                             List<LocalDate> battleDates) {

    public SeasonQuestion {
        battleDates = battleDates == null ? List.of() : List.copyOf(battleDates);
    }

    /** Which situation the question is about. */
    public enum Kind {
        /** The journal has no season yet - suggestion: the battle day. */
        FIRST,
        /** The battle is after the last season - suggestion: continue the 12-week raster. */
        NEW,
        /** The battle is before the first season - suggestion: the raster backwards. */
        EARLIER
    }

    /** The id of the question for a season suggested to start on {@code start}. */
    public static String idFor(LocalDate start) {
        return "season:" + start;
    }
}
