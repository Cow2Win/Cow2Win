package org.c2w.data.model;

/**
 * A hero's (or pet's) color rank, as shown in a Clash of Worlds battle log
 * ({@code Rot +2}, {@code Violet +3} ...) - the plus level is kept separately.
 * {@link #GREEN} and {@link #GREY} are very rare and no battle log has shown
 * them so far (see {@code BattleLogVocabulary.UNSEEN_HERO_COLORS}).
 */
public enum HeroColor {
    BLUE,
    GREEN,
    GREY,
    ORANGE,
    RED,
    VIOLET
}
