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
 * scoped fields bundled in {@link #cowScore()} below: {@link
 * CowScore#generalScore()} (for buff-less fortifications) and {@link
 * CowScore#buffFitScores()} (its buff-specific replacement for buffProfits,
 * Stufe 3).
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
 * cowScore: this hero's manually curated {@link CowScore} - see that type's
 * Javadoc for what it bundles and why it is its own type. Kept in {@code
 * cowScore.json}, deliberately SEPARATE from this record's other,
 * "objective" fields (id/roles/imagePath), which live in {@code heroes.json}
 * (2026-09-14, Trennung von heroes.json und den editierbaren Score-Werten -
 * see {@code HeroRepository}'s class Javadoc): that separation lets a future
 * master-data refresh (new/updated heroes from Hero Wars) overwrite {@code
 * heroes.json} wholesale without risking the manually maintained scores in
 * {@code cowScore.json}. {@link #generalScore()}/{@link #buffFitScores()}/
 * {@link #buffFitScore(String, boolean)} remain as convenience delegates so
 * every other caller keeps working against this record exactly as before.
 */
public record Hero(
        String id,
        List<Role> roles,
        String imagePath,
        CowScore cowScore
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
        cowScore = cowScore == null ? CowScore.DEFAULT : cowScore;
    }

    /** Convenience constructor for heroes without an avatar and without an explicit CowScore (e.g. in tests). */
    public Hero(String id, List<Role> roles) {
        this(id, roles, null, null);
    }

    /** This hero's general quality/usefulness - delegates to {@link #cowScore()}, see {@link CowScore#generalScore()}. */
    public ScoreTier generalScore() {
        return cowScore.generalScore();
    }

    /** This hero's buff-specific fit overrides - delegates to {@link #cowScore()}, see {@link CowScore#buffFitScores()}. */
    public Map<String, ScoreTier> buffFitScores() {
        return cowScore.buffFitScores();
    }

    /**
     * This hero's buff-specific fit score for the fortification with the
     * given id (which must have a buff) - delegates to {@link #cowScore()},
     * see {@link CowScore#buffFitScore(String, boolean)} for the resolution
     * order. Intended caller: {@link HeroTeamBuffFitScore#of(HeroTeam,
     * Fortification)}, which resolves {@code roleMatches} against the
     * fortification's {@link RoleBuff#role()}.
     */
    public ScoreTier buffFitScore(String fortificationId, boolean roleMatches) {
        return cowScore.buffFitScore(fortificationId, roleMatches);
    }
}
