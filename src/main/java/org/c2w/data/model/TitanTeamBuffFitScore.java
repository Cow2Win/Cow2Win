package org.c2w.data.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The TITAN-side counterpart of {@link HeroTeamBuffFitScore} - see there for
 * the full reasoning (identical here, element match instead of role match).
 */
public record TitanTeamBuffFitScore(
        TitanTeam team,
        Fortification fortification,
        Map<String, CowScoreTier> memberScores,
        double total
) {
    public TitanTeamBuffFitScore {
        if (team == null) {
            throw new IllegalArgumentException("TitanTeamBuffFitScore needs a team");
        }
        if (fortification == null) {
            throw new IllegalArgumentException("TitanTeamBuffFitScore needs a fortification");
        }
        memberScores = Map.copyOf(memberScores);
    }

    /**
     * Computes the breakdown for {@code team} at {@code fortification}
     * (which must have an {@link ElementBuff}): every titan's
     * {@link Titan#buffFitScore(String, boolean)} for this fortification's
     * id - element match determined against {@link ElementBuff#element()} -
     * keyed by {@link Titan#id()}, summed up as {@link #total()}.
     */
    public static TitanTeamBuffFitScore of(TitanTeam team, Fortification fortification) {
        if (!(fortification.buff() instanceof ElementBuff elementBuff)) {
            throw new IllegalArgumentException(
                    "TitanTeamBuffFitScore.of expects a fortification with an ElementBuff, was: " + fortification.buff());
        }
        Map<String, CowScoreTier> scores = new LinkedHashMap<>();
        double total = 0;
        for (Titan titan : team.titans()) {
            boolean elementMatches = titan.element() == elementBuff.element();
            CowScoreTier tier = titan.buffFitScore(fortification.id(), elementMatches);
            scores.put(titan.id(), tier);
            total += tier.value();
        }
        return new TitanTeamBuffFitScore(team, fortification, scores, total);
    }
}
