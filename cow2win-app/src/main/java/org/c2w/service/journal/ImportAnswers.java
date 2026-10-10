package org.c2w.service.journal;

import java.util.HashMap;
import java.util.Map;

/**
 * The answers to the questions of an {@link ImportPlan}, by question id. A
 * missing answer means the default: link the guild, leave the player
 * {@code OPEN}, leave the name unknown.
 *
 * @param linkGuild   answer to the {@link GuildLinkQuestion}, {@code null} = default (yes)
 * @param players     answer per {@link PlayerQuestion} id
 * @param names       catalog id (element name for a totem) per {@link UnknownNameQuestion} id
 */
public record ImportAnswers(Boolean linkGuild, Map<String, PlayerAnswer> players, Map<String, String> names) {

    public ImportAnswers {
        players = players == null ? Map.of() : Map.copyOf(players);
        names = names == null ? Map.of() : Map.copyOf(names);
    }

    /** No answers at all - every question gets its default. */
    public static ImportAnswers defaults() {
        return new ImportAnswers(null, Map.of(), Map.of());
    }

    /** These answers with the guild link answered. */
    public ImportAnswers withGuildLink(boolean link) {
        return new ImportAnswers(link, players, names);
    }

    /** These answers with a player answer for a player question. */
    public ImportAnswers withPlayer(String questionId, PlayerAnswer answer) {
        Map<String, PlayerAnswer> copy = new HashMap<>(players);
        copy.put(questionId, answer);
        return new ImportAnswers(linkGuild, copy, names);
    }

    /** These answers with a catalog id for an unknown-name question. */
    public ImportAnswers withName(String questionId, String catalogId) {
        Map<String, String> copy = new HashMap<>(names);
        copy.put(questionId, catalogId);
        return new ImportAnswers(linkGuild, players, copy);
    }
}
