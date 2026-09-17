package org.c2w.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Config {

    private static final Path CONFIG_FILE_PATH = Paths.get("workspace", "config.properties");
    private static final String KEY_LAST_GUILD_PATH = "lastGuildPath";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_LAST_LINEUP_PATH = "lastLineUpPath";
    private static final String KEY_DEFAULT_ALGORITHM = "defaultAlgorithm";
    private static final String KEY_BACKUP_DIR = "backupDir";
    /** Default backup directory: a "backup" folder on the same level as "workspace" (see {@link #CONFIG_FILE_PATH}). */
    private static final String DEFAULT_BACKUP_DIR = "backup";

    private static final Properties properties = new Properties();

    private Config() {
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

    // --- defaultAlgorithm ---

    public static String getDefaultAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_ALGORITHM, "");
    }

    public static void setDefaultAlgorithm(String defaultAlgorithm) {
        setProperty(KEY_DEFAULT_ALGORITHM, defaultAlgorithm == null ? "" : defaultAlgorithm);
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
}
