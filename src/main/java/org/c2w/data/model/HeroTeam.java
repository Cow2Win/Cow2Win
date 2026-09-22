package org.c2w.data.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A hero team (up to 5 heroes) fielded by a guild member. totalPower is the
 * team's total strength as shown in-game - per-hero values are not visible
 * for other players' teams and are therefore not modeled here.
 *
 * index: this team's 0-based slot/position among its member's hero teams
 * (0..{@link #MAX_TEAMS_PER_MEMBER} - 1, see {@link GuildMember}, up to 3
 * hero teams per member). Currently always equal to this team's position in
 * {@link GuildMember#heroTeams()} - added so a team can be referenced by
 * memberId + index alone (see {@link Lineup.Entry#teamIndex()}), without
 * relying on callers to separately track list position themselves.
 *
 * lastModified: date of the last change to this team VIA THE GUI - set when
 * the team is created or subsequently edited (see TeamEditorPanel/
 * MemberEditorPanel), purely informational, not a model invariant. null for
 * teams that have (still) never been created/edited via the GUI (e.g.
 * programmatically created teams like in the Main demo, or legacy data from
 * a guild file without this field).
 */
public record HeroTeam(
        String memberId,
        int index,
        List<Hero> heroes,
        int totalPower,
        LocalDate lastModified
) {
    /** Base weight per hero whose role matches the RoleBuff's role. */
    public static final int ROLE_MATCH_WEIGHT = 1;

    /** Per Clash of Worlds rules, at most 3 hero teams per member (see {@link GuildMember}) - so {@link #index} must be 0, 1 or 2. */
    public static final int MAX_TEAMS_PER_MEMBER = 3;

    public HeroTeam {
        if (totalPower < 0) {
            throw new IllegalArgumentException("totalPower must not be negative");
        }
        if (index < 0 || index >= MAX_TEAMS_PER_MEMBER) {
            throw new IllegalArgumentException(
                    "index must be between 0 and " + (MAX_TEAMS_PER_MEMBER - 1) + ", was: " + index);
        }
        if(heroes != null) {
            heroes = List.copyOf(heroes);
        }
    }

    /** Convenience constructor for hero teams without lastModified. */
    public HeroTeam(String memberId, int index, List<Hero> heroes, int totalPower) {
        this(memberId, index, heroes, totalPower, null);
    }

    /** Convenience constructor for an empty hero team at slot 0, without lastModified. */
    public HeroTeam() {
        this(null, 0, null, 0, null);
    }

    /**
     * Second comparison value besides totalPower: how much the given
     * {@link RoleBuff} helps this team. Every hero WITH the required role
     * contributes {@link #ROLE_MATCH_WEIGHT} points. Heroes WITHOUT the
     * required role do NOT contribute to the score (see {@link RoleBuff}:
     * "+bonusPercent% ... per hero WITH matching role").
     */
    public int buffFitScore(Buff buff) {
        if (!(buff instanceof RoleBuff roleBuff)) {
            throw new IllegalArgumentException("HeroTeam.buffFitScore expects a RoleBuff, was: " + buff);
        }
        int score = 0;
        for (Hero h : heroes) {
            if (h.roles().contains(roleBuff.role())) {
                score += ROLE_MATCH_WEIGHT;
            }
        }
        return score;
    }

    /**
     * Divisor {@link #totalPower()} is scaled down by before adding it to
     * {@link #sortScore()} - totalPower is typically five/six digits, while
     * a hero's {@link Hero#generalScore()} lives on {@link CowScoreTier}'s
     * 0.5-1.1 grid, so this brings both terms to a comparable order of
     * magnitude instead of one completely swamping the other.
     */
    private static final double SORT_SCORE_POWER_DIVISOR = 100_000.0;

    /**
     * Used instead of {@link #buffFitScore(Buff)} to pick a team for a
     * fortification that has NO buff - there is no role to score against
     * there, so this falls back to a general "how good is this team"
     * measure: the sum of every hero's {@link Hero#generalScore()} (some
     * heroes are simply better than others, independent of any specific
     * buff/role) plus {@link #totalPower()} scaled down via
     * {@link #SORT_SCORE_POWER_DIVISOR} - per the user's own formula (added
     * 2026-09-11, see cow2win-verbesserungsvorschlaege.md): generalScore +
     * power / 100 000.
     */
    public double sortScore() {
        double generalScoreSum = heroes.stream().mapToDouble(h -> h.generalScore().value()).sum();
        return generalScoreSum + totalPower() / SORT_SCORE_POWER_DIVISOR;
    }
}
