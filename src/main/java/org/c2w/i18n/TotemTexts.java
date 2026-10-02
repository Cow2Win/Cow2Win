package org.c2w.i18n;

import org.c2w.data.model.TitanElement;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Display texts for a titan team's totems (see {@code TitanTeam#totems()}):
 * a totem IS a {@link TitanElement}, so its name is the element's name from
 * the language files ({@code titanElement.<NAME>}).
 *
 * <p>Separately, {@link #gameName} / {@link #fromGameName} handle the totem's
 * in-game name ({@code titanTotem.<NAME>}, e.g. "Feuergeisttotem"), as it
 * appears in the Clash of Worlds battle logs.
 */
public final class TotemTexts {

    /** Language file key prefix of the element names. */
    private static final String KEY_ELEMENT_PREFIX = "titanElement.";

    /** Language file key prefix of the in-game totem names. */
    private static final String KEY_GAME_NAME_PREFIX = "titanTotem.";

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

    /**
     * The in-game name of {@code totem} in the configured language, as it
     * appears in the Clash of Worlds battle logs, e.g. "Feuergeisttotem" /
     * "Fire Spirit Totem". For display in the editor/report use
     * {@link #name} instead.
     */
    public static String gameName(TitanElement totem) {
        return LanguageService.displayName(KEY_GAME_NAME_PREFIX + totem.name());
    }

    /**
     * The totem whose in-game name (see {@link #gameName}) is {@code text}
     * in any available language - not just the configured one, since a
     * battle log is written in the game language of the exporting account.
     * Case and surrounding whitespace are ignored, and non-breaking spaces
     * (U+00A0, used in French battle logs) as well as runs of whitespace
     * count as a single normal space; empty for an unknown or blank text.
     */
    public static Optional<TitanElement> fromGameName(String text) {
        String wanted = normalize(text);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        for (String language : LanguageService.availableLanguages()) {
            for (TitanElement element : TitanElement.values()) {
                String gameName = LanguageService.textIn(language, KEY_GAME_NAME_PREFIX + element.name());
                if (gameName != null && normalize(gameName).equalsIgnoreCase(wanted)) {
                    return Optional.of(element);
                }
            }
        }
        return Optional.empty();
    }

    /** {@code text} with non-breaking spaces turned into normal ones, whitespace runs collapsed and stripped - {@code ""} for {@code null}. */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
    }
}
