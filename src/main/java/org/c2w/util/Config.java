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
    private static final String KEY_LANGUAGE_FILE = "language";
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
                System.err.println("Could not read " + CONFIG_FILE_PATH + ": " + e.getMessage());
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
            System.err.println("Could not write " + CONFIG_FILE_PATH + ": " + e.getMessage());
        }
    }

    // --- lastGuildPath ---

    public static String getLastGuildPath() {
        return properties.getProperty(KEY_LAST_GUILD_PATH, "");
    }

    public static void setLastGuildPath(String lastGuildPath) {
        properties.setProperty(KEY_LAST_GUILD_PATH, lastGuildPath == null ? "" : lastGuildPath);
    }

    // --- language ---

    public static String getLanguage() {
        return properties.getProperty(KEY_LANGUAGE_FILE, "");
    }

    public static void setLanguage(String languageFile) {
        properties.setProperty(KEY_LANGUAGE_FILE, languageFile == null ? "" : languageFile);
    }

    // --- lastLineUpPath ---

    public static String getLastLineUpPath() {
        return properties.getProperty(KEY_LAST_LINEUP_PATH, "");
    }

    public static void setLastLineUpPath(String lastLineUpPath) {
        properties.setProperty(KEY_LAST_LINEUP_PATH, lastLineUpPath == null ? "" : lastLineUpPath);
    }

    // --- defaultAlgorithm ---

    public static String getDefaultAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_ALGORITHM, "");
    }

    public static void setDefaultAlgorithm(String defaultAlgorithm) {
        properties.setProperty(KEY_DEFAULT_ALGORITHM, defaultAlgorithm == null ? "" : defaultAlgorithm);
    }

    // --- backupDir ---

    /** Configured backup directory, or {@link #DEFAULT_BACKUP_DIR} if none was ever saved. See {@link #getBackupDirPath()} for the resolved {@link Path}. */
    public static String getBackupDir() {
        return properties.getProperty(KEY_BACKUP_DIR, DEFAULT_BACKUP_DIR);
    }

    public static void setBackupDir(String backupDir) {
        properties.setProperty(KEY_BACKUP_DIR, (backupDir == null || backupDir.isBlank()) ? DEFAULT_BACKUP_DIR : backupDir);
    }

    /** {@link #getBackupDir()} as a {@link Path}, for callers (e.g. {@link BackupService}) that need to use it directly. */
    public static Path getBackupDirPath() {
        return Paths.get(getBackupDir());
    }
}
