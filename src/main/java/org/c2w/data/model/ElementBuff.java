package org.c2w.data.model;

import java.util.List;

/**
 * Bonus for titan fortifications: +bonusPercent% on {@code effect} per titan
 * with a matching element in the defending team (e.g. "Health Increase per
 * Fire Titan (8%)").
 *
 * buffProfits: see {@link Buff#buffProfits()} - here a simple list of titan
 * ids that get an additional bonus for THIS buff (see
 * TitanTeam#buffFitScore(Buff)), on top of the normal element match.
 *
 * display: see {@link Buff#display()} - free, manually maintained display text.
 */
public record ElementBuff(TitanElement element, BuffEffect effect, double bonusPercent, List<String> buffProfits,
                           String display) implements Buff {
    public ElementBuff {
        if (element == null) {
            throw new IllegalArgumentException("ElementBuff needs an element");
        }
        if (effect == null) {
            throw new IllegalArgumentException("ElementBuff needs an effect");
        }
        buffProfits = buffProfits == null ? List.of() : List.copyOf(buffProfits);
        display = display == null ? "" : display;
    }

    /** Convenience constructor for ElementBuffs without especially highlighted titans and without display text. */
    public ElementBuff(TitanElement element, BuffEffect effect, double bonusPercent) {
        this(element, effect, bonusPercent, List.of(), "");
    }
}
