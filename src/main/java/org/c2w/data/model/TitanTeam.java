package org.c2w.data.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A titan team fielded by a guild member. Per the user's feedback, team size
 * does not differ from a hero team (i.e. also up to 5 titans) - contrary to
 * older community sources, which state 1-4 titans. totalPower, as with
 * HeroTeam, is the team's total strength.
 *
 * lastModified: see {@link HeroTeam#lastModified()} - identical concept,
 * here for titan teams.
 */
public record TitanTeam(
        String memberId,
        List<Titan> titans,
        int totalPower,
        LocalDate lastModified
) {
    /** Base weight per titan whose element matches the ElementBuff's element. */
    public static final int ELEMENT_MATCH_WEIGHT = 1;

    /** Additional bonus if the titan is also listed in {@link ElementBuff#buffProfits()}. */
    public static final int BUFF_PROFIT_BONUS_WEIGHT = 2;

    public TitanTeam {
        if (totalPower < 0) {
            throw new IllegalArgumentException("totalPower must not be negative");
        }
        titans = List.copyOf(titans);
    }

    /** Convenience constructor for titan teams without lastModified. */
    public TitanTeam(String memberId, List<Titan> titans, int totalPower) {
        this(memberId, titans, totalPower, null);
    }

    /** Convenience constructor for titan teams without lastModified. */
    public TitanTeam() {
        this(null, null, 0, null);
    }
    /**
     * Second comparison value besides totalPower: how much the given
     * {@link ElementBuff} helps this team. Every titan WITH the required
     * element contributes {@link #ELEMENT_MATCH_WEIGHT} points; if it is also
     * explicitly listed in {@link ElementBuff#buffProfits()} (a catalog value
     * on the fortification, replacing the former global buffAffinities
     * mapping), an additional {@link #BUFF_PROFIT_BONUS_WEIGHT} is added on
     * top. Titans with a different element do NOT contribute to the score,
     * even if they happen to be listed in buffProfits (see {@link ElementBuff}:
     * "+bonusPercent% ... per titan WITH matching element" - buffProfits is a
     * bonus on top of this match, not a substitute for it).
     */
    public int buffFitScore(Buff buff) {
        if (!(buff instanceof ElementBuff elementBuff)) {
            throw new IllegalArgumentException("TitanTeam.buffFitScore expects an ElementBuff, was: " + buff);
        }
        int score = 0;
        for (Titan t : titans) {
            if (t.element() == elementBuff.element()) {
                score += ELEMENT_MATCH_WEIGHT;
                if (elementBuff.buffProfits().contains(t.id())) {
                    score += BUFF_PROFIT_BONUS_WEIGHT;
                }
            }
        }
        return score;
    }
}
