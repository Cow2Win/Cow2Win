package org.c2w.data.model;

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
 * fortMarks: the fortifications this war flag is marked as a good fit for
 * ({@link FortMark#POSITIVE} only - war flags carry no negative marks). Kept in
 * {@code warFlagCowScore.json}, deliberately SEPARATE from this record's
 * "objective" fields (id/imagePath), the same split heroes have. Replaces
 * the former tier-based CowScore (CowScore concept of 2026-09-30).
 */
public record WarFlag(
        String id,
        String imagePath,
        FortMarks fortMarks
) {
    /** Icon for war flags that don't have their own icon under images/flags yet. */
    public static final String PLACEHOLDER_IMAGE_PATH = "/images/flags/placeholder.png";

    public WarFlag {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WarFlag needs an id");
        }
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        fortMarks = fortMarks == null ? FortMarks.NONE : fortMarks;
    }

    /** Convenience constructor for war flags without an icon and without fortification marks (e.g. in tests). */
    public WarFlag(String id) {
        this(id, null, null);
    }

    /** True if this war flag is marked as a good fit for the given fortification. */
    public boolean isMarkedFor(String fortificationId) {
        return fortMarks.isPositive(fortificationId);
    }
}
