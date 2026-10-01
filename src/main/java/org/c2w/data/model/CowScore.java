package org.c2w.data.model;

import java.util.Map;

/**
 * A titan's two editable {@link CowScoreTier}-based assessments -
 * {@link #generalScore()} and {@link #buffFitScores()} - kept separate from
 * its objective master data (id/element/image, see {@link Titan}) and
 * persisted in {@code titanCowScore.json} (see {@code CowScoreFiles}).
 * Heroes, pets and war flags use {@link FortMarks} instead.
 *
 * @param generalScore this entity's general quality/usefulness on {@link
 *         CowScoreTier}'s shared grid, independent of any specific
 *         fortification/buff ("some titans are simply better or worse than
 *         others") - used for fortifications WITHOUT a buff, where there is
 *         no role/element match to score against. {@link CowScoreTier#GOOD}
 *         is the default for an entity without an explicit, deliberate
 *         assessment.
 * @param buffFitScores this entity's buff-specific fit, keyed by {@link
 *         Fortification#id()}. Sparse by design - see {@link #buffFitScore(String,
 *         boolean)} for how a missing entry defaults. Used INSTEAD OF (not in
 *         addition to) {@link #generalScore()} for fortifications WITH a
 *         buff - see {@link TitanTeamBuffFitScore}.
 */
public record CowScore(CowScoreTier generalScore, Map<String, CowScoreTier> buffFitScores) {

    /** The all-default CowScore for an entity without any explicit assessment: {@link CowScoreTier#GOOD} general score, no buff-fit overrides. */
    public static final CowScore DEFAULT = new CowScore(null, null);

    public CowScore {
        generalScore = generalScore == null ? CowScoreTier.GOOD : generalScore;
        buffFitScores = buffFitScores == null ? Map.of() : Map.copyOf(buffFitScores);
    }

    /**
     * This entity's buff-specific fit score for the fortification with the
     * given id (which must have a buff). Used
     * INSTEAD OF {@link #generalScore()} for fortifications WITH a buff (see
     * {@link TitanTeamBuffFitScore#of(TitanTeam, Fortification)}, which is the
     * intended caller and resolves {@code roleOrElementMatches} against the
     * fortification's {@link ElementBuff#element()}).
     *
     * Resolution order: (1) an explicit override in {@link #buffFitScores()}
     * for this fortification id, if present; (2) otherwise
     * {@link CowScoreTier#GOOD} if {@code roleOrElementMatches}; (3)
     * otherwise {@link CowScoreTier#AVERAGE} - a role/element match is only the
     * DEFAULT floor, not a hard one: an explicit override for a
     * role/element-matching entity may still be set below STANDARD.
     */
    public CowScoreTier buffFitScore(String fortificationId, boolean roleOrElementMatches) {
        CowScoreTier override = buffFitScores.get(fortificationId);
        if (override != null) {
            return override;
        }
        return roleOrElementMatches ? CowScoreTier.GOOD : CowScoreTier.AVERAGE;
    }

    /**
     * The buff-specific fit score for an entity WITHOUT role/element - pets
     * and war flags: an explicit override in {@link #buffFitScores()} for
     * this fortification id if present, otherwise {@link #generalScore()}
     * - unlike {@link
     * #buffFitScore(String, boolean)}, there is no role/element match that
     * could pick a default here.
     */
    public CowScoreTier buffFitScoreOrGeneral(String fortificationId) {
        return buffFitScores.getOrDefault(fortificationId, generalScore);
    }

    /**
     * True if this is exactly the all-default CowScore ({@link
     * CowScoreTier#GOOD} general score, no buff-fit overrides at all) - i.e.
     * there is no deliberate assessment in it.
     */
    public boolean isDefault() {
        return generalScore == CowScoreTier.GOOD && buffFitScores.isEmpty();
    }
}
