package org.c2w.data.journal.db;

/** What a manual name mapping maps to: a catalog id, or a titan element for a totem. */
public enum NameMappingKind {
    FORTIFICATION,
    HERO,
    PET,
    TITAN,
    /** {@code catalogId} is the {@code TitanElement} name. */
    TOTEM
}
