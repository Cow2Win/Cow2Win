package org.c2w.data.model;

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
 * fortMarks: the fortifications this pet is marked as a good fit for
 * ({@link FortMark#POSITIVE} only - pets carry no negative marks). Kept in
 * {@code petCowScore.json}, deliberately SEPARATE from this record's
 * "objective" fields (id/imagePath), the same split heroes have.
 */
public record Pet(
        String id,
        String imagePath,
        FortMarks fortMarks
) {
    /** Avatar for pets that don't have their own icon under images/pets yet. */
    public static final String PLACEHOLDER_IMAGE_PATH = "/images/pets/placeholder.png";

    public Pet {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pet needs an id");
        }
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        fortMarks = fortMarks == null ? FortMarks.NONE : fortMarks;
    }

    /** Convenience constructor for pets without an avatar and without fortification marks (e.g. in tests). */
    public Pet(String id) {
        this(id, null, null);
    }

    /** True if this pet is marked as a good fit for the given fortification. */
    public boolean isMarkedFor(String fortificationId) {
        return fortMarks.isPositive(fortificationId);
    }
}
