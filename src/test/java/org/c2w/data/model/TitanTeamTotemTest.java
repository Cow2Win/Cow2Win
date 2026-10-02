package org.c2w.data.model;

import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.c2w.data.model.TitanElement.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A titan team's totems (see {@link TitanTeam#totems()}): the rules enforced
 * by the constructor, {@link TitanTeam#eligibleTotems} and
 * {@link TitanTeam#validTotems} - plus the {@link TitanElement}s they are
 * based on.
 */
class TitanTeamTotemTest {

    private static List<Titan> titans(TitanElement... elements) {
        List<Titan> titans = new ArrayList<>();
        for (int i = 0; i < elements.length; i++) {
            titans.add(new Titan("t" + i, elements[i]));
        }
        return titans;
    }

    private static TitanTeam team(List<Titan> titans, Set<TitanElement> totems) {
        return new TitanTeam("m1", 0, titans, 1000, null, totems);
    }

    @Test
    @DisplayName("WIND no longer exists; every element has a name in the language files")
    void elements() {
        assertThrows(IllegalArgumentException.class, () -> TitanElement.valueOf("WIND"));
        assertEquals(List.of(FIRE, WATER, EARTH, LIGHT, DARK, DISTORTION), Arrays.asList(TitanElement.values()));
        for (TitanElement element : TitanElement.values()) {
            String key = "titanElement." + element.name();
            assertNotEquals(key, LanguageService.displayName(key), "missing language key " + key);
        }
    }

    @Test
    @DisplayName("0, 1 and 2 allowed totems are fine, stored in element order")
    void allowedTotems() {
        List<Titan> titans = titans(WATER, FIRE, WATER, FIRE, WATER);
        assertEquals(Set.of(), team(titans, Set.of()).totems());
        assertEquals(Set.of(FIRE), team(titans, Set.of(FIRE)).totems());
        assertEquals(List.of(FIRE, WATER), new ArrayList<>(team(titans, Set.of(WATER, FIRE)).totems()));
    }

    @Test
    @DisplayName("3 totems are rejected")
    void threeTotemsRejected() {
        List<Titan> titans = titans(FIRE, FIRE, WATER, WATER, EARTH, EARTH);
        assertThrows(IllegalArgumentException.class, () -> team(titans, Set.of(FIRE, WATER, EARTH)));
    }

    @Test
    @DisplayName("a totem needs at least 2 titans of its element: 1 is rejected, 2 and 3 are fine")
    void minimumTitansPerTotem() {
        assertThrows(IllegalArgumentException.class, () -> team(titans(FIRE, WATER, WATER), Set.of(FIRE)));
        assertThrows(IllegalArgumentException.class, () -> team(titans(WATER, WATER), Set.of(FIRE)));
        assertEquals(Set.of(FIRE), team(titans(FIRE, FIRE, WATER), Set.of(FIRE)).totems());
        assertEquals(Set.of(FIRE), team(titans(FIRE, FIRE, FIRE), Set.of(FIRE)).totems());
    }

    @Test
    @DisplayName("null means no totems; the convenience constructors have none")
    void nullAndConvenienceConstructors() {
        assertEquals(Set.of(), team(titans(FIRE), null).totems());
        assertEquals(Set.of(), new TitanTeam("m1", 0, titans(FIRE, FIRE), 1000, null).totems());
        assertEquals(Set.of(), new TitanTeam("m1", 0, titans(FIRE, FIRE), 1000).totems());
        assertEquals(Set.of(), new TitanTeam().totems());
    }

    @Test
    @DisplayName("the totems are unmodifiable")
    void unmodifiable() {
        TitanTeam team = team(titans(FIRE, FIRE), Set.of(FIRE));
        assertThrows(UnsupportedOperationException.class, () -> team.totems().add(WATER));
    }

    @Test
    @DisplayName("eligibleTotems: the examples of the requirement")
    void eligibleTotems() {
        assertEquals(Set.of(FIRE, WATER), TitanTeam.eligibleTotems(titans(FIRE, FIRE, WATER, WATER, WATER)));
        assertEquals(Set.of(EARTH), TitanTeam.eligibleTotems(titans(EARTH, EARTH, EARTH, LIGHT, DARK)));
        assertEquals(Set.of(), TitanTeam.eligibleTotems(titans(FIRE, WATER, EARTH, LIGHT, DARK)));
        assertEquals(Set.of(), TitanTeam.eligibleTotems(List.of()));
        assertEquals(Set.of(), TitanTeam.eligibleTotems(null));
    }

    @Test
    @DisplayName("validTotems drops duplicates, totems without 2 titans and everything beyond 2 - reporting each")
    void validTotems() {
        List<Titan> titans = titans(FIRE, FIRE, WATER, WATER, EARTH, EARTH);
        List<String> dropped = new ArrayList<>();
        Set<TitanElement> valid = TitanTeam.validTotems(List.of(WATER, WATER, LIGHT, FIRE, EARTH), titans, dropped::add);

        assertEquals(List.of(FIRE, WATER), new ArrayList<>(valid));
        assertEquals(3, dropped.size(), dropped.toString());
        assertTrue(dropped.get(0).contains("WATER"));
        assertTrue(dropped.get(1).contains("LIGHT"));
        assertTrue(dropped.get(2).contains("EARTH"));

        assertEquals(Set.of(), TitanTeam.validTotems(null, titans, null));
    }
}
