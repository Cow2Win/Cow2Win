package org.c2w.i18n;

import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.Role;
import org.c2w.data.model.RoleBuff;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.data.model.TitanRole;
import org.c2w.infra.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link TitanTexts} (titan roles, super titan, tooltip description) and the German hero role "Panzer". */
class TitanTextsTest {

    private String previousLanguage;

    @BeforeEach
    void useGerman() {
        previousLanguage = Config.getLanguage();
        useLanguage("deutsch");
    }

    @AfterEach
    void restoreLanguage() {
        useLanguage(previousLanguage);
    }

    private static void useLanguage(String language) {
        Config.setLanguage(language);
        LanguageService.resetCache();
    }

    @Test
    @DisplayName("Araji (deutsch): name, element, both roles and Supertitan")
    void describeSuperTitan() {
        Titan araji = new Titan("araji", TitanElement.FIRE, List.of(TitanRole.MARKSMAN, TitanRole.SUPPORT), true);

        assertEquals("Araji – Feuer · Scharfschütze, Unterstützer · Supertitan", TitanTexts.describe(araji));
    }

    @Test
    @DisplayName("Moloch (deutsch): no Supertitan part")
    void describeNormalTitan() {
        Titan moloch = new Titan("moloch", TitanElement.FIRE, List.of(TitanRole.TANK), false);

        String text = TitanTexts.describe(moloch);
        assertEquals("Moloch – Feuer · Panzer", text);
        assertFalse(text.contains("Supertitan"));
    }

    @Test
    @DisplayName("a titan without roles: name and element only")
    void describeWithoutRoles() {
        assertEquals("Moloch – Feuer", TitanTexts.describe(new Titan("moloch", TitanElement.FIRE)));
    }

    @Test
    @DisplayName("every TitanRole and the super titan label have a text in every language")
    void textsInEveryLanguage() {
        for (String language : new String[]{"deutsch", "english", "francais"}) {
            for (TitanRole role : TitanRole.values()) {
                String text = LanguageService.textIn(language, "titanRole." + role.name());
                assertNotNull(text, language + ": titanRole." + role.name());
                assertFalse(text.isBlank(), language + ": titanRole." + role.name());
            }
            assertNotNull(LanguageService.textIn(language, "titan.superTitan"), language);
        }
    }

    @Test
    @DisplayName("deutsch: the hero role TANK is 'Panzer', also in a fortification's buff text")
    void germanHeroTankIsPanzer() {
        assertEquals("Panzer", LanguageService.displayName("role.TANK"));
        String buffText = BuffTexts.describe(new RoleBuff(Role.TANK, BuffEffect.values()[0], 10));
        assertTrue(buffText.contains("Panzer"), buffText);
        assertFalse(buffText.contains("Tank"), buffText);
    }
}
