package org.c2w.data.model;

import java.util.List;

/**
 * Catalog entry of a hero: pure master data that is identical for all guild
 * members (role(s), avatar). Deliberately WITHOUT a strength/power field -
 * that differs per player and, for other guilds' teams, is known only as an
 * aggregated team total anyway (see HeroTeam.totalPower()).
 *
 * Used to also have a buffAffinities field (a global assessment per
 * BuffEffect of how much this hero profits from it) - that was removed. This
 * information now lives locally on the respective buff of a Fortification
 * (see {@link RoleBuff#buffProfits()}): instead of a global "hero X generally
 * likes effect Y" statement, it is now recorded directly on the buff which
 * specific heroes especially profit from EXACTLY THAT buff.
 *
 * displayName is no longer stored in this class - that information now lives
 * in the properties files (deutsch.txt, english.txt, francais.txt). The
 * displayName can be retrieved at runtime via the LanguageService.
 *
 * imagePath: classpath-absolute path (with leading "/") to this hero's avatar
 * icon, e.g. "/images/heroes/Astaroth.png" - suitable for
 * {@code Hero.class.getResourceAsStream(imagePath)}. If an avatar is missing
 * (no icon export available yet), it automatically falls back to the
 * placeholder at {@link #PLACEHOLDER_IMAGE_PATH}, so the field is never null.
 */
public record Hero(
        String id,
        List<Role> roles,
        String imagePath
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
    }

    /** Convenience constructor for heroes without an avatar (e.g. in tests). */
    public Hero(String id, List<Role> roles) {
        this(id, roles, null);
    }
}
