package org.c2w.data.repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The optional {@code "aliases"} of heroes, titans and pets
 * ({@link CatalogAliases}).
 */
class CatalogAliasesTest {

    @Test
    void shippedCatalogHasTheKnownAliases() {
        CatalogAliases aliases = CatalogAliases.load();

        assertEquals(List.of("Lara"), aliases.aliases("lara"));
        assertEquals(List.of("Silva"), aliases.aliases("silva"));
        assertEquals(List.of(), aliases.aliases("tenebris"), "entry without aliases");
        assertEquals(List.of(), aliases.aliases("no-such-id"));
    }

    @Test
    void parseDropsBlankAndDuplicateAliasesAndMergesAcrossCatalogs() {
        String heroes = """
                [
                  { "id": "a", "aliases": [" Alpha ", "", "Alpha", "A1"] },
                  { "id": "b" },
                  { "aliases": ["no id"] }
                ]""";
        String titans = """
                [
                  { "id": "a", "aliases": ["A2"] },
                  { "id": "c", "aliases": [] }
                ]""";

        CatalogAliases aliases = CatalogAliases.parse(List.of(heroes, titans));

        assertEquals(Map.of("a", List.of("Alpha", "A1", "A2")), aliases.all());
        assertEquals(List.of(), aliases.aliases("b"));
        assertEquals(List.of(), aliases.aliases("c"));
    }
}
