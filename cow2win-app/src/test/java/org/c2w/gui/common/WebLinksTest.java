package org.c2w.gui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/** The browser-free part of {@link WebLinks}: telling mail links from web pages. */
class WebLinksTest {

    @Test
    @DisplayName("mailto: is a mail link, http(s) is not")
    void isMail() {
        assertTrue(WebLinks.isMail(URI.create("mailto:cow2win@gmail.com")));
        assertTrue(WebLinks.isMail(URI.create("MAILTO:cow2win@gmail.com")));
        assertFalse(WebLinks.isMail(URI.create("https://github.com/Cow2Win/Cow2Win")));
    }

    @Test
    @DisplayName("The address of a mail link drops a ?subject=...")
    void mailAddress() {
        assertEquals("cow2win@gmail.com", WebLinks.mailAddress(URI.create("mailto:cow2win@gmail.com")));
        assertEquals("cow2win@gmail.com", WebLinks.mailAddress(URI.create("mailto:cow2win@gmail.com?subject=Hi")));
    }
}
