package org.c2w.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Central logging facility used throughout the application.
 *
 * <p>Every call to {@link #log(String)} does three things, in order: the
 * entry is appended to an in-memory {@code history} (replayed to every
 * newly registered {@link #addListener}, e.g. {@code org.c2w.gui.LogPanel}),
 * it is passed live to every already-registered listener, and - added for
 * the persistent log file - it is appended to a log file on disk (see
 * {@link #logFilePath()}). This means the file always contains exactly what
 * the running app's {@code LogPanel} shows (plus everything logged in
 * earlier sessions), without needing a separate mechanism to keep the two in
 * sync.
 *
 * <p>The log file lives directly in the workspace folder ({@link
 * Config#getWorkspaceDir()}), resolved fresh on every write (rather than
 * cached in a {@code static final} field) since that folder can be
 * reconfigured via {@link Config#setWorkspacePath}. It is capped at {@link
 * #MAX_FILE_SIZE_BYTES} (1 MB); once appending the
 * next entry would exceed that, the file is rotated into
 * {@link #ROTATED_LOG_FILE_PATH} (overwriting whatever was rotated out
 * before) and a fresh, empty file is started - so at most two log files
 * ever exist on disk at once.
 *
 * <p>Besides {@link #log(String)} for ordinary entries, {@link #logException}
 * is the dedicated entry point for logging a caught exception (message plus
 * full stack trace) - used throughout the {@code org.c2w} packages wherever
 * a problem used to be reported to stderr only, so it now also shows up in
 * {@code LogPanel} and survives in the log file after the app is closed.
 *
 * <p>A failure to write the log file itself is only reported to stderr - as
 * elsewhere in this package (see e.g. {@link Config#save()}) - since logging
 * must never crash or block the application.
 */
public final class Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Max size of the persistent log file before it is rotated - see class Javadoc. */
    private static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L; // 1 MB

    private static final List<String> history = new ArrayList<>();
    private static final List<Consumer<String>> listeners = new ArrayList<>();

    private Logger() {
    }

    /**
     * Records one log entry (message prefixed with the current time, e.g.
     * "[14:32:07] Saved: workspace/forFun/guild.json"), immediately passes
     * it to every listener currently registered (see {@link #addListener}),
     * and appends it to the persistent log file (see class Javadoc).
     */
    public static synchronized void log(String message) {
        String entry = "[" + LocalTime.now().format(TIMESTAMP_FORMAT) + "] " + message;
        history.add(entry);
        for (Consumer<String> listener : listeners) {
            listener.accept(entry);
        }
        appendToLogFile(entry);
    }

    public static synchronized void logToFile(String message) {
        String entry = "[" + LocalTime.now().format(TIMESTAMP_FORMAT) + "] " + message;
        appendToLogFile(entry);
    }

    /**
     * Convenience for logging a caught exception: delegates to
     * {@link #log(String)} with {@code context} (what was being attempted,
     * e.g. "Could not load guild from workspace/Demo/guild.json") followed
     * by the exception's own message and its full stack trace, so both the
     * running app's {@code LogPanel} and the persistent log file show enough
     * detail to diagnose the problem later without needing to reproduce it.
     */
    public static void logException(String context, Throwable t) {
        StringWriter stackTraceWriter = new StringWriter();
        t.printStackTrace(new PrintWriter(stackTraceWriter));
        log(context + ": " + t + System.lineSeparator() + stackTraceWriter);
    }

    /**
     * Registers listener to receive every future {@link #log(String)} entry
     * as it happens, and immediately replays every entry already logged so
     * far (see class Javadoc) - in chronological order, oldest first.
     */
    public static synchronized void addListener(Consumer<String> listener) {
        listeners.add(listener);
        for (String entry : history) {
            listener.accept(entry);
        }
    }

    /**
     * Appends entry to {@link #LOG_FILE_PATH} (creating the workspace folder
     * if it does not exist yet), rotating first via
     * {@link #rotateLogFile()} if appending would push the file past
     * {@link #MAX_FILE_SIZE_BYTES}. Never throws - see class Javadoc.
     */
    private static void appendToLogFile(String entry) {
        Path logFilePath = logFilePath();
        try {
            byte[] line = (entry + System.lineSeparator()).getBytes();
            if (logFilePath.getParent() != null) {
                Files.createDirectories(logFilePath.getParent());
            }
            long currentSize = Files.isRegularFile(logFilePath) ? Files.size(logFilePath) : 0L;
            if (currentSize + line.length > MAX_FILE_SIZE_BYTES) {
                rotateLogFile();
            }
            Files.write(logFilePath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Could not write " + logFilePath + ": " + e.getMessage());
        }
    }

    /** Moves the current log file to {@link #rotatedLogFilePath()}, overwriting whatever was rotated out before - see class Javadoc. */
    private static void rotateLogFile() throws IOException {
        Files.move(logFilePath(), rotatedLogFilePath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Where the persistent log file lives - see class Javadoc. Resolved fresh every time rather than cached, since {@link Config#getWorkspaceDir()} can change at runtime. */
    private static Path logFilePath() {
        return Config.getWorkspaceDir().resolve("cow2win.log");
    }

    /** The one rotated-out previous log file - see class Javadoc. Together with {@link #logFilePath()}, at most two files ever exist. */
    private static Path rotatedLogFilePath() {
        return Config.getWorkspaceDir().resolve("cow2win.log.1");
    }
}
