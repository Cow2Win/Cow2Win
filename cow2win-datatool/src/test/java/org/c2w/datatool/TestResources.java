package org.c2w.datatool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Tests never touch the real files: they work on a copy in a temporary repository layout. */
public final class TestResources {

    /** The real repository root - Surefire runs in the tool module's folder. */
    public static final Path REPOSITORY = Path.of("..");

    private TestResources() {
    }

    /**
     * Copies the real {@code catalog/} and {@code cow2win-app/src/main/resources} to the same
     * places below {@code tempDir} and returns {@code tempDir} - a repository root the tool accepts.
     */
    public static Path copyTo(Path tempDir) throws IOException {
        copy(REPOSITORY.resolve(RepositoryFolder.CATALOG), tempDir.resolve(RepositoryFolder.CATALOG));
        copy(REPOSITORY.resolve(RepositoryFolder.APP_RESOURCES), tempDir.resolve(RepositoryFolder.APP_RESOURCES));
        return tempDir;
    }

    private static void copy(Path source, Path target) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            files.forEach(file -> {
                Path destination = target.resolve(source.relativize(file).toString());
                try {
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(file, destination);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
