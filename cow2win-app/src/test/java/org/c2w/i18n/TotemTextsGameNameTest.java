package org.c2w.i18n;

import org.c2w.data.model.TitanElement;
import org.c2w.infra.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.c2w.data.model.TitanElement.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The totems' in-game names ({@code titanTotem.<NAME>}) as they appear in
 * the Clash of Worlds battle logs: present for every element in every
 * language, and mapped back to the element via {@link TotemTexts#fromGameName}.
 */
class TotemTextsGameNameTest {

    private static final Map<TitanElement, String> GERMAN = Map.of(
            FIRE, "Feuergeisttotem",
            WATER, "Wassergeisttotem",
            EARTH, "Erdgeisttotem",
            LIGHT, "Lichtgeisttotem",
            DARK, "Dunkelgeisttotem",
            DISTORTION, "Elementargeist der Verzerrung");

    private static final Map<TitanElement, String> ENGLISH = Map.of(
            FIRE, "Fire Spirit Totem",
            WATER, "Water Spirit Totem",
            EARTH, "Earth Spirit Totem",
            LIGHT, "Light Spirit Totem",
            DARK, "Dark Spirit Totem",
            DISTORTION, "Elemental Spirit of Distortion");

    private static final Map<TitanElement, String> FRENCH = Map.of(
            FIRE, "Totem de l'esprit du feu",
            WATER, "Totem de l'esprit de l'eau",
            EARTH, "Totem de l'esprit de la terre",
            LIGHT, "Totem d'esprit de lumière",
            DARK, "Totem d'esprit des ténèbres",
            DISTORTION, "Esprit élémentaire de distorsion");

    private static final Map<TitanElement, String> SPANISH = Map.of(
            FIRE, "Tótem de espíritu de fuego",
            WATER, "Tótem de espíritu de agua",
            EARTH, "Tótem de espíritu de tierra",
            LIGHT, "Tótem de espíritu de luz",
            DARK, "Tótem de espíritu de oscuridad",
            DISTORTION, "Espíritu elemental de la distorsión");

    private String previousLanguage;

    @BeforeEach
    void rememberLanguage() {
        previousLanguage = Config.getLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        Config.setLanguage(previousLanguage);
        LanguageService.resetCache();
    }

    private static void useLanguage(String language) {
        Config.setLanguage(language);
        LanguageService.resetCache();
    }

    @Test
    @DisplayName("every TitanElement has a non-empty titanTotem.<NAME> in every language")
    void everyElementHasAGameNameInEveryLanguage() {
        for (String language : LanguageService.availableLanguages()) {
            for (TitanElement element : TitanElement.values()) {
                String gameName = LanguageService.textIn(language, "titanTotem." + element.name());
                assertNotNull(gameName, "titanTotem." + element.name() + " missing in " + language);
                assertFalse(gameName.isBlank(), "titanTotem." + element.name() + " empty in " + language);
            }
        }
    }

    @Test
    @DisplayName("gameName returns the in-game name of the configured language (DE, EN, FR and ES)")
    void gameName() {
        useLanguage("deutsch");
        GERMAN.forEach((element, name) -> assertEquals(name, TotemTexts.gameName(element)));
        assertEquals("Feuer", TotemTexts.name(FIRE), "display name stays the element name");

        useLanguage("english");
        ENGLISH.forEach((element, name) -> assertEquals(name, TotemTexts.gameName(element)));

        useLanguage("francais");
        FRENCH.forEach((element, name) -> assertEquals(name, TotemTexts.gameName(element)));

        useLanguage("espanol");
        SPANISH.forEach((element, name) -> assertEquals(name, TotemTexts.gameName(element)));
    }

    @Test
    @DisplayName("fromGameName maps all 24 names regardless of language, case and surrounding whitespace")
    void fromGameName() {
        useLanguage("english"); // the lookup must not depend on the configured language
        GERMAN.forEach((element, name) -> {
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name), name);
            assertEquals(Optional.of(element), TotemTexts.fromGameName("  " + name.toUpperCase() + " "), name);
        });
        ENGLISH.forEach((element, name) -> {
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name), name);
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name.toLowerCase()), name);
        });
        FRENCH.forEach((element, name) -> {
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name), name);
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name.toUpperCase()), name);
        });
        SPANISH.forEach((element, name) -> {
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name), name);
            assertEquals(Optional.of(element), TotemTexts.fromGameName(name.toLowerCase()), name);
        });
    }

    @Test
    @DisplayName("fromGameName treats non-breaking spaces (U+00A0, used in French battle logs) like normal spaces")
    void fromGameNameNonBreakingSpace() {
        assertEquals(Optional.of(DARK), TotemTexts.fromGameName("Totem\u00A0d'esprit\u00A0des ténèbres"));
        assertEquals(Optional.of(FIRE), TotemTexts.fromGameName("\u00A0Fire Spirit Totem\u00A0"));
        assertEquals(Optional.of(DISTORTION), TotemTexts.fromGameName("Elementargeist  der Verzerrung"));
    }

    @Test
    @DisplayName("fromGameName returns empty for unknown, blank or null text")
    void fromGameNameUnknown() {
        assertEquals(Optional.empty(), TotemTexts.fromGameName("Windgeisttotem"));
        assertEquals(Optional.empty(), TotemTexts.fromGameName("Feuer"));
        assertEquals(Optional.empty(), TotemTexts.fromGameName("  "));
        assertEquals(Optional.empty(), TotemTexts.fromGameName(null));
    }
}
