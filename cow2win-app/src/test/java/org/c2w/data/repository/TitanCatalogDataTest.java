package org.c2w.data.repository;

import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The shipped {@code titans.json}: roles and super titans as checked in the game. */
class TitanCatalogDataTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("every shipped titan has at least one role")
    void everyTitanHasARole() {
        for (Titan titan : new TitanRepository(workspace).findAll()) {
            assertFalse(titan.roles().isEmpty(), titan.id() + " has no role");
        }
    }

    @Test
    @DisplayName("exactly araji, hyperion, tenebris, eden and solaris are super titans")
    void superTitans() {
        Set<String> superTitans = new TitanRepository(workspace).findAll().stream()
                .filter(Titan::superTitan).map(Titan::id).collect(Collectors.toSet());

        assertEquals(Set.of("araji", "hyperion", "tenebris", "eden", "solaris"), superTitans);
    }

    @Test
    @DisplayName("samples: hyperion = MAGE + SUPPORT, moloch = TANK, valdur-and-echo = SUMMONER")
    void samples() {
        TitanRepository repository = new TitanRepository(workspace);

        assertEquals(List.of(TitanRole.MAGE, TitanRole.SUPPORT), repository.findById("hyperion").orElseThrow().roles());
        assertEquals(List.of(TitanRole.TANK), repository.findById("moloch").orElseThrow().roles());
        assertEquals(List.of(TitanRole.SUMMONER), repository.findById("valdur-and-echo").orElseThrow().roles());
    }

    @Test
    @DisplayName("corrected ids: asherona-and-pyro, tidus-and-gelo, umbra-and-caligo, sylva; the old ids are gone")
    void correctedIds() {
        Set<String> ids = new TitanRepository(workspace).findAll().stream().map(Titan::id).collect(Collectors.toSet());

        assertTrue(ids.containsAll(Set.of("asherona-and-pyro", "tidus-and-gelo", "umbra-and-caligo", "sylva")), ids::toString);
        for (String old : List.of("aherona-and-pyro", "Tidus-and-gelo", "umbra-and-caliga", "silva")) {
            assertFalse(ids.contains(old), old);
        }
    }

    @Test
    @DisplayName("no titan id contains upper case letters")
    void idsAreLowerCase() {
        for (Titan titan : new TitanRepository(workspace).findAll()) {
            assertEquals(titan.id().toLowerCase(), titan.id());
        }
    }
}
