package org.c2w.data.journal.db;

/**
 * A manual mapping of a name the catalog does not know (e.g. a new hero) to a
 * catalog id - looked up via {@code GameNameNormalizer}, stored with the raw name.
 *
 * @param kind      what is mapped
 * @param rawName   the name as first mapped
 * @param catalogId catalog id ({@code TitanElement} name for a totem)
 */
public record NameMapping(NameMappingKind kind, String rawName, String catalogId) {
}
