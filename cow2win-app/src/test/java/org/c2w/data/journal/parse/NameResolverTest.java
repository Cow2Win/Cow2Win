package org.c2w.data.journal.parse;

import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.parse.NameResolver.NameKind;
import org.c2w.data.model.TitanElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NameResolverTest {

    private static NameResolver resolver;

    @BeforeAll
    static void load() {
        resolver = NameResolver.load();
    }

    @Test
    void searchesAllLanguages() {
        assertEquals("bridge", resolver.id(NameKind.FORTIFICATION, "Brücke"));
        assertEquals("bridge", resolver.id(NameKind.FORTIFICATION, "Bridge"));
        assertEquals("bridge", resolver.id(NameKind.FORTIFICATION, "Pont"));
        assertEquals("mushy-and-shroom", resolver.id(NameKind.HERO, "Champi und Gnon"));
        assertEquals("mushy-and-shroom", resolver.id(NameKind.HERO, "Mushy and Shroom"));
        assertEquals("mushy-and-shroom", resolver.id(NameKind.HERO, "Champi et Gnon"));
        assertEquals("asherona-and-pyro", resolver.id(NameKind.TITAN, "Asherona und Pyro"));
        assertEquals("biscuit", resolver.id(NameKind.PET, "Biskuit"));
    }

    @Test
    void normalizesNames() {
        assertEquals("sylva", resolver.id(NameKind.TITAN, "SYLVA"));
        assertNull(resolver.id(NameKind.HERO, "Lara"), "only the in-game names from the language files");
        assertEquals("lara", resolver.id(NameKind.HERO, " lara croft "));
    }

    @Test
    void kindsAreSeparate() {
        assertNull(resolver.id(NameKind.HERO, "Brücke"));
        assertNull(resolver.id(NameKind.PET, "Dante"));
        assertNull(resolver.id(NameKind.FORTIFICATION, "toolbar.guild"), "UI keys are no catalog ids");
    }

    @Test
    void unknownName() {
        NameResolver.Match match = resolver.resolve(NameKind.HERO, "Neuheld");

        assertFalse(match.isFound());
        assertFalse(match.ambiguous());
        assertNull(resolver.id(NameKind.HERO, ""));
    }

    @Test
    void ambiguousNameResolvesToNothing() {
        NameResolver custom = NameResolver.of(Map.of(NameKind.HERO,
                Map.of("one", List.of("Twin"), "two", List.of("twin"))));

        NameResolver.Match match = custom.resolve(NameKind.HERO, "TWIN");

        assertNull(match.id());
        assertTrue(match.ambiguous());
        assertEquals(Set.of("one", "two"), match.candidates());
    }

    @Test
    void manualMappingsApplyOnlyToUnknownOrAmbiguousNames() {
        NameResolver custom = NameResolver.of(Map.of(NameKind.HERO,
                Map.of("one", List.of("Twin"), "two", List.of("twin"), "dante", List.of("Dante"))));

        NameResolver mapped = custom.withMappings(Map.of(NameMappingKind.HERO, Map.of(
                "Neuheld", "dante",      // unknown name -> mapped
                "Twin", "one",           // ambiguous name -> mapped
                "Dante", "two",          // known name -> the language files win
                "Ghost", "no-such-id"))); // not in the catalog -> ignored

        assertEquals("dante", mapped.id(NameKind.HERO, " NEUHELD "));
        assertEquals("one", mapped.id(NameKind.HERO, "Twin"));
        assertEquals("dante", mapped.id(NameKind.HERO, "Dante"));
        assertNull(mapped.id(NameKind.HERO, "Ghost"));
        assertNull(mapped.id(NameKind.PET, "Neuheld"), "mappings are per kind");
        assertNull(custom.id(NameKind.HERO, "Neuheld"), "the original resolver is unchanged");
    }

    @Test
    void manualTotemMapping() {
        NameResolver mapped = resolver.withMappings(Map.of(NameMappingKind.TOTEM, Map.of(
                "Sturmgeisttotem", "DISTORTION", "Feuergeisttotem", "WATER", "Kaputt", "NOT_AN_ELEMENT")));

        assertEquals(TitanElement.DISTORTION, mapped.totem("Sturmgeisttotem"));
        assertEquals(TitanElement.FIRE, mapped.totem("Feuergeisttotem"), "known totem names win");
        assertNull(mapped.totem("Kaputt"));
        assertTrue(resolver.isValidTarget(NameMappingKind.HERO, "dante"));
        assertFalse(resolver.isValidTarget(NameMappingKind.PET, "dante"));
    }

    @Test
    void totems() {
        assertEquals(TitanElement.FIRE, resolver.totem("Feuergeisttotem"));
        assertEquals(TitanElement.DISTORTION, resolver.totem("Elemental Spirit of Distortion"));
        assertNull(resolver.totem("Araji"));
    }
}
