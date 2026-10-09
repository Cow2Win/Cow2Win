package org.c2w.data.model;

/**
 * Catalog entry of a titan: master data, identical for all guild members.
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
 * fortMarks: this titan's manually curated {@link FortMarks} (positive /
 * negative relation to individual TITAN fortifications), kept in {@code
 * titanCowScore.json}, deliberately SEPARATE from this record's
 * "objective" fields (id/element/imagePath) in {@code titans.json} (see
 * {@code TitanRepository}'s class Javadoc): a master-data refresh can
 * overwrite {@code titans.json} wholesale without risking the manually
 * maintained marks - the same split as {@link Hero#fortMarks()}.
 */
public record Titan(
        String id,
        TitanElement element,
        String imagePath,
        FortMarks fortMarks
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
        fortMarks = fortMarks == null ? FortMarks.NONE : fortMarks;
    }

    /** Convenience constructor for titans without an avatar and without fortification marks (e.g. in tests). */
    public Titan(String id, TitanElement element) {
        this(id, element, null, null);
    }

    /** True if this titan's element matches the given {@link Buff} - the automatic BUFF mark (only {@link ElementBuff}s can match). */
    public boolean matchesBuff(Buff buff) {
        return buff instanceof ElementBuff elementBuff && element == elementBuff.element();
    }

    /** This titan's mark for the given fortification, or null if unmarked - see {@link FortMarks#markFor(String)}. */
    public FortMark fortMark(String fortificationId) {
        return fortMarks.markFor(fortificationId);
    }
}
