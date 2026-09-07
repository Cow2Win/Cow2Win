package org.c2w.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final List<String> history = new ArrayList<>();
    private static final List<Consumer<String>> listeners = new ArrayList<>();

    private Logger() {
    }

    /**
     * Records one log entry (message prefixed with the current time, e.g.
     * "[14:32:07] Saved: workspace/forFun/guild.json") and immediately
     * passes it to every listener currently registered (see
     * {@link #addListener}).
     */
    public static synchronized void log(String message) {
        String entry = "[" + LocalTime.now().format(TIMESTAMP_FORMAT) + "] " + message;
        history.add(entry);
        for (Consumer<String> listener : listeners) {
            listener.accept(entry);
        }
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
}
