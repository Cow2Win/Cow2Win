package org.c2w.infra;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The technical, application-wide log: version, start and end, exceptions,
 * data checks, database, backup ... - always in English. The user's own
 * actions within a guild go to that guild's {@code guild.log} instead (see
 * {@code org.c2w.service.GuildLog}), which is what the log window shows; this
 * file is not shown in the UI.
 *
 * <p>Every call to {@link #log(String)} appends one entry, prefixed with date
 * and time (e.g. "[2026-10-05 14:32:07] Saved: workspace/forFun/guild.json"),
 * to {@code cow2win.log} directly in the workspace folder ({@link
 * Config#getWorkspaceDir()}), resolved fresh on every write since that folder
 * can be reconfigured via {@link Config#setWorkspacePath}. The file is capped
 * at {@link #MAX_FILE_SIZE_BYTES} (1 MB) and then rotated into
 * {@code cow2win.log.1} - see {@link RotatingLogFile}.
 *
 * <p>{@link #logException} is the dedicated entry point for logging a caught
 * exception (message plus full stack trace).
 *
 * <p>A failure to write the log file itself is only reported to stderr - as
 * elsewhere in this package (see e.g. {@link Config#save()}) - since logging
 * must never crash or block the application.
 */
public final class Logger {

    /** File name of the technical log in the workspace folder. */
    public static final String LOG_FILE_NAME = "cow2win.log";

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Max size of the persistent log file before it is rotated - see class Javadoc. */
    private static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L; // 1 MB

    private Logger() {
    }

    /** Entries held back by {@link #holdEntries()}, {@code null} while entries are written directly. */
    private static List<String> heldEntries;

    /** Appends one entry (message prefixed with date and time) to the log file - see class Javadoc. */
    public static synchronized void log(String message) {
        String entry = formatEntry(LocalDateTime.now(), message);
        if (heldEntries != null) {
            heldEntries.add(entry);
        } else {
            appendToLogFile(entry);
        }
    }

    /**
     * From now on keeps entries in memory instead of writing them - e.g. at startup while the
     * workspace (and so the log file's place) may still change. See {@link #releaseHeldEntries()}.
     */
    public static synchronized void holdEntries() {
        if (heldEntries == null) {
            heldEntries = new ArrayList<>();
        }
    }

    /**
     * Writes entries directly again and returns the ones held back since {@link #holdEntries()}
     * (with their original time) - to be written with {@link #writeEntries}.
     */
    public static synchronized List<String> releaseHeldEntries() {
        List<String> entries = heldEntries == null ? List.of() : heldEntries;
        heldEntries = null;
        return entries;
    }

    /** Writes already formatted entries (see {@link #releaseHeldEntries()}) to the log file. */
    public static synchronized void writeEntries(List<String> entries) {
        entries.forEach(Logger::appendToLogFile);
    }

    /** One log entry: "[yyyy-MM-dd HH:mm:ss] message". */
    static String formatEntry(LocalDateTime time, String message) {
        return "[" + time.format(TIMESTAMP_FORMAT) + "] " + message;
    }

    /**
     * Convenience for logging a caught exception: delegates to
     * {@link #log(String)} with {@code context} (what was being attempted,
     * e.g. "Could not load guild from workspace/Demo/guild.json") followed
     * by the exception's own message and its full stack trace, so the log
     * file shows enough detail to diagnose the problem later without needing
     * to reproduce it.
     */
    public static void logException(String context, Throwable t) {
        StringWriter stackTraceWriter = new StringWriter();
        t.printStackTrace(new PrintWriter(stackTraceWriter));
        log(context + ": " + t + System.lineSeparator() + stackTraceWriter);
    }

    /**
     * Appends entry to the log file (creating the workspace folder if it does
     * not exist yet), rotating it if needed. Never throws - see class Javadoc.
     */
    private static void appendToLogFile(String entry) {
        Path logFilePath = logFilePath();
        try {
            if (logFilePath.getParent() != null) {
                Files.createDirectories(logFilePath.getParent());
            }
            new RotatingLogFile(logFilePath, MAX_FILE_SIZE_BYTES).append(entry);
        } catch (IOException e) {
            System.err.println("Could not write " + logFilePath + ": " + e.getMessage());
        }
    }

    /** Where the log file lives - resolved fresh every time, since {@link Config#getWorkspaceDir()} can change at runtime. */
    private static Path logFilePath() {
        return Config.getWorkspaceDir().resolve(LOG_FILE_NAME);
    }
}
