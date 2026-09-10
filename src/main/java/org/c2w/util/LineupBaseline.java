package org.c2w.util;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

/**
 * Snapshot of ONE fortification's total power and buff-member count within
 * a lineup, at one point in time (added 2026-09-10; changed the same day to
 * be per-fortification instead of one combined value for the whole lineup -
 * see {@link AppContext#loadedFortificationBaselines()}). AppContext takes
 * one of these per fortification that has at least one team assigned, the
 * moment a ".lineup" file is (re)loaded (see
 * {@link AppContext#set(Lineup, java.nio.file.Path)}), so that later - once
 * the user has changed the in-memory lineup via
 * TeamsOverviewPanel/ToolbarPanel (team assignments, algorithm runs, "clear
 * lineup", ...) - the difference to what is currently saved on disk can be
 * computed per fortification on demand via {@link #diffFrom}, without
 * re-reading/re-parsing the file. Not yet surfaced in the GUI; that is
 * planned for later.
 *
 * totalPower is the sum of {@link Lineup.Entry#totalPower()} across every
 * entry currently assigned to this ONE fortification - a fortification can
 * host several teams at once (see {@link Fortification#capacity()}).
 *
 * buffMemberCount is {@link BuffCalculationService#countMatchingMembersForFortification}
 * for this fortification's own buff - i.e. the number of HEROES with a
 * matching role for a HERO-type fortification (its buff is then a
 * {@link org.c2w.data.model.RoleBuff}), or the number of TITANS with a
 * matching element for a TITAN-type fortification (its buff is then an
 * {@link org.c2w.data.model.ElementBuff}) - see {@link Fortification#type()}.
 * 0 for a fortification without a buff.
 */
public record LineupBaseline(int totalPower, int buffMemberCount) {

    /** Computes the baseline for one fortification within the given lineup/guild. */
    public static LineupBaseline forFortification(Fortification fortification, Lineup lineup, Guild guild) {
        if (fortification == null) {
            throw new IllegalArgumentException("fortification must not be null");
        }
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        int totalPower = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.fortificationId().equals(fortification.id())) {
                totalPower += entry.totalPower();
            }
        }
        int buffMemberCount = BuffCalculationService.countMatchingMembersForFortification(
                fortification.id(), lineup, guild, fortification.buff());
        return new LineupBaseline(totalPower, buffMemberCount);
    }

    /**
     * Difference of {@code other} relative to this baseline (other minus
     * this) - e.g. called as {@code loadedBaseline.diffFrom(currentBaseline)}
     * to see how far the current in-memory lineup has drifted, for this one
     * fortification, from the one loaded from disk. A positive value means
     * {@code other} is higher.
     */
    public Diff diffFrom(LineupBaseline other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return new Diff(other.totalPower - totalPower, other.buffMemberCount - buffMemberCount);
    }

    /** totalPowerDiff/buffMemberCountDiff: see {@link #diffFrom}. */
    public record Diff(int totalPowerDiff, int buffMemberCountDiff) {
    }
}
