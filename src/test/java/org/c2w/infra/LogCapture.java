package org.c2w.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * The lines {@link Logger} wrote to the technical log file since {@link #start()} -
 * a live view, read again on every access, so a test can start the capture, act,
 * then inspect what was logged in between.
 */
public final class LogCapture extends AbstractList<String> {

    private final Path file;
    private final long offset;

    private LogCapture(Path file, long offset) {
        this.file = file;
        this.offset = offset;
    }

    /** Starts capturing at the current end of the log file of the configured workspace. */
    public static LogCapture start() {
        Path file = Config.getWorkspaceDir().resolve(Logger.LOG_FILE_NAME);
        try {
            return new LogCapture(file, Files.isRegularFile(file) ? Files.size(file) : 0L);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines() {
        try {
            if (!Files.isRegularFile(file)) {
                return List.of();
            }
            byte[] bytes = Files.readAllBytes(file);
            int from = (int) Math.min(offset, bytes.length);
            String text = new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
            return text.isEmpty() ? List.of() : Arrays.asList(text.split("\\R"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String get(int index) {
        return lines().get(index);
    }

    @Override
    public int size() {
        return lines().size();
    }
}
