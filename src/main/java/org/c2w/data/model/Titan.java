package org.c2w.data.model;

import java.util.Map;

/**
 * Catalog entry of a titan: master data, identical for all guild members.
 *
 * Used to also have a buffAffinities field, analogous to {@link Hero} - that
 * was removed in favor of a per-buff buffProfits list on the respective
 * Fortification buff, which was itself later removed again (2026-09-11, see
 * cow2win-verbesserungsvorschlaege.md) in favor of {@link #generalScore()}
 * and {@link #buffFitScores()} below - see the Javadoc on {@link Hero} for
 * the full reasoning, identical here.
 *
 * displayName is no longer stored in this class - that information now lives
 * in the properties files (deutsch.txt, english.txt, francais.txt). The
 * displayName can be retrieved at runtime via the LanguageService.
 *
 * imagePath: classpath-absolute path (with leading "/") to this titan's
 * avatar icon, e.g. "/images/titans/Ignis.png" - analogous to
 * {@link Hero#imagePath()}, suitable for
 * {@code Titan.class.getResourceAsStream(imagePath)}. If an image under
 * images/titans is missing (see TitanRepository/titans.json - field
 * "image"), it automatically falls back to the placeholder at
 * {@link #PLACEHOLDER_IMAGE_PATH}, so the field is never null.
 *
 * generalScore: the TITAN-side counterpart of {@link Hero#generalScore()} -
 * same shared {@link ScoreTier} grid, same default ({@link
 * ScoreTier#STANDARD}) and same sparse-catalog intent (added 2026-09-11, see
 * cow2win-verbesserungsvorschlaege.md, "gleiche Behandlung" as heroes per
 * the user's own words). Used by {@link TitanTeam#sortScore()} for
 * fortifications without a buff, exactly as {@link Hero#generalScore()} is
 * used by {@link HeroTeam#sortScore()}.
 *
 * buffFitScores: the TITAN-side counterpart of {@link Hero#buffFitScores()} -
 * same idea (buffProfits successor, keyed by {@link Fortification#id()},
 * n:m, sparse), same default resolution (see
 * {@link #buffFitScore(String, boolean)}), also added 2026-09-11 for the
 * same "gleiche Behandlung" reason as generalScore above. Used INSTEAD OF
 * generalScore for fortifications WITH a buff - see
 * {@link TitanTeamBuffFitScore}.
 */
public record Titan(
        String id,
        TitanElement element,
        String imagePath,
        ScoreTier generalScore,
        Map<String, ScoreTier> buffFitScores
) {
    /** Avatar for titans that don't have their own icon under images/titans yet. */
    public static final String PLACEHOLDER_IMAGE_PATH = "/images/titans/placeholder.png";

    public Titan {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Titan needs an id");
        }
        if (element == null) {
            throw new IllegalArgumentException("Titan '" + id + "' needs an element");
        }
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        generalScore = generalScore == null ? ScoreTier.STANDARD : generalScore;
        buffFitScores = buffFitScores == null ? Map.of() : Map.copyOf(buffFitScores);
    }

    /** Convenience constructor for titans without an avatar and without an explicit general/buff-fit score (e.g. in tests). */
    public Titan(String id, TitanElement element) {
        this(id, element, null, null, null);
    }

    /**
     * This titan's buff-specific fit score for the fortification with the
     * given id (which must have a buff) - see {@link
     * Hero#buffFitScore(String, boolean)} for the identical resolution
     * order/reasoning (element match instead of role match here). Intended
     * caller: {@link TitanTeamBuffFitScore#of(TitanTeam, Fortification)},
     * which resolves {@code elementMatches} against the fortification's
     * {@link ElementBuff#element()}.
     */
    public ScoreTier buffFitScore(String fortificationId, boolean elementMatches) {
        ScoreTier override = buffFitScores.get(fortificationId);
        if (override != null) {
            return override;
        }
        return elementMatches ? ScoreTier.STANDARD : ScoreTier.NORMAL;
    }
}
