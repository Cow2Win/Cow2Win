package org.c2w.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * A text log file that is appended to line by line (UTF-8) and capped at
 * {@code maxBytes}: once appending the next line would exceed that, the file
 * is moved to {@link #rotatedFile()} ({@code <name>.1}, overwriting whatever
 * was rotated out before) and a fresh, empty file is started - so at most two
 * files ever exist. Shared by the technical {@link Logger} file and the
 * per-guild {@code guild.log} (see {@code org.c2w.service.GuildLog}).
 *
 * <p>Not thread-safe on its own - callers synchronize.
 */
public final class RotatingLogFile {

    private final Path file;
    private final long maxBytes;

    public RotatingLogFile(Path file, long maxBytes) {
        if (file == null) {
            throw new IllegalArgumentException("RotatingLogFile needs a file");
        }
        this.file = file;
        this.maxBytes = maxBytes;
    }

    /** The current log file. */
    public Path file() {
        return file;
    }

    /** The one rotated-out previous log file, next to {@link #file()}. */
    public Path rotatedFile() {
        return file.resolveSibling(file.getFileName() + ".1");
    }

    /**
     * Appends {@code line} plus a line separator, rotating first if needed.
     * The file is created if missing, but its folder must already exist.
     */
    public void append(String line) throws IOException {
        byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        long currentSize = Files.isRegularFile(file) ? Files.size(file) : 0L;
        if (currentSize > 0 && currentSize + bytes.length > maxBytes) {
            Files.move(file, rotatedFile(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
