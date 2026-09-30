package org.c2w.data.model;

import java.util.Map;

/**
 * Catalog entry of a war flag: master data, identical for all guild members.
 * Analogous to {@link Pet} - war flags have neither roles nor an element,
 * their only "objective" data are id and icon.
 *
 * displayName is not stored in this class - that information lives in the
 * properties files (deutsch/deutsch.properties, english/english.properties,
 * francais/francais.properties), keyed by {@link #id()}. The displayName can
 * be retrieved at runtime via the LanguageService. War flag ids carry a
 * {@code "flag-"} prefix (e.g. {@code "flag-bastion"}), because the language
 * files share one flat key namespace with heroes, titans, pets and
 * fortifications - and some flag names (e.g. "Bastion") already exist there.
 *
 * imagePath: classpath-absolute path (with leading "/") to this flag's icon,
 * e.g. "/images/flags/Bastion.png" - analogous to {@link Pet#imagePath()},
 * suitable for {@code WarFlag.class.getResourceAsStream(imagePath)}. If no
 * image is given (see WarFlagRepository/warFlags.json - field "image"), it
 * automatically falls back to the placeholder at {@link
 * #PLACEHOLDER_IMAGE_PATH}, so the field is never null.
 *
 * cowScore: this flag's manually curated {@link CowScore} - same {@link
 * CowScoreTier} grid and defaults as {@link Pet#cowScore()}. Kept in {@code
 * warFlagCowScore.json}, deliberately SEPARATE from this record's
 * "objective" fields (id/imagePath), which live in {@code warFlags.json} -
 * the same split heroes, titans and pets have (see {@code
 * WarFlagRepository}'s class Javadoc). {@link #generalScore()}/{@link
 * #buffFitScores()} are convenience delegates.
 */
public record WarFlag(
        String id,
        String imagePath,
        CowScore cowScore
) {
    /** Icon for war flags that don't have their own icon under images/flags yet. */
    public static final String PLACEHOLDER_IMAGE_PATH = "/images/flags/placeholder.png";

    public WarFlag {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WarFlag needs an id");
        }
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        cowScore = cowScore == null ? CowScore.DEFAULT : cowScore;
    }

    /** Convenience constructor for war flags without an icon and without an explicit CowScore (e.g. in tests). */
    public WarFlag(String id) {
        this(id, null, null);
    }

    /** This flag's general quality/usefulness - delegates to {@link #cowScore()}, see {@link CowScore#generalScore()}. */
    public CowScoreTier generalScore() {
        return cowScore.generalScore();
    }

    /** This flag's buff-specific fit overrides - delegates to {@link #cowScore()}, see {@link CowScore#buffFitScores()}. */
    public Map<String, CowScoreTier> buffFitScores() {
        return cowScore.buffFitScores();
    }

    /**
     * This war flag's fit score for the fortification with the given id (which
     * must have a buff) - its explicit override for that fortification if
     * present, otherwise its {@link #generalScore()}: war flags have no
     * role/element, so there is no match to default on - see {@link
     * CowScore#buffFitScoreOrGeneral(String)}.
     */
    public CowScoreTier buffFitScore(String fortificationId) {
        return cowScore.buffFitScoreOrGeneral(fortificationId);
    }
}
