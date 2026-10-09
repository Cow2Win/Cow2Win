package org.c2w.data.model;

/**
 * The combat roles of titans in the Hero Wars web/browser version (Dominion Era) -
 * deliberately separate from the heroes' {@link Role} (different values). A titan can
 * have several roles at once (e.g. Araji = MARKSMAN + SUPPORT).
 *
 * <p>"Super titan" is deliberately NOT a role but a property of its own, see
 * {@link Titan#superTitan()}.
 */
public enum TitanRole {
    TANK,
    MARKSMAN,
    MAGE,
    SUPPORT,
    SUMMONER
}
