package org.c2w.data.model;

/**
 * Bonus for hero fortifications: +bonusPercent% on {@code effect} per hero
 * with a matching role in the defending team (e.g. "Health Increase per Tank
 * Hero (8%)").
 *
 * The localized display text is built by {@code org.c2w.util.BuffTexts}.
 */
public record RoleBuff(Role role, BuffEffect effect, double bonusPercent)
        implements Buff {
    public RoleBuff {
        if (role == null) {
            throw new IllegalArgumentException("RoleBuff needs a role");
        }
        if (effect == null) {
            throw new IllegalArgumentException("RoleBuff needs an effect");
        }
    }
}
