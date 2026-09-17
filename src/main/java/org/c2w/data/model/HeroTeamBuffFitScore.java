package org.c2w.data.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime-only "how well does this team fit THIS specific buffed
 * fortification" breakdown - the buff-specific counterpart of
 * {@link HeroTeam#sortScore()}, added 2026-09-11 as Stufe 3 of the
 * buffProfits replacement (see cow2win-verbesserungsvorschlaege.md).
 *
 * Deliberately NOT a persisted concept, unlike {@link Hero#buffFitScores()}:
 * this wraps a {@link HeroTeam} together with the per-member
 * {@link CowScoreTier} values (resolved via
 * {@link Hero#buffFitScore(String, boolean)}) for one candidate
 * {@link Fortification}, plus their sum as {@link #total()} - per the user's
 * stated intent, this is meant to be surfaced later as a table per team
 * across its possible fortifications (Stufe 4, not yet built), so a fresh
 * instance is computed on demand via {@link #of(HeroTeam, Fortification)}
 * rather than stored anywhere.
 *
 * total REPLACES {@link Hero#generalScore()} for this purpose entirely, it
 * is not added to it - generalScore is used only for buff-less
 * fortifications, via {@link HeroTeam#sortScore()}. The fortification's
 * actual in-game buff bonus (role-match count times bonusPercent) is a
 * separate, untouched mechanism - see {@link HeroTeam#buffFitScore(Buff)}.
 */
public record HeroTeamBuffFitScore(
        HeroTeam team,
        Fortification fortification,
        Map<String, CowScoreTier> memberScores,
        double total
) {
    public HeroTeamBuffFitScore {
        if (team == null) {
            throw new IllegalArgumentException("HeroTeamBuffFitScore needs a team");
        }
        if (fortification == null) {
            throw new IllegalArgumentException("HeroTeamBuffFitScore needs a fortification");
        }
        memberScores = Map.copyOf(memberScores);
    }

    /**
     * Computes the breakdown for {@code team} at {@code fortification}
     * (which must have a {@link RoleBuff}): every hero's
     * {@link Hero#buffFitScore(String, boolean)} for this fortification's
     * id - role match determined against {@link RoleBuff#role()} - keyed by
     * {@link Hero#id()}, summed up as {@link #total()}.
     */
    public static HeroTeamBuffFitScore of(HeroTeam team, Fortification fortification) {
        if (!(fortification.buff() instanceof RoleBuff roleBuff)) {
            throw new IllegalArgumentException(
                    "HeroTeamBuffFitScore.of expects a fortification with a RoleBuff, was: " + fortification.buff());
        }
        Map<String, CowScoreTier> scores = new LinkedHashMap<>();
        double total = 0;
        for (Hero hero : team.heroes()) {
            boolean roleMatches = hero.roles().contains(roleBuff.role());
            CowScoreTier tier = hero.buffFitScore(fortification.id(), roleMatches);
            scores.put(hero.id(), tier);
            total += tier.value();
        }
        return new HeroTeamBuffFitScore(team, fortification, scores, total);
    }
}
