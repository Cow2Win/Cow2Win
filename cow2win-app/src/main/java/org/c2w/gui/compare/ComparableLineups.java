package org.c2w.gui.compare;

import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.Logger;
import org.c2w.service.GuildService;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The lineups the lineup comparison can compare the open lineup with: every ".lineup" file of
 * every guild in the workspace, except the open file itself (it is the left side already).
 * Order: the current guild first, then the other guilds alphabetically by name; within a
 * guild "Live" (the Original lineup) first, then the others alphabetically.
 *
 * <p>Lineup files are listed through the file system, since {@link LineupRepository#findAll()}
 * is a stub - like the lineup combo box of the context bar.
 */
public final class ComparableLineups {

    private static final String LINEUP_FILE_GLOB = "*" + LineupFiles.SUFFIX;

    private ComparableLineups() {
    }

    /**
     * One selectable lineup: the folder and name of its guild and the lineup file.
     * {@code guildName} is the name stored in the guild file, or the folder name if it has none.
     */
    public record Choice(String guildFolder, String guildName, Path lineupPath) {

        public Choice {
            Objects.requireNonNull(guildFolder, "guildFolder");
            Objects.requireNonNull(guildName, "guildName");
            Objects.requireNonNull(lineupPath, "lineupPath");
        }

        public String fileName() {
            return lineupPath.getFileName().toString();
        }

        /** "Live" for the Original lineup, otherwise the file name without suffix. */
        public String lineupDisplayName() {
            return LineupFiles.displayName(fileName());
        }

        public boolean isOriginal() {
            return LineupFiles.isOriginal(lineupPath);
        }

        /** True if this is the lineup file at {@code path}. */
        public boolean isAt(Path path) {
            return path != null && normalize(lineupPath).equals(normalize(path));
        }
    }

    /**
     * Every lineup of the guilds in {@code guildFolders} (folders of {@code workspaceDir}),
     * without the file at {@code openLineupPath}, in the order described in the class Javadoc.
     * {@code currentFolder} and {@code openLineupPath} may be null.
     */
    public static List<Choice> list(Path workspaceDir, List<String> guildFolders, String currentFolder,
                                    Path openLineupPath) {
        List<GuildLineups> guilds = new ArrayList<>();
        for (String folder : guildFolders) {
            Path guildDir = workspaceDir.resolve(folder);
            guilds.add(new GuildLineups(folder, guildName(guildDir, folder), listLineupFiles(guildDir)));
        }
        guilds.sort(Comparator
                .comparing((GuildLineups g) -> !g.folder().equals(currentFolder))
                .thenComparing(g -> g.name().toLowerCase(Locale.ROOT))
                .thenComparing(GuildLineups::folder));

        List<Choice> result = new ArrayList<>();
        for (GuildLineups guild : guilds) {
            guild.lineupFiles().stream()
                    .map(path -> new Choice(guild.folder(), guild.name(), path))
                    .filter(choice -> !choice.isAt(openLineupPath))
                    .sorted(Comparator
                            .comparing((Choice c) -> !c.isOriginal())
                            .thenComparing(c -> c.lineupDisplayName().toLowerCase(Locale.ROOT))
                            .thenComparing(Choice::fileName))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * The choice to select first: Live of the current guild if there is one in {@code choices},
     * otherwise the first one - null if {@code choices} is empty.
     */
    public static Choice preselect(List<Choice> choices, String currentFolder) {
        return choices.stream()
                .filter(c -> c.isOriginal() && c.guildFolder().equals(currentFolder))
                .findFirst()
                .orElse(choices.isEmpty() ? null : choices.get(0));
    }

    /**
     * The choice to select after {@code choices} were rebuilt: the one at the lineup file of
     * {@code previous} if it is still there, otherwise {@link #preselect}.
     */
    public static Choice reselect(List<Choice> choices, Choice previous, String currentFolder) {
        if (previous != null) {
            for (Choice choice : choices) {
                if (choice.isAt(previous.lineupPath())) {
                    return choice;
                }
            }
        }
        return preselect(choices, currentFolder);
    }

    private record GuildLineups(String folder, String name, List<Path> lineupFiles) {
    }

    private static String guildName(Path guildDir, String folder) {
        Path guildFile = guildDir.resolve(GuildService.GUILD_FILE_NAME);
        try {
            String name = GuildRepository.readSummary(guildFile).name();
            return name == null || name.isBlank() ? folder : name;
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not read the guild name from " + guildFile, e);
            return folder;
        }
    }

    private static List<Path> listLineupFiles(Path guildDir) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(guildDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(guildDir, LINEUP_FILE_GLOB)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    result.add(path);
                }
            }
        } catch (IOException e) {
            Logger.logException("Could not list lineup files in " + guildDir, e);
        }
        return result;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
