package org.c2w.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link Cow2Frame#titleFor}: window title with application name and version only. */
class Cow2FrameTitleTest {

    @Test
    @DisplayName("Title is application name and version - no guild, no star")
    void nameAndVersion() {
        assertEquals("Cow2Win 1.0.2", Cow2Frame.titleFor("1.0.2"));
    }
}
