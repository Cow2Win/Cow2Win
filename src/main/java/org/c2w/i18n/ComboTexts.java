package org.c2w.i18n;

import org.c2w.data.model.TeamCombo;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the human-readable name of a {@link TeamCombo}. Combo names are
 * deliberately NOT kept in the language files - the user can add own combos
 * to {@code heroCombos.json} but cannot add keys to the language files
 * inside the jar. Instead the name is put together from the members'
 * already localized names (e.g. "Sebastian + Nebula"), which works for
 * shipped and user combos alike and always follows the configured
 * language. A custom {@link TeamCombo#name()} set by the user takes
 * precedence and is shown as is.
 */
public final class ComboTexts {

    /** Separator between the member names - language-neutral. */
    static final String SEPARATOR = " + ";

    private ComboTexts() {
        // Utility class, no instantiation
    }

    /** Display name of {@code combo} in the configured language, or {@code ""} for {@code null}. */
    public static String displayName(TeamCombo combo) {
        return displayName(combo, LanguageService::displayName);
    }

    /** The localized names of {@code memberIds}, joined like a combo's display name - also for a combo still being edited. */
    public static String memberNames(List<String> memberIds) {
        return memberIds.stream().map(LanguageService::displayName).collect(Collectors.joining(SEPARATOR));
    }

    /** Like {@link #displayName(TeamCombo)}, resolving each member id via {@code memberName}. */
    static String displayName(TeamCombo combo, Function<String, String> memberName) {
        if (combo == null) {
            return "";
        }
        if (combo.hasCustomName()) {
            return combo.name();
        }
        return combo.memberIds().stream().map(memberName).collect(Collectors.joining(SEPARATOR));
    }
}
