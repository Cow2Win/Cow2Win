package org.c2w.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameNameNormalizerTest {

    @Test
    void cleanKeepsCaseButNormalizesSpacesAndApostrophes() {
        assertEquals("Tortues Ninja", GameNameNormalizer.clean("Tortues Ninja"));
        assertEquals("Vale", GameNameNormalizer.clean("  Vale  "));
        assertEquals("Astrid and Lucas", GameNameNormalizer.clean("Astrid   and\tLucas"));
        assertEquals("K'arkh", GameNameNormalizer.clean("K’arkh"));
        assertEquals("", GameNameNormalizer.clean(null));
    }

    @Test
    void keyIgnoresCase() {
        assertEquals("champi und gnon", GameNameNormalizer.key(" Champi UND  Gnon "));
        assertEquals("", GameNameNormalizer.key(null));
    }

    @Test
    void sameName() {
        assertTrue(GameNameNormalizer.sameName("Heroes' Bridge", "heroes’ bridge"));
        assertTrue(GameNameNormalizer.sameName("Ténèbris", "TÉNÈBRIS"));
        assertFalse(GameNameNormalizer.sameName("Tenebris", "Ténèbris"), "accents are significant");
        assertFalse(GameNameNormalizer.sameName("Astrid & Lucas", "Astrid and Lucas"), "no word replacement");
    }
}
