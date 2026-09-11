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
     * element contributes {@link #ELEMENT_MATCH_WEIGHT} points. Titans with a
     * different element do NOT contribute to the score (see
     * {@link ElementBuff}: "+bonusPercent% ... per titan WITH matching
     * element").
     */
    public int buffFitScore(Buff buff) {
        if (!(buff instanceof ElementBuff elementBuff)) {
            throw new IllegalArgumentException("TitanTeam.buffFitScore expects an ElementBuff, was: " + buff);
        }
        int score = 0;
        for (Titan t : titans) {
            if (t.element() == elementBuff.element()) {
                score += ELEMENT_MATCH_WEIGHT;
            }
        }
        return score;
    }

    /** See {@link HeroTeam#SORT_SCORE_POWER_DIVISOR} - identical reasoning, here for titan teams. */
    private static final double SORT_SCORE_POWER_DIVISOR = 100_000.0;

    /**
     * The TITAN-side counterpart of {@link HeroTeam#sortScore()}, used the
     * same way to pick a team for a fortification without a buff: the sum of
     * every titan's {@link Titan#generalScore()} plus {@link #totalPower()}
     * scaled down via {@link #SORT_SCORE_POWER_DIVISOR} - same formula, same
     * treatment as the hero side (per the user's explicit request, 2026-09-11,
     * see cow2win-verbesserungsvorschlaege.md: titans get "die gleiche
     * Behandlung" as heroes here), now that {@link Titan#generalScore()}
     * exists.
     */
    public double sortScore() {
        double generalScoreSum = titans.stream().mapToDouble(t -> t.generalScore().value()).sum();
        return generalScoreSum + totalPower() / SORT_SCORE_POWER_DIVISOR;
    }
}
