package org.c2w.data.model;

/**
 * Bonus for titan fortifications: +bonusPercent% on {@code effect} per titan
 * with a matching element in the defending team (e.g. "Health Increase per
 * Fire Titan (8%)").
 *
 * The localized display text is built by {@code org.c2w.i18n.BuffTexts}.
 */
public record ElementBuff(TitanElement element, BuffEffect effect, double bonusPercent) implements Buff {
    public ElementBuff {
        if (element == null) {
            throw new IllegalArgumentException("ElementBuff needs an element");
        }
        if (effect == null) {
            throw new IllegalArgumentException("ElementBuff needs an effect");
        }
    }
}
