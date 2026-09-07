package org.c2w.data.model;

import java.util.List;

/**
 * Bonus for hero fortifications: +bonusPercent% on {@code effect} per hero
 * with a matching role in the defending team (e.g. "Health Increase per Tank
 * Hero (8%)").
 *
 * buffProfits: see {@link Buff#buffProfits()} - here a simple list of hero
 * ids that get an additional bonus for THIS buff (see
 * HeroTeam#buffFitScore(Buff)), on top of the normal role match.
 *
 * display: see {@link Buff#display()} - free, manually maintained display text.
 */
public record RoleBuff(Role role, BuffEffect effect, double bonusPercent, List<String> buffProfits, String display)
        implements Buff {
    public RoleBuff {
        if (role == null) {
            throw new IllegalArgumentException("RoleBuff needs a role");
        }
        if (effect == null) {
            throw new IllegalArgumentException("RoleBuff needs an effect");
        }
        buffProfits = buffProfits == null ? List.of() : List.copyOf(buffProfits);
        display = display == null ? "" : display;
    }

    /** Convenience constructor for RoleBuffs without especially highlighted heroes and without display text. */
    public RoleBuff(Role role, BuffEffect effect, double bonusPercent) {
        this(role, effect, bonusPercent, List.of(), "");
    }
}
