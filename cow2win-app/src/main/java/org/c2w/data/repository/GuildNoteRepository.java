package org.c2w.data.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The free text note of a guild: {@value #FILE_NAME} directly in the guild folder, plain UTF-8
 * text without a BOM. Not part of {@code guild.json} - a missing file is an empty note, and an
 * empty (or blank) note is stored by deleting the file.
 */
public final class GuildNoteRepository {

    /** File name of the note inside a guild folder. */
    public static final String FILE_NAME = "note.txt";

    private GuildNoteRepository() {
    }

    /** The note file of {@code guildDir}. */
    public static Path fileOf(Path guildDir) {
        return guildDir.resolve(FILE_NAME);
    }

    /** The note of {@code guildDir} with {@code \n} line breaks - empty if there is no note file. */
    public static String read(Path guildDir) throws IOException {
        Path file = fileOf(guildDir);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        return normalize(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Stores {@code text} exactly as given in the note file of {@code guildDir} - atomically via a
     * temporary file in the same folder - or deletes the file if {@code text} is null or blank.
     */
    public static void write(Path guildDir, String text) throws IOException {
        Path target = fileOf(guildDir);
        if (text == null || text.isBlank()) {
            Files.deleteIfExists(target);
            return;
        }
        Path temp = Files.createTempFile(guildDir, FILE_NAME + ".", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** {@code text} with Windows ({@code \r\n}) line breaks turned into {@code \n}; null becomes empty. */
    public static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n");
    }
}
