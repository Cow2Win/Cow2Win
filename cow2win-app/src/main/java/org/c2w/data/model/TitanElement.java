package org.c2w.data.model;

/**
 * A titan's element. Known values per the user's titan catalog: FIRE, WATER,
 * EARTH, LIGHT, DARK, as well as the rare special element DISTORTION
 * (e.g. for event titans like Alecto).
 *
 * <p>Every element also stands for exactly one totem a titan team can field
 * (fire totem = FIRE etc.) - see {@link TitanTeam#totems()}.
 */
public enum TitanElement {
    FIRE,
    WATER,
    EARTH,
    LIGHT,
    DARK,
    DISTORTION
}
