package org.c2w.data.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link Guild#displayName()}: the name, or the id if there is no name. */
class GuildTest {

    @Test
    @DisplayName("displayName: the name if set, otherwise the id")
    void displayName() {
        assertEquals("Testgilde", new Guild("testgilde", "Testgilde", List.of()).displayName());
        assertEquals("testgilde", new Guild("testgilde", "", List.of()).displayName());
        assertEquals("testgilde", new Guild("testgilde", "  ", List.of()).displayName());
        assertEquals("testgilde", new Guild("testgilde", null, List.of()).displayName());
    }
}
