package org.c2w.service.journal;

import org.c2w.data.journal.NameMappingKind;

import java.util.Set;

/**
 * A fortification or unit name without a catalog id (after the journal's own
 * name mappings), from either log - only about the readability of the journal,
 * never about the guild. Answer: a catalog id (element name for a totem) or
 * nothing (stays unknown).
 *
 * @param id          stable question id
 * @param kind        what kind of name
 * @param rawName     the name as in the (first) log
 * @param candidates  the catalog ids it is ambiguous between, empty if simply unknown
 * @param occurrences how often the name occurs in the imported files
 */
public record UnknownNameQuestion(String id, NameMappingKind kind, String rawName, Set<String> candidates,
                                  int occurrences) {

    public UnknownNameQuestion {
        candidates = candidates == null ? Set.of() : Set.copyOf(candidates);
    }

    /** The id of the question for a name key ({@code GameNameNormalizer.key}) of a kind. */
    public static String idFor(NameMappingKind kind, String nameKey) {
        return "name:" + kind + ":" + nameKey;
    }
}
