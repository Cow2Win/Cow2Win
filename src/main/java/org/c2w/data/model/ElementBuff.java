package org.c2w.data.model;

/**
 * Bonus for titan fortifications: +bonusPercent% on {@code effect} per titan
 * with a matching element in the defending team (e.g. "Health Increase per
 * Fire Titan (8%)").
 *
 * display: see {@link Buff#display()} - free, manually maintained display text.
 */
public record ElementBuff(TitanElement element, BuffEffect effect, double bonusPercent,
                           String display) implements Buff {
    public ElementBuff {
        if (element == null) {
            throw new IllegalArgumentException("ElementBuff needs an element");
        }
        if (effect == null) {
            throw new IllegalArgumentException("ElementBuff needs an effect");
        }
        display = display == null ? "" : display;
    }

    /** Convenience constructor for ElementBuffs without display text. */
    public ElementBuff(TitanElement element, BuffEffect effect, double bonusPercent) {
        this(element, effect, bonusPercent, "");
    }
}
