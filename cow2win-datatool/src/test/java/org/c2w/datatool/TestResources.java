package org.c2w.datatool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Tests never touch the real resources: they work on a copy in a temporary repository layout. */
public final class TestResources {

    /** The app module's real resources - Surefire runs in the tool module's folder. */
    public static final Path REAL = Path.of("..", "cow2win-app", "src", "main", "resources");

    private TestResources() {
    }

    /** Copies the real resources to {@code <tempDir>/cow2win-app/src/main/resources} and returns that folder. */
    public static Path copyTo(Path tempDir) throws IOException {
        Path target = tempDir.resolve(ResourceFolder.APP_RESOURCES);
        try (Stream<Path> files = Files.walk(REAL)) {
            files.forEach(source -> {
                Path destination = target.resolve(REAL.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return target;
    }
}
