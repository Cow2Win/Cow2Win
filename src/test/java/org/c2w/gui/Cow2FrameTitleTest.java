package org.c2w.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link Cow2Frame#titleFor}: window title with a leading "*" while something is unsaved. */
class Cow2FrameTitleTest {

    @Test
    @DisplayName("Saved: no star")
    void savedHasNoStar() {
        assertEquals("Cow2Win 1.0.2 - Testgilde", Cow2Frame.titleFor("Testgilde", "1.0.2", false));
    }

    @Test
    @DisplayName("Unsaved: title starts with a star")
    void unsavedStartsWithStar() {
        assertEquals("*Cow2Win 1.0.2 - Testgilde", Cow2Frame.titleFor("Testgilde", "1.0.2", true));
    }

    @Test
    @DisplayName("Blank guild name: the guild id is shown instead")
    void blankGuildNameFallsBackToId() {
        assertEquals("*Cow2Win 1.0.2 - testgilde", Cow2Frame.titleFor(Cow2Frame.guildDisplayName("", "testgilde"), "1.0.2", true));
        assertEquals("Cow2Win 1.0.2 - Testgilde", Cow2Frame.titleFor(Cow2Frame.guildDisplayName("Testgilde", "testgilde"), "1.0.2", false));
    }
}
