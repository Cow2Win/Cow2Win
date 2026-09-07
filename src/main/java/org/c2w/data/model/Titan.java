package org.c2w.data.model;

/**
 * Catalog entry of a titan: master data, identical for all guild members.
 *
 * Used to also have a buffAffinities field, analogous to {@link Hero} - that
 * was removed, see the Javadoc there for the reasoning. That information now
 * lives locally on the respective buff of a Fortification (see
 * {@link ElementBuff#buffProfits()}).
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
 */
public record Titan(
        String id,
        TitanElement element,
        String imagePath
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
    }

    /** Convenience constructor for titans without an avatar (e.g. in tests). */
    public Titan(String id, TitanElement element) {
        this(id, element, null);
    }
}
