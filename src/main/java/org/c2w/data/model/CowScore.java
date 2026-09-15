package org.c2w.data.model;

import java.util.Map;

/**
 * The umbrella term (Thorsten's own naming, 2026-09-14) for a hero's (or,
 * later, titan's) two editable {@link ScoreTier}-based assessments -
 * {@link #generalScore()} and {@link #buffFitScores()} - as opposed to a
 * catalog entry's own objective master data (id/roles/image, see
 * {@link Hero}). Bundling both into one type is what makes it possible to
 * hold "the CowScore" for a hero as a single value and persist it in a file
 * of its own, separate from that hero's master data - see
 * {@code HeroRepository}'s class Javadoc for why that separation exists
 * ({@code cowScore.json} vs. {@code heroes.json}).
 *
 * <p>Currently used by {@link Hero} only ({@link Hero#cowScore()}); {@link
 * Titan} still keeps its own, identical pair of fields directly (its master
 * data and scores are not split across two files the way heroes.json/
 * cowScore.json are) - nothing about this type is hero-specific, though, so
 * {@link Titan} could adopt it later without changing the underlying
 * {@link ScoreTier} grid or resolution logic.
 *
 * @param generalScore this entity's general quality/usefulness on {@link
 *         ScoreTier}'s shared grid, independent of any specific
 *         fortification/buff ("some heroes are simply better or worse than
 *         others") - used for fortifications WITHOUT a buff, where there is
 *         no role/element match to score against. {@link ScoreTier#STANDARD}
 *         is the default for an entity without an explicit, deliberate
 *         assessment - most heroes are expected to stay at the default; only
 *         deliberately better/worse heroes need an explicit entry in
 *         cowScore.json, keeping that file sparse.
 * @param buffFitScores this entity's buff-specific fit, keyed by {@link
 *         Fortification#id()} - the buffProfits successor (Stufe 3, added
 *         2026-09-11, see cow2win-verbesserungsvorschlaege.md). An n:m
 *         relationship (one hero can have an override for several
 *         fortifications, one fortification can have overrides from several
 *         heroes). Sparse by design - see {@link #buffFitScore(String,
 *         boolean)} for how a missing entry defaults. Used INSTEAD OF (not in
 *         addition to) {@link #generalScore()} for fortifications WITH a
 *         buff - see {@link HeroTeamBuffFitScore}.
 */
public record CowScore(ScoreTier generalScore, Map<String, ScoreTier> buffFitScores) {

    /** The all-default CowScore for an entity without any explicit assessment: {@link ScoreTier#STANDARD} general score, no buff-fit overrides. */
    public static final CowScore DEFAULT = new CowScore(null, null);

    public CowScore {
        generalScore = generalScore == null ? ScoreTier.STANDARD : generalScore;
        buffFitScores = buffFitScores == null ? Map.of() : Map.copyOf(buffFitScores);
    }

    /**
     * This entity's buff-specific fit score for the fortification with the
     * given id (which must have a buff) - the buffProfits successor. Used
     * INSTEAD OF {@link #generalScore()} for fortifications WITH a buff (see
     * {@link HeroTeamBuffFitScore#of(HeroTeam, Fortification)}, which is the
     * intended caller and resolves {@code roleMatches} against the
     * fortification's {@link RoleBuff#role()}).
     *
     * Resolution order: (1) an explicit override in {@link #buffFitScores()}
     * for this fortification id, if present; (2) otherwise
     * {@link ScoreTier#STANDARD} if {@code roleOrElementMatches}; (3)
     * otherwise {@link ScoreTier#NORMAL} - a role/element match is only the
     * DEFAULT floor, not a hard one: an explicit override for a
     * role/element-matching entity may still be set below STANDARD (per the
     * user's own decision, 2026-09-11).
     */
    public ScoreTier buffFitScore(String fortificationId, boolean roleOrElementMatches) {
        ScoreTier override = buffFitScores.get(fortificationId);
        if (override != null) {
            return override;
        }
        return roleOrElementMatches ? ScoreTier.STANDARD : ScoreTier.NORMAL;
    }

    /**
     * True if this is exactly the all-default CowScore ({@link
     * ScoreTier#STANDARD} general score, no buff-fit overrides at all) - i.e.
     * there is nothing here worth persisting explicitly. Used by {@code
     * HeroRepository#saveCowScores} to omit an entry from cowScore.json
     * entirely for a hero without any deliberate assessment, keeping that
     * file sparse.
     */
    public boolean isDefault() {
        return generalScore == ScoreTier.STANDARD && buffFitScores.isEmpty();
    }
}
