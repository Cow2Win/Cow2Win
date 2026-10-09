package org.c2w.datatool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds and checks the folder the tool works on: {@code <repo>/cow2win-app/src/main/resources}.
 * Only a folder that ends with {@code src/main/resources} and holds {@code data/heroes.json} is
 * accepted - protection against editing a user workspace by mistake.
 */
public final class ResourceFolder {

    static final Path APP_RESOURCES = Path.of("cow2win-app", "src", "main", "resources");
    private static final Path HEROES = Path.of("data", "heroes.json");

    private ResourceFolder() {
    }

    /**
     * The folder from the program argument (the repository or the resources folder itself),
     * else the first one found going up from {@code workingDir}; empty if neither works.
     *
     * @throws IllegalArgumentException if the argument names a folder that is not accepted
     */
    public static Optional<Path> find(String[] args, Path workingDir) {
        if (args.length > 0) {
            Path given = Path.of(args[0]).toAbsolutePath().normalize();
            Path inRepo = given.resolve(APP_RESOURCES);
            Path candidate = Files.isRegularFile(inRepo.resolve(HEROES)) ? inRepo : given;
            String problem = check(candidate);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            return Optional.of(candidate);
        }
        for (Path dir = workingDir.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(APP_RESOURCES);
            if (Files.isRegularFile(candidate.resolve(HEROES))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Null if {@code folder} is an accepted resources folder, otherwise the reason why not. */
    public static String check(Path folder) {
        Path normalized = folder.toAbsolutePath().normalize();
        int count = normalized.getNameCount();
        boolean endsRight = count >= 3
                && normalized.getName(count - 3).toString().equals("src")
                && normalized.getName(count - 2).toString().equals("main")
                && normalized.getName(count - 1).toString().equals("resources");
        if (!endsRight) {
            return normalized + " does not end with src/main/resources - the tool only edits the app's"
                    + " source resources, never a workspace.";
        }
        if (!Files.isRegularFile(normalized.resolve(HEROES))) {
            return normalized + " contains no data/heroes.json.";
        }
        return null;
    }
}
