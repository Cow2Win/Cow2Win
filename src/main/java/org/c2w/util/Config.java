package org.c2w.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Config {

    /**
     * {@code <user.home>/.cow2Win} - umbrella folder holding both the
     * default workspace folder ({@link #DEFAULT_WORKSPACE_DIR}) and the
     * default backup folder ({@link #DEFAULT_BACKUP_DIR}). Anchored to the
     * user's home directory (rather than the app's working directory) so
     * every install/update on this machine automatically finds the same
     * data, with nothing to recreate or copy by hand.
     */
    private static final Path ROOT = Paths.get(System.getProperty("user.home"), ".cow2Win");

    /**
     * {@code <user.home>/.cow2Win/workspace} - guilds, lineups and {@link
     * Logger}'s log file, unless {@link #KEY_WORKSPACE_PATH} points
     * elsewhere (see {@link #getWorkspaceDir()}). Also used by {@link
     * BackupService} (what gets backed up) and, as a fallback, {@code
     * org.c2w.gui.ToolbarPanel}.
     */
    private static final Path DEFAULT_WORKSPACE_DIR = ROOT.resolve("workspace");

    /**
     * This class's own config.properties - deliberately NOT under {@link
     * #getWorkspaceDir()}: {@link #KEY_WORKSPACE_PATH} is exactly what
     * points at the workspace to use, and a config file living inside the
     * workspace it names could never point at a *different* one (there
     * would be nowhere to read {@link #KEY_WORKSPACE_PATH} from before
     * knowing which workspace's config file to read it from). Living at
     * the app level instead is what makes several Cow2Win
     * installations/copies on the same machine able to use different
     * workspace folders.
     *
     * <p>Resolved the same way as {@link JsonSupport#resolveDataFile} -
     * the packaged app's on-disk "resources" folder (shipped right next to
     * {@code Cow2Win.exe} via jpackage's {@code appContentPaths}) when
     * running the installed app-image, falling back to the project root
     * when run from the IDE/source checkout (see {@link
     * #resolveConfigFilePath()}).
     */
    private static final Path CONFIG_FILE_PATH = resolveConfigFilePath();

    private static final String KEY_LAST_GUILD_PATH = "lastGuildPath";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_LAST_LINEUP_PATH = "lastLineUpPath";
    /** Legacy (pre-2026-09-24) single default algorithm for both sides - only read as a fallback, see {@link #getDefaultHeroAlgorithm()}. */
    private static final String KEY_DEFAULT_ALGORITHM = "defaultAlgorithm";
    private static final String KEY_DEFAULT_HERO_ALGORITHM = "defaultHeroAlgorithm";
    private static final String KEY_DEFAULT_TITAN_ALGORITHM = "defaultTitanAlgorithm";
    private static final String KEY_BACKUP_DIR = "backupDir";
    private static final String KEY_WORKSPACE_PATH = "workspacePath";
    /** Default backup directory: a "backup" folder next to {@link #DEFAULT_WORKSPACE_DIR}, under the same {@link #ROOT} umbrella. */
    private static final String DEFAULT_BACKUP_DIR = ROOT.resolve("backup").toString();

    private static final Properties properties = new Properties();

    private Config() {
    }

    /**
     * Where {@link #CONFIG_FILE_PATH} lives: the packaged app-image's
     * on-disk {@code resources} folder (a sibling of {@code Cow2Win.exe},
     * present whenever running as the installed app rather than from
     * source - see {@code JsonSupport}'s class Javadoc) if that folder
     * exists, otherwise the current working directory, which is the
     * project root when run from the IDE/source checkout.
     */
    private static Path resolveConfigFilePath() {
        Path packagedResources = Paths.get("resources");
        Path base = Files.isDirectory(packagedResources) ? packagedResources : Paths.get("");
        return base.resolve("config.properties");
    }

    public static boolean exists() {
        return Files.isRegularFile(CONFIG_FILE_PATH);
    }

    public static void load() {
        properties.clear();
        if (Files.isRegularFile(CONFIG_FILE_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE_PATH)) {
                properties.load(in);
            } catch (IOException e) {
                Logger.logException("Could not read " + CONFIG_FILE_PATH, e);
            }
        }
    }

    public static void save() {
        try {
            if (CONFIG_FILE_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_FILE_PATH.getParent());
            }
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE_PATH)) {
                properties.store(out, "Cow2Win - recently used settings (auto-generated)");
            }
        } catch (IOException e) {
            Logger.logException("Could not write " + CONFIG_FILE_PATH, e);
        }
    }

    /**
     * Sets one property and, if this actually changes its previous value,
     * logs that via {@link Logger#log} (e.g. "Config changed: language =
     * \"deutsch\" (previously \"english\")") - this is how every
     * {@code setXxx} method below reports a "technical change" to the log
     * file/LogPanel, without each of them having to do it individually.
     * Never logs anything during {@link #load()}, since that only replays
     * values already on disk rather than changing them.
     */
    private static void setProperty(String key, String value) {
        String previous = properties.getProperty(key, "");
        if (!previous.equals(value)) {
            String previousNote = previous.isEmpty() ? "" : " (previously \"" + previous + "\")";
            Logger.log("Config changed: " + key + " = \"" + value + "\"" + previousNote);
        }
        properties.setProperty(key, value);
    }

    // --- lastGuildPath ---

    public static String getLastGuildPath() {
        return properties.getProperty(KEY_LAST_GUILD_PATH, "");
    }

    public static void setLastGuildPath(String lastGuildPath) {
        setProperty(KEY_LAST_GUILD_PATH, lastGuildPath == null ? "" : lastGuildPath);
    }

    // --- language ---

    /**
     * Raw configured language, e.g. {@code "deutsch"} - the name of the
     * subdirectory under {@code resources/language} (see {@link
     * LanguageService#availableLanguages()}), not a file name. May also
     * still hold a pre-2026-09-16 value like {@code "deutsch.txt"} for a
     * config saved by an older Cow2Win version; {@link
     * LanguageService#configuredLanguage()} is what normalizes that,
     * callers that just need "the language to use" should go through it
     * rather than this raw getter.
     */
    public static String getLanguage() {
        return properties.getProperty(KEY_LANGUAGE, "");
    }

    public static void setLanguage(String language) {
        setProperty(KEY_LANGUAGE, language == null ? "" : language);
    }

    // --- lastLineUpPath ---

    public static String getLastLineUpPath() {
        return properties.getProperty(KEY_LAST_LINEUP_PATH, "");
    }

    public static void setLastLineUpPath(String lastLineUpPath) {
        setProperty(KEY_LAST_LINEUP_PATH, lastLineUpPath == null ? "" : lastLineUpPath);
    }

    // --- defaultHeroAlgorithm / defaultTitanAlgorithm ---

    /**
     * Display name of the default HERO lineup algorithm (see {@code
     * LineupAlgorithms#HERO}), or {@code ""} if none is configured. Falls back
     * to the legacy single {@code defaultAlgorithm} value saved before the
     * algorithms were split into hero and titan variants (2026-09-24), since
     * every strategy kept its display name in that split.
     */
    public static String getDefaultHeroAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_HERO_ALGORITHM, properties.getProperty(KEY_DEFAULT_ALGORITHM, ""));
    }

    public static void setDefaultHeroAlgorithm(String defaultHeroAlgorithm) {
        setProperty(KEY_DEFAULT_HERO_ALGORITHM, defaultHeroAlgorithm == null ? "" : defaultHeroAlgorithm);
    }

    /** Display name of the default TITAN lineup algorithm - same fallback as {@link #getDefaultHeroAlgorithm()}. */
    public static String getDefaultTitanAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_TITAN_ALGORITHM, properties.getProperty(KEY_DEFAULT_ALGORITHM, ""));
    }

    public static void setDefaultTitanAlgorithm(String defaultTitanAlgorithm) {
        setProperty(KEY_DEFAULT_TITAN_ALGORITHM, defaultTitanAlgorithm == null ? "" : defaultTitanAlgorithm);
    }

    // --- backupDir ---

    /** Configured backup directory, or {@link #DEFAULT_BACKUP_DIR} if none was ever saved. See {@link #getBackupDirPath()} for the resolved {@link Path}. */
    public static String getBackupDir() {
        return properties.getProperty(KEY_BACKUP_DIR, DEFAULT_BACKUP_DIR);
    }

    public static void setBackupDir(String backupDir) {
        setProperty(KEY_BACKUP_DIR, (backupDir == null || backupDir.isBlank()) ? DEFAULT_BACKUP_DIR : backupDir);
    }

    /** {@link #getBackupDir()} as a {@link Path}, for callers (e.g. {@link BackupService}) that need to use it directly. */
    public static Path getBackupDirPath() {
        return Paths.get(getBackupDir());
    }

    // --- workspacePath ---

    /**
     * Configured workspace directory (guilds, lineups, {@link Logger}'s log
     * file), or {@link #DEFAULT_WORKSPACE_DIR} if none was ever saved. See
     * {@link #getWorkspaceDir()} for the resolved {@link Path} - callers
     * throughout the app (e.g. {@code C2WApp}, {@code ToolbarPanel}, {@link
     * BackupService}, {@link Logger}) should go through that rather than
     * this raw getter, so a configured workspace actually takes effect
     * everywhere.
     */
    public static String getWorkspacePath() {
        return properties.getProperty(KEY_WORKSPACE_PATH, DEFAULT_WORKSPACE_DIR.toString());
    }

    public static void setWorkspacePath(String workspacePath) {
        setProperty(KEY_WORKSPACE_PATH, (workspacePath == null || workspacePath.isBlank())
                ? DEFAULT_WORKSPACE_DIR.toString() : workspacePath);
    }

    /**
     * {@link #getWorkspacePath()} as a {@link Path} - this is the one to
     * use wherever the old {@code Config.DIR} field used to be read, since
     * unlike that field this reflects {@link #KEY_WORKSPACE_PATH} and can
     * therefore only be trusted after {@link #load()} has run.
     */
    public static Path getWorkspaceDir() {
        return Paths.get(getWorkspacePath());
    }
}
