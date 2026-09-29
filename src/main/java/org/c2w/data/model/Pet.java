package org.c2w.data.model;

import java.util.Map;

/**
 * Catalog entry of a pet: master data, identical for all guild members.
 * Analogous to {@link Hero} and {@link Titan}, but pets have neither roles
 * nor an element - their only "objective" data are id and avatar.
 *
 * displayName is not stored in this class - that information lives in the
 * properties files (deutsch/deutsch.properties, english/english.properties,
 * francais/francais.properties), keyed by {@link #id()}. The displayName can
 * be retrieved at runtime via the LanguageService.
 *
 * imagePath: classpath-absolute path (with leading "/") to this pet's avatar
 * icon, e.g. "/images/pets/Albus.png" - analogous to {@link Hero#imagePath()},
 * suitable for {@code Pet.class.getResourceAsStream(imagePath)}. If no image
 * is given (see PetRepository/pets.json - field "image"), it automatically
 * falls back to the placeholder at {@link #PLACEHOLDER_IMAGE_PATH}, so the
 * field is never null.
 *
 * cowScore: this pet's manually curated {@link CowScore} - same {@link
 * CowScoreTier} grid and defaults as {@link Hero#cowScore()}. Kept in {@code
 * petCowScore.json}, deliberately SEPARATE from this record's "objective"
 * fields (id/imagePath), which live in {@code pets.json} - the same split
 * heroes and titans have (see {@code PetRepository}'s class Javadoc).
 * {@link #generalScore()}/{@link #buffFitScores()} are convenience delegates.
 */
public record Pet(
        String id,
        String imagePath,
        CowScore cowScore
) {
    /** Avatar for pets that don't have their own icon under images/pets yet. */
    public static final String PLACEHOLDER_IMAGE_PATH = "/images/pets/placeholder.png";

    public Pet {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pet needs an id");
        }
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        cowScore = cowScore == null ? CowScore.DEFAULT : cowScore;
    }

    /** Convenience constructor for pets without an avatar and without an explicit CowScore (e.g. in tests). */
    public Pet(String id) {
        this(id, null, null);
    }

    /** This pet's general quality/usefulness - delegates to {@link #cowScore()}, see {@link CowScore#generalScore()}. */
    public CowScoreTier generalScore() {
        return cowScore.generalScore();
    }

    /** This pet's buff-specific fit overrides - delegates to {@link #cowScore()}, see {@link CowScore#buffFitScores()}. */
    public Map<String, CowScoreTier> buffFitScores() {
        return cowScore.buffFitScores();
    }

    /**
     * This pet's buff-specific fit score for the fortification with the given
     * id (which must have a buff) - delegates to {@link #cowScore()}, see
     * {@link CowScore#buffFitScore(String, boolean)} for the resolution order.
     * Pets have no role/element, so {@code buffMatches} is up to the caller
     * (typically {@code false}).
     */
    public CowScoreTier buffFitScore(String fortificationId, boolean buffMatches) {
        return cowScore.buffFitScore(fortificationId, buffMatches);
    }
}
