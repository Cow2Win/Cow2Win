package org.c2w.data.model;

import java.util.List;

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
 * roles: this titan's {@link TitanRole}s (1-n in the shipped data), in file order,
 * never null - empty if titans.json lists none. superTitan: whether this
 * titan is a super titan - deliberately a property of its own, not a
 * {@link TitanRole}. Both are "objective" master data from {@code titans.json}.
 *
 * fortMarks: this titan's manually curated {@link FortMarks} (positive /
 * negative relation to individual TITAN fortifications), kept in {@code
 * titanCowScore.json}, deliberately SEPARATE from this record's
 * "objective" fields (id/element/roles/superTitan/imagePath) in {@code titans.json} (see
 * {@code TitanRepository}'s class Javadoc): a master-data refresh can
 * overwrite {@code titans.json} wholesale without risking the manually
 * maintained marks - the same split as {@link Hero#fortMarks()}.
 */
public record Titan(
        String id,
        TitanElement element,
        List<TitanRole> roles,
        boolean superTitan,
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
        roles = roles == null ? List.of() : List.copyOf(roles);
        imagePath = (imagePath == null || imagePath.isBlank()) ? PLACEHOLDER_IMAGE_PATH : imagePath;
        fortMarks = fortMarks == null ? FortMarks.NONE : fortMarks;
    }

    /** Convenience constructor for titans without roles, avatar and fortification marks and not a super titan (e.g. in tests). */
    public Titan(String id, TitanElement element) {
        this(id, element, List.of(), false, null, null);
    }

    /** Convenience constructor for titans without an avatar and without fortification marks (e.g. in tests). */
    public Titan(String id, TitanElement element, List<TitanRole> roles, boolean superTitan) {
        this(id, element, roles, superTitan, null, null);
    }

    /** A copy of this titan with {@code fortMarks} instead of its current marks - everything else (roles, super titan, ...) is kept. */
    public Titan withFortMarks(FortMarks fortMarks) {
        return new Titan(id, element, roles, superTitan, imagePath, fortMarks);
    }

    /** True if {@code role} is one of this titan's roles. */
    public boolean hasRole(TitanRole role) {
        return roles.contains(role);
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
