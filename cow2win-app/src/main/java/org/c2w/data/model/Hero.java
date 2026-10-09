package org.c2w.data.model;

import java.util.List;

/**
 * Catalog entry of a hero: pure master data that is identical for all guild
 * members (role(s), avatar). Deliberately WITHOUT a strength/power field -
 * that differs per player and, for other guilds' teams, is known only as an
 * aggregated team total anyway (see HeroTeam.totalPower()).
 *
 * The hero's manually curated assessment is a set of per-fortification
 * {@link FortMark}s, see {@link #fortMarks()}.
 *
 * displayName is no longer stored in this class - that information now lives
 * in the properties files (deutsch/deutsch.properties, english/english.properties, francais/francais.properties). The
 * displayName can be retrieved at runtime via the LanguageService.
 *
 * imagePath: classpath-absolute path (with leading "/") to this hero's avatar
 * icon, e.g. "/images/heroes/Astaroth.png" - suitable for
 * {@code Hero.class.getResourceAsStream(imagePath)}. If an avatar is missing
 * (no icon export available yet), it automatically falls back to the
 * placeholder at {@link #PLACEHOLDER_IMAGE_PATH}, so the field is never null.
 *
 * fortMarks: this hero's manually curated {@link FortMarks} (positive /
 * negative relation to individual fortifications). Kept in {@code
 * cowScore.json}, deliberately SEPARATE from this record's other,
 * "objective" fields (id/roles/imagePath), which live in {@code heroes.json}
 * (see {@code HeroRepository}'s class Javadoc): that separation lets a future
 * master-data refresh overwrite {@code heroes.json} wholesale without risking
 * the manually maintained marks.
 */
public record Hero(
        String id,
        List<Role> roles,
        String imagePath,
        FortMarks fortMarks
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
        fortMarks = fortMarks == null ? FortMarks.NONE : fortMarks;
    }

    /** Convenience constructor for heroes without an avatar and without fortification marks (e.g. in tests). */
    public Hero(String id, List<Role> roles) {
        this(id, roles, null, null);
    }

    /** True if one of this hero's roles matches the given {@link Buff} - the automatic BUFF mark (only {@link RoleBuff}s can match). */
    public boolean matchesBuff(Buff buff) {
        return buff instanceof RoleBuff roleBuff && roles.contains(roleBuff.role());
    }

    /** This hero's mark for the given fortification, or null if unmarked - see {@link FortMarks#markFor(String)}. */
    public FortMark fortMark(String fortificationId) {
        return fortMarks.markFor(fortificationId);
    }
}
