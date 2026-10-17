package org.c2w.datatool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds and checks the folder the tool works on: the repository root, holding the two folders
 * the tool edits - {@code catalog/} (the game catalog: catalog data files, images, manifest)
 * and {@code cow2win-app/src/main/resources} (CowScore defaults, combos, templates, display
 * names). Only a folder with both is accepted - protection against editing a user workspace
 * by mistake.
 */
public final class RepositoryFolder {

    /** The game catalog below the repository root. */
    public static final Path CATALOG = Path.of("catalog");
    /** The app module's resources below the repository root. */
    public static final Path APP_RESOURCES = Path.of("cow2win-app", "src", "main", "resources");

    private static final Path HEROES = CATALOG.resolve(Path.of("data", "heroes.json"));
    private static final Path HERO_COW_SCORE = APP_RESOURCES.resolve(Path.of("data", "cowScore.json"));

    private RepositoryFolder() {
    }

    /**
     * The repository from the program argument, else the first one found going up from
     * {@code workingDir}; empty if neither works.
     *
     * @throws IllegalArgumentException if the argument names a folder that is not accepted
     */
    public static Optional<Path> find(String[] args, Path workingDir) {
        if (args.length > 0) {
            Path given = Path.of(args[0]).toAbsolutePath().normalize();
            String problem = check(given);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            return Optional.of(given);
        }
        for (Path dir = workingDir.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (check(dir) == null) {
                return Optional.of(dir);
            }
        }
        return Optional.empty();
    }

    /** Null if {@code folder} is an accepted repository root, otherwise the reason why not. */
    public static String check(Path folder) {
        Path normalized = folder.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized.resolve(HEROES))) {
            return normalized + " contains no " + slashes(HEROES) + " - the tool only edits the Cow2Win"
                    + " repository, never a workspace.";
        }
        if (!Files.isRegularFile(normalized.resolve(HERO_COW_SCORE))) {
            return normalized + " contains no " + slashes(HERO_COW_SCORE) + " - the tool only edits the"
                    + " Cow2Win repository, never a workspace.";
        }
        return null;
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }
}
