package org.c2w.data.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link Titan}'s roles, super titan property and {@link Titan#withFortMarks}. */
class TitanTest {

    @Test
    @DisplayName("the convenience constructor gives no roles and no super titan")
    void convenienceConstructor() {
        Titan titan = new Titan("t", TitanElement.FIRE);

        assertEquals(List.of(), titan.roles());
        assertFalse(titan.superTitan());
        assertEquals(Titan.PLACEHOLDER_IMAGE_PATH, titan.imagePath());
        assertEquals(FortMarks.NONE, titan.fortMarks());
    }

    @Test
    @DisplayName("null roles become an empty list, the roles are an unmodifiable copy")
    void rolesAreCopied() {
        assertEquals(List.of(), new Titan("t", TitanElement.FIRE, null, false).roles());

        List<TitanRole> roles = new ArrayList<>(List.of(TitanRole.MARKSMAN));
        Titan titan = new Titan("t", TitanElement.FIRE, roles, false);
        roles.add(TitanRole.TANK);
        assertEquals(List.of(TitanRole.MARKSMAN), titan.roles());
        assertThrows(UnsupportedOperationException.class, () -> titan.roles().add(TitanRole.MAGE));
    }

    @Test
    @DisplayName("withFortMarks keeps roles, super titan, element and image")
    void withFortMarksKeepsEverythingElse() {
        Titan titan = new Titan("t", TitanElement.WATER, List.of(TitanRole.MAGE, TitanRole.SUPPORT), true,
                "/images/titans/T.png", null);
        FortMarks marks = new FortMarks(Map.of("bridge", FortMark.NEGATIVE));

        Titan copy = titan.withFortMarks(marks);

        assertEquals(new Titan("t", TitanElement.WATER, List.of(TitanRole.MAGE, TitanRole.SUPPORT), true,
                "/images/titans/T.png", marks), copy);
        assertTrue(copy.hasRole(TitanRole.MAGE));
        assertFalse(copy.hasRole(TitanRole.TANK));
    }
}
