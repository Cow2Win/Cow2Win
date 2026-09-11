package org.c2w.data.model;

/**
 * Bonus for hero fortifications: +bonusPercent% on {@code effect} per hero
 * with a matching role in the defending team (e.g. "Health Increase per Tank
 * Hero (8%)").
 *
 * display: see {@link Buff#display()} - free, manually maintained display text.
 */
public record RoleBuff(Role role, BuffEffect effect, double bonusPercent, String display)
        implements Buff {
    public RoleBuff {
        if (role == null) {
            throw new IllegalArgumentException("RoleBuff needs a role");
        }
        if (effect == null) {
            throw new IllegalArgumentException("RoleBuff needs an effect");
        }
        display = display == null ? "" : display;
    }

    /** Convenience constructor for RoleBuffs without display text. */
    public RoleBuff(Role role, BuffEffect effect, double bonusPercent) {
        this(role, effect, bonusPercent, "");
    }
}
