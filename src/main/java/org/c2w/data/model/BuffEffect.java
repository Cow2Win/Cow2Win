package org.c2w.data.model;

/**
 * The kind of effect a fortification buff grants ("Defense Buff" column).
 * List based on the 20 fortification types known so far - new fortifications
 * may introduce further effect kinds.
 */
public enum BuffEffect {
    HEALTH_INCREASE,
    SKILL_COOLDOWN_DECREASE,
    ENERGY_GAIN,
    MAGIC_DEFENSE_INCREASE,
    DAMAGE_RESIST,
    HEALING_INCREASE,
    ARMOR_INCREASE
}
