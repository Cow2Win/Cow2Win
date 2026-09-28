package org.c2w.data.model;

import java.util.Map;

/**
 * Catalog entry of a titan: master data, identical for all guild members.
 *
 * Used to also have a buffAffinities field, analogous to {@link Hero} - that
 * was removed in favor of a per-buff buffProfits list on the respective
 * Fortification buff, which was itself later removed again (2026-09-11) in
 * favor of the two scores now bundled in {@link #cowScore()} - see the
 * Javadoc on {@link Hero} for the full reasoning, identical here.
 *
 * displayName is no longer stored in this class - that information now lives
 * in the properties files (deutsch/deutsch.properties, english/english.properties, francais/francais.properties). The
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
 * cowScore: this titan's manually curated {@link CowScore} - the TITAN-side
 * counterpart of {@link Hero#cowScore()}, same {@link CowScoreTier} grid,
 * same defaults. Kept in {@code titanCowScore.json}, deliberately SEPARATE
 * from this record's "objective" fields (id/element/imagePath), which live
 * in {@code titans.json} (2026-09-28, same split the heroes got on
 * 2026-09-14 - see {@code TitanRepository}'s class Javadoc): a future
 * master-data refresh can overwrite {@code titans.json} wholesale without
 * risking the manually maintained scores. {@link #generalScore()}/{@link
 * #buffFitScores()}/{@link #buffFitScore(String, boolean)} remain as
 * convenience delegates so every other caller keeps working unchanged.
 */
public record Titan(
        String id,
        TitanElement element,
        String imagePath,
        CowScore cowScore
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
        cowScore = cowScore == null ? CowScore.DEFAULT : cowScore;
    }

    /** Convenience constructor for titans without an avatar and without an explicit CowScore (e.g. in tests). */
    public Titan(String id, TitanElement element) {
        this(id, element, null, null);
    }

    /**
     * This titan's general quality/usefulness - delegates to {@link #cowScore()},
     * see {@link CowScore#generalScore()}. Used by {@link TitanTeam#sortScore()}
     * for fortifications without a buff, exactly as {@link Hero#generalScore()}
     * is used by {@link HeroTeam#sortScore()}.
     */
    public CowScoreTier generalScore() {
        return cowScore.generalScore();
    }

    /** This titan's buff-specific fit overrides - delegates to {@link #cowScore()}, see {@link CowScore#buffFitScores()}. */
    public Map<String, CowScoreTier> buffFitScores() {
        return cowScore.buffFitScores();
    }

    /**
     * This titan's buff-specific fit score for the fortification with the
     * given id (which must have a buff) - delegates to {@link #cowScore()},
     * see {@link CowScore#buffFitScore(String, boolean)} for the resolution
     * order (element match instead of role match here). Intended caller:
     * {@link TitanTeamBuffFitScore#of(TitanTeam, Fortification)}, which
     * resolves {@code elementMatches} against the fortification's
     * {@link ElementBuff#element()}.
     */
    public CowScoreTier buffFitScore(String fortificationId, boolean elementMatches) {
        return cowScore.buffFitScore(fortificationId, elementMatches);
    }
}
