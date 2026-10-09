package org.c2w.i18n;

import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanRole;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Display texts of a titan's master data: its {@link TitanRole roles}
 * ({@code titanRole.<NAME>}) and the "super titan" property
 * ({@code titan.superTitan}), plus a one-line description combining name,
 * element, roles and super titan - e.g. for a tooltip.
 */
public final class TitanTexts {

    /** Language file key prefix of the titan role names. */
    private static final String KEY_ROLE_PREFIX = "titanRole.";

    /** Language file key of the "super titan" label. */
    private static final String KEY_SUPER_TITAN = "titan.superTitan";

    /** Separates the name from the rest of the description, as elsewhere in the app. */
    private static final String NAME_SEPARATOR = " – ";

    /** Separates the parts after the name. */
    private static final String PART_SEPARATOR = " · ";

    private TitanTexts() {
        // Utility class, no instantiation
    }

    /** The localized name of {@code role}, e.g. "Scharfschütze". */
    public static String roleName(TitanRole role) {
        return LanguageService.displayName(KEY_ROLE_PREFIX + role.name());
    }

    /** The localized names of {@code roles} in iteration order, comma-separated - {@code ""} for none. */
    public static String roleNames(Collection<TitanRole> roles) {
        if (roles == null) {
            return "";
        }
        return roles.stream().map(TitanTexts::roleName).collect(Collectors.joining(", "));
    }

    /** The localized "super titan" label, e.g. "Supertitan". */
    public static String superTitan() {
        return LanguageService.displayName(KEY_SUPER_TITAN);
    }

    /**
     * Name, element, roles and - for a super titan - the "super titan" label,
     * e.g. "Araji – Feuer · Scharfschütze, Unterstützer · Supertitan". The
     * roles part is left out for a titan without roles.
     */
    public static String describe(Titan titan) {
        StringBuilder text = new StringBuilder(LanguageService.displayName(titan.id()))
                .append(NAME_SEPARATOR)
                .append(TotemTexts.name(titan.element()));
        if (!titan.roles().isEmpty()) {
            text.append(PART_SEPARATOR).append(roleNames(titan.roles()));
        }
        if (titan.superTitan()) {
            text.append(PART_SEPARATOR).append(superTitan());
        }
        return text.toString();
    }
}
