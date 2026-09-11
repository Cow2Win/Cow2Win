package org.c2w.data.model;

import java.util.List;
import java.util.Map;

/**
 * Catalog entry of a hero: pure master data that is identical for all guild
 * members (role(s), avatar). Deliberately WITHOUT a strength/power field -
 * that differs per player and, for other guilds' teams, is known only as an
 * aggregated team total anyway (see HeroTeam.totalPower()).
 *
 * Used to also have a buffAffinities field (a global assessment per
 * BuffEffect of how much this hero profits from it) - that was removed in
 * favor of a per-buff buffProfits list on the respective Fortification buff,
 * which was itself later removed again (2026-09-11, see
 * cow2win-verbesserungsvorschlaege.md) in favor of the two, more precisely
 * scoped fields below: {@link #generalScore()} (for buff-less fortifications)
 * and {@link #buffFitScores()} (its buff-specific replacement for
 * buffProfits, Stufe 3).
 *
 * displayName is no longer stored in this class - that information now lives
 * in the properties files (deutsch.txt, english.txt, francais.txt). The
 * displayName can be retrieved at runtime via the LanguageService.
 *
 * imagePath: classpath-absolute path (with leading "/") to this hero's avatar
 * icon, e.g. "/images/heroes/Astaroth.png" - suitable for
 * {@code Hero.class.getResourceAsStream(imagePath)}. If an avatar is missing
 * (no icon export available yet), it automatically falls back to the
 * placeholder at {@link #PLACEHOLDER_IMAGE_PATH}, so the field is never null.
 *
 * generalScore: this hero's general quality/usefulness on {@link ScoreTier}'s
 * shared grid, independent of any specific fortification or buff ("some
 * heroes are simply better or worse than others") - used for fortifications
 * WITHOUT a buff, where there is no role/element match to score against.
 * {@link ScoreTier#STANDARD} is the default for a hero without an explicit,
 * deliberate assessment - most heroes are expected to stay at the default;
 * only deliberately better/worse heroes need an explicit entry in
 * heroes.json, keeping the catalog sparse.
 *
 * buffFitScores: this hero's buff-specific fit, keyed by
 * {@link Fortification#id()} - the buffProfits successor (Stufe 3, added
 * 2026-09-11, see cow2win-verbesserungsvorschlaege.md). An n:m relationship
 * (one hero can have an override for several fortifications, one
 * fortification can have overrides from several heroes), deliberately kept
 * on the HERO side (not on Fortification, unlike the old buffProfits) since
 * upkeep here is hero-centric. Sparse by design - see
 * {@link #buffFitScore(String, boolean)} for how a missing entry defaults.
 * Used INSTEAD OF (not in addition to) {@link #generalScore()} for
 * fortifications WITH a buff - see {@link HeroTeamBuffFitScore}.
 */
public record Hero(
        String id,
        List<Role> roles,
        String imagePath,
        ScoreTier generalScore,
        Map<String, ScoreTier> buffFitScores
) {
    /** Avatar for heroes that don't have their own icon under images/heroes yet. */
    public static final String PLACEHOLDER_IMAGE_PATH = "/images/heroes/placeholder.png";

    public Hero {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Hero needs an id");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Hero '" + id + "' needs at least one role");
        }
        roles = List.copyOf(roles);
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        generalScore = generalScore == null ? ScoreTier.STANDARD : generalScore;
        buffFitScores = buffFitScores == null ? Map.of() : Map.copyOf(buffFitScores);
    }

    /** Convenience constructor for heroes without an avatar and without an explicit general/buff-fit score (e.g. in tests). */
    public Hero(String id, List<Role> roles) {
        this(id, roles, null, null, null);
    }

    /**
     * This hero's buff-specific fit score for the fortification with the
     * given id (which must have a buff) - the buffProfits successor. Used
     * INSTEAD OF {@link #generalScore()} for fortifications WITH a buff (see
     * {@link HeroTeamBuffFitScore#of(HeroTeam, Fortification)}, which is the
     * intended caller and resolves {@code roleMatches} against the
     * fortification's {@link RoleBuff#role()}).
     *
     * Resolution order: (1) an explicit override in {@link #buffFitScores()}
     * for this fortification id, if present; (2) otherwise
     * {@link ScoreTier#STANDARD} if {@code roleMatches}; (3) otherwise
     * {@link ScoreTier#NORMAL} - a role match is only the DEFAULT floor, not
     * a hard one: an explicit override for a role-matching hero may still be
     * set below STANDARD (per the user's own decision, 2026-09-11).
     */
    public ScoreTier buffFitScore(String fortificationId, boolean roleMatches) {
        ScoreTier override = buffFitScores.get(fortificationId);
        if (override != null) {
            return override;
        }
        return roleMatches ? ScoreTier.STANDARD : ScoreTier.NORMAL;
    }
}
