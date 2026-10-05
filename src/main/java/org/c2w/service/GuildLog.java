package org.c2w.service;

import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.infra.RotatingLogFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * The log of the user's actions within one guild: {@value #FILE_NAME} directly in
 * the guild folder, one line per event ("2026-10-05 21:54:12  Guild saved"), in the
 * UI language that is set when the line is written. Only events - no content
 * details, no errors (those go to the technical {@link Logger} only).
 *
 * <p>The file is created with the first event and rotated at 1 MB into
 * {@code guild.log.1} (see {@link RotatingLogFile}). Writing never throws and
 * never creates the guild folder: a failure is noted in the technical log.
 * Listeners (the log window) learn about every event written, together with the
 * guild folder it belongs to.
 */
public final class GuildLog {

    /** File name of the guild log inside a guild folder. */
    public static final String FILE_NAME = "guild.log";

    /** Max size of a guild log before it is rotated. */
    static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L; // 1 MB

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<BiConsumer<Path, String>> listeners = new CopyOnWriteArrayList<>();

    private GuildLog() {
    }

    /**
     * Writes one event to the guild log of {@code guildDir}: the text of {@code key}
     * in the current UI language, formatted with {@code args} (see
     * {@link LanguageService#displayName(String, Object...)}). Never throws.
     */
    public static void event(Path guildDir, String key, Object... args) {
        String text;
        try {
            text = LanguageService.displayName(key, args);
        } catch (RuntimeException e) {
            Logger.logException("Could not build the guild log text for " + key, e);
            text = key;
        }
        write(guildDir, text);
    }

    /** Writes one line with the current date and time and {@code text} - see {@link #event}. Never throws. */
    static void write(Path guildDir, String text) {
        if (guildDir == null) {
            Logger.log("Guild log entry not written (no guild folder): " + text);
            return;
        }
        String line = LocalDateTime.now().format(TIMESTAMP_FORMAT) + "  " + text;
        synchronized (GuildLog.class) {
            if (!Files.isDirectory(guildDir)) {
                Logger.log("Guild log entry not written, guild folder missing: " + guildDir);
                return;
            }
            try {
                new RotatingLogFile(logFile(guildDir), MAX_FILE_SIZE_BYTES).append(line);
            } catch (IOException | RuntimeException e) {
                Logger.log("Could not write " + logFile(guildDir) + ": " + e);
                return;
            }
        }
        for (BiConsumer<Path, String> listener : listeners) {
            try {
                listener.accept(guildDir, line);
            } catch (RuntimeException e) {
                Logger.logException("Guild log listener failed", e);
            }
        }
    }

    /** The guild log file of {@code guildDir} (whether it exists or not). */
    public static Path logFile(Path guildDir) {
        return guildDir.resolve(FILE_NAME);
    }

    /**
     * The lines of the guild log of {@code guildDir}, oldest first - empty if there
     * is none (or it cannot be read, which is logged). The rotated-out older file is
     * not included.
     */
    public static List<String> read(Path guildDir) {
        if (guildDir == null) {
            return List.of();
        }
        Path file = logFile(guildDir);
        synchronized (GuildLog.class) {
            if (!Files.isRegularFile(file)) {
                return List.of();
            }
            try {
                return List.copyOf(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                Logger.logException("Could not read " + file, e);
                return List.of();
            }
        }
    }

    /** Registers a listener for every event written: it gets the guild folder and the written line. */
    public static void addListener(BiConsumer<Path, String> listener) {
        listeners.add(listener);
    }

    public static void removeListener(BiConsumer<Path, String> listener) {
        listeners.remove(listener);
    }

    /** The guild folder of a guild file ({@code guild.json}), null for null. */
    public static Path dirOf(Path guildFile) {
        return guildFile == null ? null : guildFile.getParent();
    }

    /** True if both paths denote the same folder. */
    public static boolean sameDir(Path a, Path b) {
        return a != null && b != null && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
