package org.c2w.data.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A hero team (up to 5 heroes) fielded by a guild member. totalPower is the
 * team's total strength as shown in-game - per-hero values are not visible
 * for other players' teams and are therefore not modeled here.
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
        List<Hero> heroes,
        int totalPower,
        LocalDate lastModified
) {
    /** Base weight per hero whose role matches the RoleBuff's role. */
    public static final int ROLE_MATCH_WEIGHT = 1;

    /** Additional bonus if the hero is also listed in {@link RoleBuff#buffProfits()}. */
    public static final int BUFF_PROFIT_BONUS_WEIGHT = 2;

    public HeroTeam {
        if (totalPower < 0) {
            throw new IllegalArgumentException("totalPower must not be negative");
        }
        if(heroes != null) {
            heroes = List.copyOf(heroes);
        }
    }

    /** Convenience constructor for hero teams without lastModified. */
    public HeroTeam(String memberId, List<Hero> heroes, int totalPower) {
        this(memberId, heroes, totalPower, null);
    }

    /** Convenience constructor for hero teams without lastModified. */
    public HeroTeam() {
        this(null, null, 0, null);
    }

    /**
     * Second comparison value besides totalPower: how much the given
     * {@link RoleBuff} helps this team. Every hero WITH the required role
     * contributes {@link #ROLE_MATCH_WEIGHT} points; if they are also
     * explicitly listed in {@link RoleBuff#buffProfits()} (a catalog value on
     * the fortification, replacing the former global buffAffinities mapping),
     * an additional {@link #BUFF_PROFIT_BONUS_WEIGHT} is added on top. Heroes
     * WITHOUT the required role do NOT contribute to the score, even if they
     * happen to be listed in buffProfits (see {@link RoleBuff}: "+bonusPercent%
     * ... per hero WITH matching role" - buffProfits is a bonus on top of
     * this match, not a substitute for it).
     */
    public int buffFitScore(Buff buff) {
        if (!(buff instanceof RoleBuff roleBuff)) {
            throw new IllegalArgumentException("HeroTeam.buffFitScore expects a RoleBuff, was: " + buff);
        }
        int score = 0;
        for (Hero h : heroes) {
            if (h.roles().contains(roleBuff.role())) {
                score += ROLE_MATCH_WEIGHT;
                if (roleBuff.buffProfits().contains(h.id())) {
                    score += BUFF_PROFIT_BONUS_WEIGHT;
                }
            }
        }
        return score;
    }
}
