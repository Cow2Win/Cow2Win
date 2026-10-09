package org.c2w.service.journal;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The answers to the questions of an {@link ImportPlan}, by question id. A
 * missing answer means the default: link the guild, take the suggested season
 * start, leave the player {@code OPEN}, leave the name unknown.
 *
 * @param linkGuild   answer to the {@link GuildLinkQuestion}, {@code null} = default (yes)
 * @param seasons     season start per {@link SeasonQuestion} id; {@link Optional#empty()} = no season
 * @param players     answer per {@link PlayerQuestion} id
 * @param names       catalog id (element name for a totem) per {@link UnknownNameQuestion} id
 */
public record ImportAnswers(Boolean linkGuild, Map<String, Optional<LocalDate>> seasons,
                            Map<String, PlayerAnswer> players, Map<String, String> names) {

    public ImportAnswers {
        seasons = seasons == null ? Map.of() : Map.copyOf(seasons);
        players = players == null ? Map.of() : Map.copyOf(players);
        names = names == null ? Map.of() : Map.copyOf(names);
    }

    /** No answers at all - every question gets its default. */
    public static ImportAnswers defaults() {
        return new ImportAnswers(null, Map.of(), Map.of(), Map.of());
    }

    /** These answers with the guild link answered. */
    public ImportAnswers withGuildLink(boolean link) {
        return new ImportAnswers(link, seasons, players, names);
    }

    /** These answers with a season start ({@code null} = no season) for a season question. */
    public ImportAnswers withSeason(String questionId, LocalDate start) {
        Map<String, Optional<LocalDate>> copy = new HashMap<>(seasons);
        copy.put(questionId, Optional.ofNullable(start));
        return new ImportAnswers(linkGuild, copy, players, names);
    }

    /** These answers with a player answer for a player question. */
    public ImportAnswers withPlayer(String questionId, PlayerAnswer answer) {
        Map<String, PlayerAnswer> copy = new HashMap<>(players);
        copy.put(questionId, answer);
        return new ImportAnswers(linkGuild, seasons, copy, names);
    }

    /** These answers with a catalog id for an unknown-name question. */
    public ImportAnswers withName(String questionId, String catalogId) {
        Map<String, String> copy = new HashMap<>(names);
        copy.put(questionId, catalogId);
        return new ImportAnswers(linkGuild, seasons, players, copy);
    }
}
