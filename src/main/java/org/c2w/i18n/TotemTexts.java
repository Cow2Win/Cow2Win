package org.c2w.i18n;

import org.c2w.data.model.TitanElement;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Display texts for a titan team's totems (see {@code TitanTeam#totems()}):
 * a totem IS a {@link TitanElement}, so its name is the element's name from
 * the language files ({@code titanElement.<NAME>}).
 */
public final class TotemTexts {

    /** Language file key prefix of the element names. */
    private static final String KEY_ELEMENT_PREFIX = "titanElement.";

    /** Language file key of the totem list, e.g. "Totems: {0}". */
    private static final String KEY_LIST = "totem.list";

    private TotemTexts() {
        // Utility class, no instantiation
    }

    /** The localized name of {@code totem}, e.g. "Feuer". */
    public static String name(TitanElement totem) {
        return LanguageService.displayName(KEY_ELEMENT_PREFIX + totem.name());
    }

    /** The localized names of {@code totems} in iteration order, comma-separated, e.g. "Feuer, Wasser" - {@code ""} for none. */
    public static String names(Collection<TitanElement> totems) {
        if (totems == null) {
            return "";
        }
        return totems.stream().map(TotemTexts::name).collect(Collectors.joining(", "));
    }

    /** E.g. "Totems: Feuer, Wasser" - {@code ""} if there are no totems. */
    public static String list(Collection<TitanElement> totems) {
        return totems == null || totems.isEmpty() ? "" : LanguageService.displayName(KEY_LIST, names(totems));
    }
}
