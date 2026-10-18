package org.c2w.data.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GuildNoteRepositoryTest {

    @TempDir
    Path guildDir;

    @Test
    @DisplayName("No note file - an empty note")
    void missingFileIsEmpty() throws Exception {
        assertEquals("", GuildNoteRepository.read(guildDir));
    }

    @Test
    @DisplayName("Written text is read back unchanged, umlauts, Cyrillic and emojis included, as UTF-8 without BOM")
    void roundTripUtf8() throws Exception {
        String text = "Absprachen für Größe\nКрепость удержать\nTeam 🐄🏆\n";
        GuildNoteRepository.write(guildDir, text);

        assertEquals(text, GuildNoteRepository.read(guildDir));
        byte[] bytes = Files.readAllBytes(guildDir.resolve(GuildNoteRepository.FILE_NAME));
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    @DisplayName("Windows line breaks are read as \\n")
    void crlfIsNormalized() throws Exception {
        Files.writeString(guildDir.resolve(GuildNoteRepository.FILE_NAME), "a\r\nb\r\n", StandardCharsets.UTF_8);
        assertEquals("a\nb\n", GuildNoteRepository.read(guildDir));
    }

    @Test
    @DisplayName("An empty or blank note deletes the file, or does not create it")
    void blankNoteDeletesFile() throws Exception {
        Path file = guildDir.resolve(GuildNoteRepository.FILE_NAME);
        GuildNoteRepository.write(guildDir, " \n\t ");
        assertFalse(Files.exists(file));

        GuildNoteRepository.write(guildDir, "note");
        assertTrue(Files.exists(file));
        GuildNoteRepository.write(guildDir, "");
        assertFalse(Files.exists(file));
        assertEquals("", GuildNoteRepository.read(guildDir));
    }

    @Test
    @DisplayName("Writing replaces an existing note and leaves no temporary file behind")
    void overwrites() throws Exception {
        GuildNoteRepository.write(guildDir, "first");
        GuildNoteRepository.write(guildDir, "second");

        assertEquals("second", GuildNoteRepository.read(guildDir));
        try (var files = Files.list(guildDir)) {
            assertEquals(1, files.count());
        }
    }
}
